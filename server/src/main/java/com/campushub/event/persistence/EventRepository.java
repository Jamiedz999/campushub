package com.campushub.event.persistence;

import com.campushub.event.domain.EnrolledEntry;
import com.campushub.event.domain.EnrollmentVia;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventBrowseQuery;
import com.campushub.event.domain.EventEdit;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.EventSort;
import com.campushub.event.domain.EventStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.core.index.TextIndexDefinition.TextIndexDefinitionBuilder;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

// MongoTemplate access for the Event document. Every write here is one guarded findAndModify-style
// update whose filter carries the lifecycle rule — no read-then-write. See
// docs/adr/03-define-event-lifecycle.md and docs/planning/implementation/TECHNICAL-BASELINE.md.
@Component
public class EventRepository {

    private final MongoTemplate mongoTemplate;

    public EventRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Mongock-owned: called only from EventStructuralChangeUnit. The text index is the only one here
     * serving a read path rather than a correctness guarantee — see the technical baseline. The other
     * three are the compound shapes discovery's single $match actually filters and sorts by.
     */
    public void ensureIndexes() {
        TextIndexDefinition textIndex = new TextIndexDefinitionBuilder()
                .onField("title")
                .onField("description")
                .build();
        mongoTemplate.indexOps(Event.class).createIndex(textIndex);
        mongoTemplate
                .indexOps(Event.class)
                .createIndex(new Index().on("status", Sort.Direction.ASC).on("startsAt", Sort.Direction.ASC));
        mongoTemplate
                .indexOps(Event.class)
                .createIndex(new Index().on("status", Sort.Direction.ASC).on("clubId", Sort.Direction.ASC));
        mongoTemplate
                .indexOps(Event.class)
                .createIndex(new Index()
                        .on("status", Sort.Direction.ASC)
                        .on("registrationOpensAt", Sort.Direction.ASC)
                        .on("registrationClosesAt", Sort.Direction.ASC));
    }

    /** Mongock-owned: called only from EventSeatLedgerIndexChangeUnit. Serves findEnrolled's "my events" query. */
    public void ensureSeatLedgerIndexes() {
        mongoTemplate.indexOps(Event.class).createIndex(new Index().on("enrolled.studentId", Sort.Direction.ASC));
    }

    /** Inserts a new Draft and returns its generated id. */
    public String insertDraft(Event draft) {
        return mongoTemplate.insert(draft).getId();
    }

    /** Scoped by the caller's Club grants, per docs/adr/08-define-roles-and-resource-authorization.md. */
    public Optional<Event> findScopedById(String eventId, Set<String> clubIds) {
        Query query = new Query(Criteria.where("id").is(eventId).and("clubId").in(clubIds));
        return Optional.ofNullable(mongoTemplate.findOne(query, Event.class));
    }

    /** True if an Event with this id exists within the caller's Club grants, whatever its Status. */
    public boolean existsScoped(String eventId, Set<String> clubIds) {
        Query query = new Query(Criteria.where("id").is(eventId).and("clubId").in(clubIds));
        return mongoTemplate.exists(query, Event.class);
    }

    /** True if an Event with this id exists at all, ignoring Club — the admin cancel path's classifier. */
    public boolean exists(String eventId) {
        return mongoTemplate.exists(new Query(Criteria.where("id").is(eventId)), Event.class);
    }

    /**
     * Unscoped by Club and by Status — a Student is never an Officer of anything, so there is no grant
     * set to scope by. Used both for the Student's own view of an Event and as the one follow-up read
     * that classifies a refused takeSeat. See docs/adr/04-define-registration-capacity-and-waitlist.md.
     */
    public Optional<Event> findById(String eventId) {
        return Optional.ofNullable(mongoTemplate.findById(eventId, Event.class));
    }

    /**
     * One atomic update. Draft: every field is editable. Published, before startsAt: title,
     * description, the four timestamps and capacity, and capacity may only be raised — both guards live
     * in the filter, not in application code. From startsAt: nothing here matches. Returns whether the
     * guard passed.
     */
    public boolean edit(String eventId, Set<String> clubIds, EventEdit edit, Instant now) {
        Criteria editableWindow = new Criteria()
                .orOperator(
                        Criteria.where("status").is(EventStatus.DRAFT),
                        new Criteria()
                                .andOperator(
                                        Criteria.where("status").is(EventStatus.PUBLISHED),
                                        Criteria.where("startsAt").gt(now)));

        List<Criteria> guards = new ArrayList<>();
        guards.add(editableWindow);
        if (edit.capacity() != null) {
            guards.add(new Criteria()
                    .orOperator(
                            Criteria.where("status").is(EventStatus.DRAFT),
                            Criteria.where("capacity").lt(edit.capacity())));
        }

        Criteria filter = Criteria.where("id")
                .is(eventId)
                .and("clubId")
                .in(clubIds)
                .andOperator(guards.toArray(new Criteria[0]));

        Update update = new Update();
        if (edit.title() != null) {
            update.set("title", edit.title());
        }
        if (edit.description() != null) {
            update.set("description", edit.description());
        }
        if (edit.registrationOpensAt() != null) {
            update.set("registrationOpensAt", edit.registrationOpensAt());
        }
        if (edit.registrationClosesAt() != null) {
            update.set("registrationClosesAt", edit.registrationClosesAt());
        }
        if (edit.startsAt() != null) {
            update.set("startsAt", edit.startsAt());
        }
        if (edit.endsAt() != null) {
            update.set("endsAt", edit.endsAt());
        }
        if (edit.capacity() != null) {
            update.set("capacity", edit.capacity());
        }

        return mongoTemplate
                        .updateFirst(new Query(filter), update, Event.class)
                        .getModifiedCount()
                > 0;
    }

    /** Draft only, forward-only — there is no matching un-publish method. */
    public boolean publish(String eventId, Set<String> clubIds) {
        Query query = new Query(Criteria.where("id")
                .is(eventId)
                .and("clubId")
                .in(clubIds)
                .and("status")
                .is(EventStatus.DRAFT));
        Update update = new Update().set("status", EventStatus.PUBLISHED);
        return mongoTemplate.updateFirst(query, update, Event.class).getModifiedCount() > 0;
    }

    /** The owning Club's Officer. Published only, and only before endsAt. */
    public boolean cancelAsOfficer(String eventId, Set<String> clubIds, Instant now) {
        Query query = new Query(Criteria.where("id")
                .is(eventId)
                .and("clubId")
                .in(clubIds)
                .and("status")
                .is(EventStatus.PUBLISHED)
                .and("endsAt")
                .gt(now));
        return cancel(query);
    }

    /**
     * A University Admin, unscoped by Club — the one cross-Club write in the system. See
     * docs/adr/08-define-roles-and-resource-authorization.md.
     */
    public boolean cancelAsAdmin(String eventId, Instant now) {
        Query query = new Query(Criteria.where("id")
                .is(eventId)
                .and("status")
                .is(EventStatus.PUBLISHED)
                .and("endsAt")
                .gt(now));
        return cancel(query);
    }

    private boolean cancel(Query query) {
        // Only `status` is set: the Seat Ledger (enrolled, waitlist) is untouched and freezes exactly as
        // it was, per docs/adr/03-define-event-lifecycle.md.
        Update update = new Update().set("status", EventStatus.CANCELLED);
        return mongoTemplate.updateFirst(query, update, Event.class).getModifiedCount() > 0;
    }

    /**
     * The whole Seat Ledger race resolved in one write: Published, both Registration Window bounds, the
     * startsAt freeze, both duplicate guards and the capacity $expr guard all live in this one filter, so
     * a losing writer changes nothing and there is no read-then-write anywhere. See
     * docs/adr/04-define-registration-capacity-and-waitlist.md's "Taking a Seat". Returns whether the
     * guard passed; a failed attempt is classified by one separate follow-up read (findById), never by
     * anything in this method.
     */
    public boolean takeSeat(String eventId, String studentId, Instant now) {
        Criteria filter = Criteria.where("id")
                .is(eventId)
                .and("status")
                .is(EventStatus.PUBLISHED)
                .and("registrationOpensAt")
                .lte(now)
                .and("registrationClosesAt")
                .gt(now)
                .and("startsAt")
                .gt(now)
                .and("enrolled.studentId")
                .ne(studentId)
                .and("waitlist")
                .ne(studentId)
                .and("$expr")
                .is(new Document("$lt", List.of(new Document("$size", "$enrolled"), "$capacity")));

        Update update = new Update().push("enrolled", new EnrolledEntry(studentId, EnrollmentVia.DIRECT, now));

        return mongoTemplate
                        .updateFirst(new Query(filter), update, Event.class)
                        .getModifiedCount()
                > 0;
    }

    /** The Student's own "my events" list — every Event, whatever its Status, where they hold a Seat. */
    public EventPage findEnrolled(String studentId, int page, int size) {
        Query query = new Query(Criteria.where("enrolled.studentId").is(studentId));

        long total = mongoTemplate.count(query, Event.class);

        query.with(Sort.by(Sort.Direction.ASC, "startsAt")).skip((long) page * size).limit(size);
        List<Event> items = mongoTemplate.find(query, Event.class);

        return new EventPage(items, page, size, total);
    }

    /**
     * The four indexable filters plus the one $expr free-seat filter, all in this single $match — see
     * docs/adr/16-define-event-discovery.md. Keeping them in one stage is what makes {@code total} agree
     * with the page even when the free-seat filter is active. {@code resolvedSort} and paging are
     * already decided by the caller; this method only executes them.
     */
    public EventPage browse(EventBrowseQuery query, EventSort resolvedSort, Instant now) {
        Document filter = matchStage(query, now);
        BasicQuery mongoQuery = new BasicQuery(filter);

        long total = mongoTemplate.count(mongoQuery, Event.class);

        BasicQuery pagedQuery = new BasicQuery(filter);
        pagedQuery.setSortObject(sortDocument(resolvedSort));
        pagedQuery.skip((long) query.page() * query.size()).limit(query.size());
        List<Event> items = mongoTemplate.find(pagedQuery, Event.class);

        return new EventPage(items, query.page(), query.size(), total);
    }

    private static Document matchStage(EventBrowseQuery query, Instant now) {
        List<Document> and = new ArrayList<>();
        and.add(new Document("status", EventStatus.PUBLISHED.name()));
        if (query.clubId() != null) {
            and.add(new Document("clubId", query.clubId()));
        }
        if (Boolean.TRUE.equals(query.openForRegistration())) {
            and.add(new Document("registrationOpensAt", new Document("$lte", Date.from(now))));
            and.add(new Document("registrationClosesAt", new Document("$gt", Date.from(now))));
        }
        if (query.startsAtFrom() != null) {
            and.add(new Document("startsAt", new Document("$gte", Date.from(query.startsAtFrom()))));
        }
        if (query.startsAtTo() != null) {
            and.add(new Document("startsAt", new Document("$lte", Date.from(query.startsAtTo()))));
        }
        if (Boolean.TRUE.equals(query.hasFreeSeat())) {
            and.add(new Document(
                    "$expr", new Document("$lt", List.of(new Document("$size", "$enrolled"), "$capacity"))));
        }
        if (query.searchTerm() != null && !query.searchTerm().isBlank()) {
            and.add(new Document("$text", new Document("$search", query.searchTerm())));
        }
        return new Document("$and", and);
    }

    private static Document sortDocument(EventSort sort) {
        return switch (sort) {
            case STARTS_AT_ASC -> new Document("startsAt", 1);
            case STARTS_AT_DESC -> new Document("startsAt", -1);
            case CREATED_AT_DESC -> new Document("_id", -1);
            case RELEVANCE -> new Document("score", new Document("$meta", "textScore"));
        };
    }
}

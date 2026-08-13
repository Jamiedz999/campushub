package com.campushub.event.persistence;

import com.campushub.event.EventModule.RegistrationForm;
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
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
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

    /** Mongock-owned defaults for Event documents written before registration forms existed. */
    public void initializeRegistrationForms() {
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("registrationForm").is(null)),
                new Update().set("registrationForm", RegistrationForm.empty()),
                Event.class);
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("registrationFormRevision").is(null)),
                new Update().set("registrationFormRevision", 0),
                Event.class);
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("registrationFormLocked").is(null).and("enrolled.0").exists(true)),
                new Update().set("registrationFormLocked", true),
                Event.class);
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("registrationFormLocked").is(null)),
                new Update().set("registrationFormLocked", false),
                Event.class);
    }

    /** Inserts a new Draft and returns its generated id. */
    public String insertDraft(Event draft) {
        return mongoTemplate.insert(draft).getId();
    }

    /**
     * Replaces the ordered form definition only while it is still safe for every future answer to use
     * that one definition. The lock and lifecycle checks live in this write, so an Officer can never
     * win a read-then-write race against the first Student taking a Seat.
     */
    public boolean updateRegistrationForm(
            String eventId, Set<String> clubIds, RegistrationForm form, Instant now) {
        Criteria editableLifecycle = new Criteria()
                .orOperator(
                        Criteria.where("status").is(EventStatus.DRAFT),
                        new Criteria()
                                .andOperator(
                                        Criteria.where("status").is(EventStatus.PUBLISHED),
                                        Criteria.where("startsAt").gt(now)));
        Criteria filter = new Criteria()
                .andOperator(
                        Criteria.where("id").is(eventId),
                        Criteria.where("clubId").in(clubIds),
                        Criteria.where("registrationFormLocked").ne(true),
                        Criteria.where("enrolled.0").exists(false),
                        editableLifecycle);
        Update update = new Update()
                .set("registrationForm", form)
                .inc("registrationFormRevision", 1);
        return mongoTemplate.updateFirst(new Query(filter), update, Event.class).getModifiedCount() > 0;
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
            guards.add(Criteria.where("startsAt").gt(now));
            if (edit.startsAt() != null) {
                guards.add(Criteria.where("$expr")
                        .is(new Document(
                                "$gt", List.of(Date.from(edit.startsAt()), Date.from(now)))));
            }
        }

        Criteria filter = Criteria.where("id")
                .is(eventId)
                .and("clubId")
                .in(clubIds)
                .andOperator(guards.toArray(new Criteria[0]));

        if (edit.capacity() != null) {
            AggregationUpdate update = capacityRaiseUpdate(edit, now);
            return mongoTemplate.updateFirst(new Query(filter), update, Event.class).getModifiedCount() > 0;
        }

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
        return mongoTemplate
                        .updateFirst(new Query(filter), update, Event.class)
                        .getModifiedCount()
                > 0;
    }

    private static AggregationUpdate capacityRaiseUpdate(EventEdit edit, Instant now) {
        int newCapacity = edit.capacity();
        Document availablePlaces = new Document(
                "$max",
                List.of(0, new Document("$subtract", List.of(newCapacity, new Document("$size", "$enrolled")))));
        Document studentsToPromote = promotionCount(availablePlaces);

        Document eventFields = editFields(edit);
        eventFields.put("capacity", newCapacity);
        eventFields.put("lastEnrollmentVersion", nextEnrollmentVersion());

        Document ledgerFields = new Document();
        ledgerFields.put(
                "enrolled",
                new Document(
                        "$concatArrays",
                        List.of(
                                "$enrolled",
                                promotedEntries(
                                        studentsToPromote, now, "$lastEnrollmentVersion"))));
        ledgerFields.put("waitlist", waitlistAfterPromotions(studentsToPromote));
        ledgerFields.put("promotedCount", promotedCountAfter(studentsToPromote));

        return AggregationUpdate.from(List.of(
                Aggregation.stage(new Document("$set", eventFields)),
                Aggregation.stage(new Document("$set", ledgerFields))));
    }

    private static Document editFields(EventEdit edit) {
        Document fields = new Document();
        putIfPresent(fields, "title", asLiteral(edit.title()));
        putIfPresent(fields, "description", asLiteral(edit.description()));
        putIfPresent(fields, "registrationOpensAt", asDate(edit.registrationOpensAt()));
        putIfPresent(fields, "registrationClosesAt", asDate(edit.registrationClosesAt()));
        putIfPresent(fields, "startsAt", asDate(edit.startsAt()));
        putIfPresent(fields, "endsAt", asDate(edit.endsAt()));
        return fields;
    }

    private static Document asLiteral(String value) {
        return value == null ? null : new Document("$literal", value);
    }

    private static Date asDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    private static void putIfPresent(Document fields, String name, Object value) {
        if (value != null) {
            fields.put(name, value);
        }
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
        return takeSeat(eventId, studentId, now, null).isPresent();
    }

    /**
     * Takes a Seat only if the form revision is still the one the Registration module validated.
     * Whichever write wins first — this one or an Officer form edit — forces the loser to retry from a
     * fresh definition, so answers can never be stored against a definition that did not validate them.
     */
    public Optional<Long> takeSeatForForm(
            String eventId, String studentId, Instant now, int expectedFormRevision) {
        return takeSeat(eventId, studentId, now, Integer.valueOf(expectedFormRevision));
    }

    private Optional<Long> takeSeat(
            String eventId,
            String studentId,
            Instant now,
            Integer expectedFormRevision) {
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
        if (expectedFormRevision != null) {
            filter = filter.and("registrationFormRevision").is(expectedFormRevision);
        }

        Document eventFields = new Document("lastEnrollmentVersion", nextEnrollmentVersion())
                .append("registrationFormLocked", true);
        Document entry = new Document("studentId", studentId)
                .append("via", EnrollmentVia.DIRECT.name())
                .append("at", Date.from(now))
                .append("enrollmentVersion", "$lastEnrollmentVersion");
        Document ledgerFields = new Document(
                "enrolled",
                new Document("$concatArrays", List.of("$enrolled", List.of(entry))));
        AggregationUpdate update = AggregationUpdate.from(List.of(
                Aggregation.stage(new Document("$set", eventFields)),
                Aggregation.stage(new Document("$set", ledgerFields))));

        Event updated = mongoTemplate.findAndModify(
                new Query(filter),
                update,
                FindAndModifyOptions.options().returnNew(true),
                Event.class);
        if (updated == null) {
            return Optional.empty();
        }
        return updated.getEnrolled().stream()
                .filter(entryAfterWrite -> entryAfterWrite.studentId().equals(studentId))
                .findFirst()
                .map(EnrolledEntry::enrollmentVersion);
    }

    /**
     * Appends one Student to the ordered Waitlist and records that join in the same guarded write. The
     * write deliberately has no capacity guard: the caller reaches it only after losing takeSeat, while
     * every lifecycle and duplicate guard is repeated here so a changed Event can never accept an
     * invalid join.
     */
    public boolean joinWaitlist(String eventId, String studentId, Instant now) {
        Query query = new Query(Criteria.where("id")
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
                .ne(studentId));
        Update update = new Update().push("waitlist", studentId).inc("everQueuedCount", 1);

        return mongoTemplate.updateFirst(query, update, Event.class).getModifiedCount() > 0;
    }

    /** Leaving the Waitlist removes only that Student, preserves order, and never promotes anyone. */
    public boolean leaveWaitlist(String eventId, String studentId, Instant now) {
        Query query = new Query(Criteria.where("id")
                .is(eventId)
                .and("status")
                .is(EventStatus.PUBLISHED)
                .and("startsAt")
                .gt(now)
                .and("waitlist")
                .is(studentId));
        Update update = new Update().pull("waitlist", studentId);

        return mongoTemplate.updateFirst(query, update, Event.class).getModifiedCount() > 0;
    }

    /**
     * Frees one held Seat and, when the Waitlist is non-empty, fills that same Seat from its head in
     * one aggregation-pipeline update. MongoDB serializes concurrent writes to this Event document, so
     * each matching withdrawal can promote at most one Student and no separate promotion race exists.
     */
    public boolean withdrawEnrolled(String eventId, String studentId, Instant now) {
        Query query = new Query(Criteria.where("id")
                .is(eventId)
                .and("status")
                .is(EventStatus.PUBLISHED)
                .and("startsAt")
                .gt(now)
                .and("enrolled.studentId")
                .is(studentId));

        Document studentsToPromote = promotionCount(1);
        Document remainingEnrolled = new Document(
                "$filter",
                new Document("input", "$enrolled")
                        .append("as", "entry")
                        .append("cond", new Document("$ne", List.of("$$entry.studentId", studentId))));
        Document setFields = new Document(
                        "enrolled",
                        new Document(
                                "$concatArrays",
                                List.of(
                                        remainingEnrolled,
                                        promotedEntries(
                                                studentsToPromote,
                                                now,
                                                "$lastEnrollmentVersion"))))
                .append("waitlist", waitlistAfterPromotions(studentsToPromote))
                .append("promotedCount", promotedCountAfter(studentsToPromote));
        AggregationUpdate update = AggregationUpdate.from(List.of(
                Aggregation.stage(new Document(
                        "$set",
                        new Document("lastEnrollmentVersion", nextEnrollmentVersion()))),
                Aggregation.stage(new Document("$set", setFields))));

        return mongoTemplate.updateFirst(query, update, Event.class).getModifiedCount() > 0;
    }

    private static Document promotionCount(Object availablePlaces) {
        return new Document("$min", List.of(new Document("$size", "$waitlist"), availablePlaces));
    }

    private static Document promotedEntries(
            Document studentsToPromote, Instant now, Object enrollmentVersion) {
        return new Document(
                "$map",
                new Document("input", new Document("$slice", List.of("$waitlist", studentsToPromote)))
                        .append("as", "studentId")
                        .append(
                                "in",
                                new Document("studentId", "$$studentId")
                                        .append("via", EnrollmentVia.PROMOTED.name())
                                        .append("at", Date.from(now))
                                        .append("enrollmentVersion", enrollmentVersion)));
    }

    private static Document nextEnrollmentVersion() {
        return new Document(
                "$add",
                List.of(
                        new Document("$ifNull", List.of("$lastEnrollmentVersion", 0L)),
                        1L));
    }

    private static Document waitlistAfterPromotions(Document studentsToPromote) {
        Document waitlistSize = new Document("$size", "$waitlist");
        return new Document(
                "$cond",
                List.of(
                        new Document("$gt", List.of(studentsToPromote, 0)),
                        new Document("$slice", List.of("$waitlist", studentsToPromote, waitlistSize)),
                        "$waitlist"));
    }

    private static Document promotedCountAfter(Document studentsToPromote) {
        return new Document(
                "$add", List.of(new Document("$ifNull", List.of("$promotedCount", 0)), studentsToPromote));
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

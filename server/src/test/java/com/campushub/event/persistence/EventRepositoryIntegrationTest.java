package com.campushub.event.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.event.domain.EnrolledEntry;
import com.campushub.event.domain.EnrollmentVia;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventEdit;
import com.campushub.event.domain.EventStatus;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// Real MongoDB (Testcontainers): every write here is a guarded findAndModify whose filter carries a
// lifecycle rule, and that behaviour only means anything proven against a real database.
@Testcontainers
class EventRepositoryIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private static final Instant OPENS = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant CLOSES = Instant.parse("2026-03-10T00:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-03-20T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-03-20T02:00:00Z");

    private MongoTemplate mongoTemplate;
    private EventRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate =
                new MongoTemplate(MongoClients.create(MONGO_DB.getConnectionString()), "event-repository-test");
        repository = new EventRepository(mongoTemplate);
        repository.ensureIndexes();
    }

    @Test
    void insertingADraftSetsDraftStatusAndAnEmptySeatLedger() {
        String id = repository.insertDraft(draft("club-a"));

        Event event = repository.findScopedById(id, Set.of("club-a")).orElseThrow();
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.getEnrolled()).isEmpty();
        assertThat(event.getWaitlist()).isEmpty();
    }

    @Test
    void anOfficerOfClubACannotFindClubBsDraft() {
        String id = repository.insertDraft(draft("club-b"));

        assertThat(repository.findScopedById(id, Set.of("club-a"))).isEmpty();
        assertThat(repository.existsScoped(id, Set.of("club-a"))).isFalse();
    }

    @Test
    void everyFieldOnADraftIsEditableIncludingLoweringCapacity() {
        String id = repository.insertDraft(draft("club-a"));

        boolean edited = repository.edit(
                id,
                Set.of("club-a"),
                new EventEdit("New title", "New description", null, null, null, null, 1),
                STARTS.minusSeconds(1_000_000));

        assertThat(edited).isTrue();
        Event event = repository.findScopedById(id, Set.of("club-a")).orElseThrow();
        assertThat(event.getTitle()).isEqualTo("New title");
        assertThat(event.getCapacity()).isEqualTo(1);
    }

    @Test
    void aPublishedEventBeforeStartsAtMayHaveItsCapacityRaised() {
        String id = publishedEvent("club-a", 5);

        boolean edited = repository.edit(
                id, Set.of("club-a"), new EventEdit(null, null, null, null, null, null, 10), STARTS.minusSeconds(60));

        assertThat(edited).isTrue();
        assertThat(repository.findScopedById(id, Set.of("club-a")).orElseThrow().getCapacity())
                .isEqualTo(10);
    }

    @Test
    void aCapacityRaiseTreatsOtherSparseEditValuesAsLiterals() {
        String id = publishedEvent("club-a", 5);

        boolean edited = repository.edit(
                id,
                Set.of("club-a"),
                new EventEdit(
                        "$5 Robotics Night",
                        "$expr is part of the title, not Mongo syntax",
                        null,
                        null,
                        null,
                        null,
                        10),
                STARTS.minusSeconds(60));

        assertThat(edited).isTrue();
        Event event = repository.findScopedById(id, Set.of("club-a")).orElseThrow();
        assertThat(event.getTitle()).isEqualTo("$5 Robotics Night");
        assertThat(event.getDescription()).isEqualTo("$expr is part of the title, not Mongo syntax");
        assertThat(event.getCapacity()).isEqualTo(10);
    }

    @Test
    void aPublishedEventBeforeStartsAtMayNotHaveItsCapacityLowered() {
        String id = publishedEvent("club-a", 5);

        boolean edited = repository.edit(
                id, Set.of("club-a"), new EventEdit(null, null, null, null, null, null, 4), STARTS.minusSeconds(60));

        assertThat(edited).isFalse();
        assertThat(repository.findScopedById(id, Set.of("club-a")).orElseThrow().getCapacity())
                .isEqualTo(5);
    }

    @Test
    void nothingIsEditableFromStartsAtOnwardsIncludingCapacity() {
        String id = publishedEvent("club-a", 5);

        boolean titleEdit = repository.edit(
                id, Set.of("club-a"), new EventEdit("Late edit", null, null, null, null, null, null), STARTS);
        boolean capacityRaise =
                repository.edit(id, Set.of("club-a"), new EventEdit(null, null, null, null, null, null, 99), STARTS);

        assertThat(titleEdit).isFalse();
        assertThat(capacityRaise).isFalse();
    }

    @Test
    void publishingADraftMovesItToPublished() {
        String id = repository.insertDraft(draft("club-a"));

        boolean published = repository.publish(id, Set.of("club-a"));

        assertThat(published).isTrue();
        assertThat(repository.findScopedById(id, Set.of("club-a")).orElseThrow().getStatus())
                .isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void publishingAnAlreadyPublishedEventFailsThereIsNoUnPublishOrRePublish() {
        String id = publishedEvent("club-a", 5);

        assertThat(repository.publish(id, Set.of("club-a"))).isFalse();
    }

    @Test
    void anOfficerCannotPublishAnotherClubsDraft() {
        String id = repository.insertDraft(draft("club-b"));

        assertThat(repository.publish(id, Set.of("club-a"))).isFalse();
        assertThat(repository.findScopedById(id, Set.of("club-b")).orElseThrow().getStatus())
                .isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void officerCancelFreezesTheSeatLedgerRatherThanClearingIt() {
        String id = publishedEvent("club-a", 5);
        // Seeded directly rather than through takeSeat — the point here is only that cancel's guarded
        // update touches `status` and nothing else.
        List<EnrolledEntry> seededEnrolled =
                List.of(new EnrolledEntry("student-1", EnrollmentVia.DIRECT, STARTS.minusSeconds(1_000)));
        mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(id)),
                new Update().set("enrolled", seededEnrolled).set("waitlist", List.of("student-2")),
                Event.class);

        boolean cancelled = repository.cancelAsOfficer(id, Set.of("club-a"), STARTS.minusSeconds(60));

        assertThat(cancelled).isTrue();
        Event event = repository.findScopedById(id, Set.of("club-a")).orElseThrow();
        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(event.getEnrolled()).extracting(EnrolledEntry::studentId).containsExactly("student-1");
        assertThat(event.getWaitlist()).containsExactly("student-2");
    }

    @Test
    void officerCannotCancelAnotherClubsEvent() {
        String id = publishedEvent("club-b", 5);

        assertThat(repository.cancelAsOfficer(id, Set.of("club-a"), STARTS.minusSeconds(60)))
                .isFalse();
    }

    @Test
    void aCompletedEventCannotBeCancelled() {
        String id = publishedEvent("club-a", 5);

        assertThat(repository.cancelAsOfficer(id, Set.of("club-a"), ENDS.plusSeconds(60)))
                .isFalse();
    }

    @Test
    void adminCancelReachesAnyClubsEvent() {
        String id = publishedEvent("club-a", 5);

        boolean cancelled = repository.cancelAsAdmin(id, STARTS.minusSeconds(60));

        assertThat(cancelled).isTrue();
        assertThat(repository.findScopedById(id, Set.of("club-a")).orElseThrow().getStatus())
                .isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void adminCancelFailsOnAnAlreadyCancelledEvent() {
        String id = publishedEvent("club-a", 5);
        repository.cancelAsAdmin(id, STARTS.minusSeconds(60));

        assertThat(repository.cancelAsAdmin(id, STARTS.minusSeconds(60))).isFalse();
        assertThat(repository.exists(id)).isTrue();
    }

    private String publishedEvent(String clubId, int capacity) {
        String id = repository.insertDraft(
                new Event(clubId, "Title", "Description", OPENS, CLOSES, STARTS, ENDS, capacity));
        repository.publish(id, Set.of(clubId));
        return id;
    }

    private static Event draft(String clubId) {
        return new Event(clubId, "Title", "Description", OPENS, CLOSES, STARTS, ENDS, 5);
    }
}

package com.campushub.event.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.event.domain.EnrolledEntry;
import com.campushub.event.domain.EnrollmentVia;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.EventStatus;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// Real MongoDB (Testcontainers): takeSeat is the one guarded write the whole capacity claim rests on —
// see docs/adr/04-define-registration-capacity-and-waitlist.md. Every guard here is proven against a
// real database, including the concurrency claim, which a mock cannot prove at all.
@Testcontainers
class EventRepositorySeatLedgerIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private static final Instant OPENS = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant CLOSES = Instant.parse("2026-03-10T00:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-03-20T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-03-20T02:00:00Z");
    private static final Instant WITHIN_WINDOW = OPENS.plusSeconds(60);

    private MongoTemplate mongoTemplate;
    private EventRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO_DB.getConnectionString()), "event-seat-ledger-test");
        repository = new EventRepository(mongoTemplate);
        repository.ensureIndexes();
    }

    @Test
    void takeSeatSucceedsWithinTheWindowAndRecordsADirectEntry() {
        String id = publishedEvent(5);

        boolean taken = repository.takeSeat(id, "student-1", WITHIN_WINDOW);

        assertThat(taken).isTrue();
        Event event = repository.findById(id).orElseThrow();
        assertThat(event.getEnrolled()).hasSize(1);
        EnrolledEntry entry = event.getEnrolled().get(0);
        assertThat(entry.studentId()).isEqualTo("student-1");
        assertThat(entry.via()).isEqualTo(EnrollmentVia.DIRECT);
        assertThat(entry.at()).isEqualTo(WITHIN_WINDOW);
    }

    @Test
    void aDoubleClickedRegistrationIsAnIdempotentNoOp() {
        String id = publishedEvent(5);
        repository.takeSeat(id, "student-1", WITHIN_WINDOW);

        boolean secondAttempt = repository.takeSeat(id, "student-1", WITHIN_WINDOW.plusSeconds(1));

        assertThat(secondAttempt).isFalse();
        assertThat(repository.findById(id).orElseThrow().getEnrolled()).hasSize(1);
    }

    @Test
    void takeSeatFailsWhenAlreadyWaitlisted() {
        String id = publishedEvent(5);
        mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(id)),
                new Update().push("waitlist", "student-1"),
                Event.class);

        boolean taken = repository.takeSeat(id, "student-1", WITHIN_WINDOW);

        assertThat(taken).isFalse();
        assertThat(repository.findById(id).orElseThrow().getEnrolled()).isEmpty();
    }

    @Test
    void takeSeatFailsWhenTheEventIsAlreadyFull() {
        String id = publishedEvent(1);
        assertThat(repository.takeSeat(id, "student-1", WITHIN_WINDOW)).isTrue();

        boolean losingWriter = repository.takeSeat(id, "student-2", WITHIN_WINDOW);

        assertThat(losingWriter).isFalse();
        assertThat(repository.findById(id).orElseThrow().getEnrolled())
                .extracting(EnrolledEntry::studentId)
                .containsExactly("student-1");
    }

    @Test
    void takeSeatFailsBeforeRegistrationOpens() {
        String id = publishedEvent(5);

        assertThat(repository.takeSeat(id, "student-1", OPENS.minusSeconds(1))).isFalse();
    }

    @Test
    void takeSeatSucceedsAtTheExactInstantRegistrationOpens() {
        String id = publishedEvent(5);

        assertThat(repository.takeSeat(id, "student-1", OPENS)).isTrue();
    }

    @Test
    void takeSeatFailsAtTheExactInstantRegistrationCloses() {
        String id = publishedEvent(5);

        assertThat(repository.takeSeat(id, "student-1", CLOSES)).isFalse();
    }

    @Test
    void takeSeatFailsAtTheExactInstantTheEventStarts() {
        String id = publishedEvent(5);

        assertThat(repository.takeSeat(id, "student-1", STARTS)).isFalse();
    }

    @Test
    void takeSeatFailsOnACancelledEvent() {
        String id = publishedEvent(5);
        mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(id)),
                new Update().set("status", EventStatus.CANCELLED),
                Event.class);

        assertThat(repository.takeSeat(id, "student-1", WITHIN_WINDOW)).isFalse();
    }

    @Test
    void takeSeatFailsOnADraftEvent() {
        String id = repository.insertDraft(draft(5));

        assertThat(repository.takeSeat(id, "student-1", WITHIN_WINDOW)).isFalse();
    }

    @Test
    void findByIdReturnsAnEventRegardlessOfStatus() {
        String id = repository.insertDraft(draft(5));

        assertThat(repository.findById(id)).isPresent();
        assertThat(repository.findById("000000000000000000000000")).isEmpty();
    }

    @Test
    void findEnrolledReturnsOnlyEventsWhereTheStudentHoldsASeat() {
        // Unique ids: this Testcontainers instance is shared across every test in the class, and other
        // tests here also enrol "student-1"-style ids — a global count must not see their leftovers.
        String targetStudent = "student-" + UUID.randomUUID();
        String otherStudent = "student-" + UUID.randomUUID();
        String enrolledEventId = publishedEvent(5);
        repository.takeSeat(enrolledEventId, targetStudent, WITHIN_WINDOW);
        String otherEventId = publishedEvent(5);
        repository.takeSeat(otherEventId, otherStudent, WITHIN_WINDOW);

        EventPage page = repository.findEnrolled(targetStudent, 0, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(Event::getId).containsExactly(enrolledEventId);
    }

    @Test
    @Tag("concurrency")
    void nParallelRegistrationsAgainstACapacityOfMProduceExactlyMEnrolments() throws InterruptedException {
        int capacity = 10;
        int attempts = 40;
        String id = publishedEvent(capacity);

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startingGate = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = IntStream.range(0, attempts)
                .<Callable<Boolean>>mapToObj(index -> () -> {
                    startingGate.await();
                    return repository.takeSeat(id, "student-" + index, WITHIN_WINDOW);
                })
                .toList();

        try {
            List<Future<Boolean>> futures = tasks.stream().map(pool::submit).toList();
            startingGate.countDown();

            AtomicInteger successes = new AtomicInteger();
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successes.incrementAndGet();
                }
            }

            assertThat(successes.get()).isEqualTo(capacity);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AssertionError(e);
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        Event event = repository.findById(id).orElseThrow();
        assertThat(event.getEnrolled()).hasSize(capacity);
        assertThat(event.getEnrolled()).extracting(EnrolledEntry::studentId).doesNotHaveDuplicates();
    }

    private String publishedEvent(int capacity) {
        String id = repository.insertDraft(draft(capacity));
        repository.publish(id, Set.of("club-a"));
        return id;
    }

    private static Event draft(int capacity) {
        return new Event("club-a", "Title", "Description", OPENS, CLOSES, STARTS, ENDS, capacity);
    }
}

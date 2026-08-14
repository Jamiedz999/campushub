package com.campushub.event.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.event.domain.EnrolledEntry;
import com.campushub.event.domain.EnrollmentVia;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventEdit;
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
        assertThat(entry.enrollmentVersion()).isPositive();
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
    void joinWaitlistAppendsTheStudentAndCountsTheJoinInTheSameWrite() {
        String id = publishedEvent(1);
        repository.takeSeat(id, "student-1", WITHIN_WINDOW);

        boolean joined = repository.joinWaitlist(id, "student-2", WITHIN_WINDOW);

        assertThat(joined).isTrue();
        Event event = repository.findById(id).orElseThrow();
        assertThat(event.getWaitlist()).containsExactly("student-2");
        assertThat(event.getEverQueuedCount()).isEqualTo(1);
    }

    @Test
    void aStudentWhoJoinsLeavesAndJoinsAgainCountsBothJoinsInConversion() {
        String id = publishedEvent(1);
        repository.takeSeat(id, "student-1", WITHIN_WINDOW);
        repository.joinWaitlist(id, "student-2", WITHIN_WINDOW);

        assertThat(repository.leaveWaitlist(id, "student-2", WITHIN_WINDOW.plusSeconds(1)))
                .isTrue();
        assertThat(repository.joinWaitlist(id, "student-2", WITHIN_WINDOW.plusSeconds(2)))
                .isTrue();
        assertThat(repository.withdrawEnrolled(id, "student-1", WITHIN_WINDOW.plusSeconds(3)))
                .isTrue();

        Event event = repository.findById(id).orElseThrow();
        assertThat(event.getWaitlist()).isEmpty();
        assertThat(event.getEnrolled()).singleElement().satisfies(entry -> {
            assertThat(entry.studentId()).isEqualTo("student-2");
            assertThat(entry.via()).isEqualTo(EnrollmentVia.PROMOTED);
            assertThat(entry.at()).isEqualTo(WITHIN_WINDOW.plusSeconds(3));
            assertThat(entry.enrollmentVersion()).isPositive();
        });
        assertThat(event.getEverQueuedCount()).isEqualTo(2);
        assertThat(event.getPromotedCount()).isEqualTo(1);
        assertThat(event.waitlistConversion()).isEqualTo(0.5);
    }

    @Test
    void raisingCapacityPromotesAsManyWaitingStudentsAsNowFitInTheSameWrite() {
        String id = publishedEvent(2);
        repository.takeSeat(id, "student-1", WITHIN_WINDOW);
        repository.takeSeat(id, "student-2", WITHIN_WINDOW);
        repository.joinWaitlist(id, "student-3", WITHIN_WINDOW);
        repository.joinWaitlist(id, "student-4", WITHIN_WINDOW);
        repository.joinWaitlist(id, "student-5", WITHIN_WINDOW);
        EventEdit raiseCapacity = new EventEdit(null, null, null, null, null, null, 4);

        boolean raised = repository.edit(id, Set.of("club-a"), raiseCapacity, WITHIN_WINDOW.plusSeconds(1));

        assertThat(raised).isTrue();
        Event event = repository.findById(id).orElseThrow();
        assertThat(event.getCapacity()).isEqualTo(4);
        assertThat(event.getEnrolled())
                .extracting(EnrolledEntry::studentId)
                .containsExactly("student-1", "student-2", "student-3", "student-4");
        assertThat(event.getEnrolled().subList(2, 4))
                .allSatisfy(entry -> {
                    assertThat(entry.via()).isEqualTo(EnrollmentVia.PROMOTED);
                    assertThat(entry.at()).isEqualTo(WITHIN_WINDOW.plusSeconds(1));
                    assertThat(entry.enrollmentVersion()).isPositive();
                });
        assertThat(event.getWaitlist()).containsExactly("student-5");
        assertThat(event.getPromotedCount()).isEqualTo(2);
    }

    @Test
    void capacityRaiseCannotMoveStartsAtToNowAndPromoteInTheSameWrite() {
        String id = publishedEvent(1);
        repository.takeSeat(id, "student-1", WITHIN_WINDOW);
        repository.joinWaitlist(id, "student-2", WITHIN_WINDOW);
        Instant now = WITHIN_WINDOW.plusSeconds(1);
        EventEdit startNowAndRaiseCapacity =
                new EventEdit(null, null, null, null, now, null, 2);

        boolean edited = repository.edit(id, Set.of("club-a"), startNowAndRaiseCapacity, now);

        assertThat(edited).isFalse();
        Event event = repository.findById(id).orElseThrow();
        assertThat(event.getStartsAt()).isEqualTo(STARTS);
        assertThat(event.getCapacity()).isEqualTo(1);
        assertThat(event.getEnrolled()).extracting(EnrolledEntry::studentId).containsExactly("student-1");
        assertThat(event.getWaitlist()).containsExactly("student-2");
        assertThat(event.getPromotedCount()).isZero();
    }

    @Test
    void withdrawalStillPromotesAfterTheRegistrationWindowCloses() {
        String id = publishedEvent(1);
        repository.takeSeat(id, "student-1", WITHIN_WINDOW);
        repository.joinWaitlist(id, "student-2", WITHIN_WINDOW);

        boolean withdrawn = repository.withdrawEnrolled(id, "student-1", CLOSES.plusSeconds(1));

        assertThat(withdrawn).isTrue();
        assertThat(repository.findById(id).orElseThrow().getEnrolled())
                .extracting(EnrolledEntry::studentId)
                .containsExactly("student-2");
    }

    @Test
    void everySeatLedgerWriteIsRefusedAtTheExactInstantTheEventStarts() {
        String id = publishedEvent(1);
        repository.takeSeat(id, "student-1", WITHIN_WINDOW);
        repository.joinWaitlist(id, "student-2", WITHIN_WINDOW);
        EventEdit raiseCapacity = new EventEdit(null, null, null, null, null, null, 2);

        assertThat(repository.takeSeat(id, "student-3", STARTS)).isFalse();
        assertThat(repository.joinWaitlist(id, "student-3", STARTS)).isFalse();
        assertThat(repository.leaveWaitlist(id, "student-2", STARTS)).isFalse();
        assertThat(repository.withdrawEnrolled(id, "student-1", STARTS)).isFalse();
        assertThat(repository.edit(id, Set.of("club-a"), raiseCapacity, STARTS)).isFalse();

        Event event = repository.findById(id).orElseThrow();
        assertThat(event.getCapacity()).isEqualTo(1);
        assertThat(event.getEnrolled()).extracting(EnrolledEntry::studentId).containsExactly("student-1");
        assertThat(event.getWaitlist()).containsExactly("student-2");
        assertThat(event.getPromotedCount()).isZero();
        assertThat(event.getEverQueuedCount()).isEqualTo(1);
    }

    // The freeze is a guard, not a moment. The test above pins the exact instant, which is where an
    // off-by-one lives; this one pins the half-open interval after it, which is where a guard written as
    // `now == startsAt` would pass the instant and let every later write through. A Roster the door
    // checks against cannot change once the Event has started — including after it has ended.
    @Test
    void everySeatLedgerWriteStaysRefusedAfterTheEventHasStarted() {
        String id = publishedEvent(1);
        repository.takeSeat(id, "student-1", WITHIN_WINDOW);
        repository.joinWaitlist(id, "student-2", WITHIN_WINDOW);
        EventEdit raiseCapacity = new EventEdit(null, null, null, null, null, null, 2);

        for (Instant afterStart : List.of(STARTS.plusSeconds(1), ENDS, ENDS.plusSeconds(86_400))) {
            assertThat(repository.takeSeat(id, "student-3", afterStart)).isFalse();
            assertThat(repository.joinWaitlist(id, "student-3", afterStart)).isFalse();
            assertThat(repository.leaveWaitlist(id, "student-2", afterStart)).isFalse();
            assertThat(repository.withdrawEnrolled(id, "student-1", afterStart)).isFalse();
            assertThat(repository.edit(id, Set.of("club-a"), raiseCapacity, afterStart)).isFalse();
        }

        Event event = repository.findById(id).orElseThrow();
        assertThat(event.getCapacity()).isEqualTo(1);
        assertThat(event.getEnrolled()).extracting(EnrolledEntry::studentId).containsExactly("student-1");
        assertThat(event.getWaitlist()).containsExactly("student-2");
        // No withdrawal succeeded, so nothing freed a Seat, so nobody was promoted into the frozen Roster.
        assertThat(event.getPromotedCount()).isZero();
        assertThat(event.getEverQueuedCount()).isEqualTo(1);
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

    @Test
    @Tag("concurrency")
    void parallelWithdrawalsOfOneSeatPromoteExactlyOneStudent() throws InterruptedException {
        int attempts = 20;
        String id = publishedEvent(1);
        repository.takeSeat(id, "enrolled-student", WITHIN_WINDOW);
        repository.joinWaitlist(id, "waiting-student-1", WITHIN_WINDOW);
        repository.joinWaitlist(id, "waiting-student-2", WITHIN_WINDOW);

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startingGate = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = IntStream.range(0, attempts)
                .<Callable<Boolean>>mapToObj(index -> () -> {
                    startingGate.await();
                    return repository.withdrawEnrolled(
                            id, "enrolled-student", WITHIN_WINDOW.plusSeconds(1));
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

            assertThat(successes.get()).isEqualTo(1);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AssertionError(e);
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        Event event = repository.findById(id).orElseThrow();
        assertThat(event.getEnrolled())
                .extracting(EnrolledEntry::studentId)
                .containsExactly("waiting-student-1");
        assertThat(event.getWaitlist()).containsExactly("waiting-student-2");
        assertThat(event.getPromotedCount()).isEqualTo(1);
        assertThat(event.getPromotedCount()).isLessThanOrEqualTo(event.getEverQueuedCount());
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

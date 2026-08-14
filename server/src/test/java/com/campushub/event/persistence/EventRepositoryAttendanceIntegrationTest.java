package com.campushub.event.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.AttendanceMethod;
import com.campushub.event.domain.AttendanceEntry;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventStatus;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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

// Real MongoDB (Testcontainers): the attendance write is the one Seat Ledger write that deliberately
// omits the startsAt freeze, because it exists to happen after the Event has begun — see
// docs/adr/07-define-qr-checkin-and-anti-fraud.md. Every guard it carries instead is proven here
// against a real database, including the idempotency claim under genuine concurrency.
@Testcontainers
class EventRepositoryAttendanceIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private static final Set<String> CLUB_IDS = Set.of("club-a");
    private static final Instant OPENS = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant CLOSES = Instant.parse("2026-03-10T00:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-03-20T18:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-03-20T20:00:00Z");
    private static final Instant BEFORE_REGISTRATION_CLOSES = OPENS.plusSeconds(60);
    private static final Instant DOOR_OPENS = STARTS.minus(EventModule.CHECK_IN_OPENS_BEFORE_START);
    private static final Instant MID_EVENT = STARTS.plusSeconds(600);

    private MongoTemplate mongoTemplate;
    private EventRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate =
                new MongoTemplate(MongoClients.create(MONGO_DB.getConnectionString()), "event-attendance-test");
        repository = new EventRepository(mongoTemplate);
        repository.ensureIndexes();
    }

    @Test
    void aScanAfterTheEventHasStartedSucceedsWhereEveryOtherSeatLedgerWriteIsRefused() {
        String id = eventWithEnrolled("student-1");

        // The freeze that refuses these three is exactly the one this write leaves out.
        assertThat(repository.takeSeat(id, "student-2", MID_EVENT)).isFalse();
        assertThat(repository.joinWaitlist(id, "student-2", MID_EVENT)).isFalse();
        assertThat(repository.withdrawEnrolled(id, "student-1", MID_EVENT)).isFalse();

        assertThat(repository.recordScannedAttendance(id, "student-1", MID_EVENT)).isPresent();
        assertThat(attendance(id)).singleElement().satisfies(entry -> {
            assertThat(entry.studentId()).isEqualTo("student-1");
            assertThat(entry.at()).isEqualTo(MID_EVENT);
            assertThat(entry.method()).isEqualTo(AttendanceMethod.SCANNED);
        });
    }

    @Test
    void checkInOpensFifteenMinutesBeforeStartsAtAndNotAMomentEarlier() {
        String id = eventWithEnrolled("student-1");

        assertThat(repository.recordScannedAttendance(id, "student-1", DOOR_OPENS.minusMillis(1)))
                .isEmpty();
        assertThat(repository.recordScannedAttendance(id, "student-1", DOOR_OPENS)).isPresent();
    }

    @Test
    void checkInClosesWhenTheEventEnds() {
        String id = eventWithEnrolled("student-1");

        assertThat(repository.recordScannedAttendance(id, "student-1", ENDS)).isEmpty();
        assertThat(repository.recordScannedAttendance(id, "student-1", ENDS.minusMillis(1)))
                .isPresent();
    }

    @Test
    void aWaitlistedStudentIsNotOnTheRosterAndCannotCheckIn() {
        String id = eventWithEnrolled("student-1");
        repository.joinWaitlist(id, "hopeful-student", BEFORE_REGISTRATION_CLOSES);

        assertThat(repository.recordScannedAttendance(id, "hopeful-student", MID_EVENT))
                .isEmpty();
        assertThat(attendance(id)).isEmpty();
    }

    @Test
    void aStudentWhoNeverRegisteredCannotCheckIn() {
        String id = eventWithEnrolled("student-1");

        assertThat(repository.recordScannedAttendance(id, "stranger", MID_EVENT)).isEmpty();
        assertThat(attendance(id)).isEmpty();
    }

    @Test
    void aSecondScanChangesNothing() {
        String id = eventWithEnrolled("student-1");
        repository.recordScannedAttendance(id, "student-1", MID_EVENT);

        assertThat(repository.recordScannedAttendance(id, "student-1", MID_EVENT.plusSeconds(30)))
                .isEmpty();
        assertThat(attendance(id)).singleElement().satisfies(entry -> assertThat(entry.at())
                .isEqualTo(MID_EVENT));
    }

    @Test
    void aCancelledEventHasNoDoor() {
        String id = eventWithEnrolled("student-1");
        repository.cancelAsOfficer(id, CLUB_IDS, BEFORE_REGISTRATION_CLOSES);

        assertThat(repository.recordScannedAttendance(id, "student-1", MID_EVENT)).isEmpty();
    }

    @Test
    void aDraftEventHasNoDoorEither() {
        String id = eventWithEnrolled("student-1");
        setStatus(id, EventStatus.DRAFT);

        assertThat(repository.recordScannedAttendance(id, "student-1", MID_EVENT)).isEmpty();
    }

    @Test
    void aManualOverrideIsAlwaysDistinguishableFromAScan() {
        String id = eventWithEnrolled("scanner", "no-phone");
        repository.recordScannedAttendance(id, "scanner", MID_EVENT);

        assertThat(repository.recordManualAttendance(id, CLUB_IDS, "no-phone", MID_EVENT))
                .isPresent();
        assertThat(attendance(id))
                .extracting(AttendanceEntry::studentId, AttendanceEntry::method)
                .containsExactly(
                        tuple("scanner", AttendanceMethod.SCANNED), tuple("no-phone", AttendanceMethod.MANUAL));
    }

    @Test
    void anOverrideNeverOverwritesAScan() {
        String id = eventWithEnrolled("student-1");
        repository.recordScannedAttendance(id, "student-1", MID_EVENT);

        assertThat(repository.recordManualAttendance(id, CLUB_IDS, "student-1", MID_EVENT.plusSeconds(60)))
                .isEmpty();
        assertThat(attendance(id))
                .singleElement()
                .satisfies(entry -> assertThat(entry.method()).isEqualTo(AttendanceMethod.SCANNED));
    }

    @Test
    void anOfficerOfAnotherClubCannotMarkAnyonePresent() {
        String id = eventWithEnrolled("student-1");

        assertThat(repository.recordManualAttendance(id, Set.of("club-b"), "student-1", MID_EVENT))
                .isEmpty();
        assertThat(attendance(id)).isEmpty();
    }

    @Test
    void theWinningWriteReturnsTheEventItJustChanged() {
        String id = eventWithEnrolled("student-1");

        Event updated = repository.recordScannedAttendance(id, "student-1", MID_EVENT).orElseThrow();

        assertThat(updated.getTitle()).isEqualTo("Title");
        assertThat(updated.getAttendance()).hasSize(1);
    }

    @Test
    void attendanceDefaultsAreBackfilledOnDocumentsWrittenBeforeCheckInExisted() {
        String id = eventWithEnrolled("student-1");
        unsetAttendance(id);
        assertThat(repository.findById(id).orElseThrow().getAttendance()).isEmpty();

        repository.initializeAttendance();

        assertThat(repository.recordScannedAttendance(id, "student-1", MID_EVENT)).isPresent();
    }

    @Test
    @Tag("concurrency")
    void nParallelScansByOneStudentProduceExactlyOneAttendanceRecord() throws InterruptedException {
        int attempts = 30;
        String id = eventWithEnrolled("student-1");

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startingGate = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = IntStream.range(0, attempts)
                .<Callable<Boolean>>mapToObj(index -> () -> {
                    startingGate.await();
                    return repository
                            .recordScannedAttendance(id, "student-1", MID_EVENT.plusSeconds(index))
                            .isPresent();
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
        } catch (java.util.concurrent.ExecutionException failedTask) {
            throw new AssertionError(failedTask);
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(attendance(id)).hasSize(1);
    }

    private List<AttendanceEntry> attendance(String eventId) {
        return repository.findById(eventId).orElseThrow().getAttendance();
    }

    private String eventWithEnrolled(String... studentIds) {
        String id = repository.insertDraft(
                new Event("club-a", "Title", "Description", OPENS, CLOSES, STARTS, ENDS, 10));
        repository.publish(id, CLUB_IDS);
        for (String studentId : studentIds) {
            enrol(id, studentId);
        }
        return id;
    }

    private void enrol(String eventId, String studentId) {
        repository.takeSeat(eventId, studentId, BEFORE_REGISTRATION_CLOSES);
    }

    // Reproduces an Event document written before this Issue: no attendance field at all.
    private void unsetAttendance(String eventId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(eventId)), new Update().unset("attendance"), Event.class);
    }

    // The repository publishes forward-only and never un-publishes, so a Draft with a Roster — which
    // cannot happen through the API — is built here directly.
    private void setStatus(String eventId, EventStatus status) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(eventId)), new Update().set("status", status), Event.class);
    }
}

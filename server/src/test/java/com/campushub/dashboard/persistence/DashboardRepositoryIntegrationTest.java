package com.campushub.dashboard.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.campushub.dashboard.DashboardModule.ClubMonthTotals;
import com.campushub.dashboard.DashboardModule.EventTotals;
import com.campushub.dashboard.DashboardModule.ExcludedEvents;
import com.campushub.dashboard.DashboardModule.MetricTotals;
import com.campushub.dashboard.domain.ClubScope;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// Real MongoDB (Testcontainers): a metric is a claim about what a query planner returns, and every
// number below is hand-computed in the comment beside the fixture that produces it. See
// docs/adr/09-define-attendance-dashboard.md.
//
// The fixtures are written as raw BSON rather than through the Event module, deliberately. The
// dashboard is the one module allowed to read a peer's collection and it does so by field name, so the
// field names are the contract, and a test that spelled them out through a Java class it is forbidden
// to import would be testing the wrong thing.
@Testcontainers
class DashboardRepositoryIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-14T10:15:00Z");
    private static final ClubScope EVERY_CLUB = ClubScope.allClubs();
    private static final ClubScope BOTH_CLUBS = ClubScope.of(Set.of("club-a", "club-b"));
    private static final ClubScope CLUB_A = ClubScope.of(Set.of("club-a"));
    private static final ClubScope NO_CLUBS = ClubScope.of(Set.of());

    private MongoTemplate mongoTemplate;
    private DashboardRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO_DB.getConnectionString()), "dashboard-test-" + UUID.randomUUID());
        repository = new DashboardRepository(mongoTemplate);
        repository.ensureIndexes();
    }

    @Test
    void everyTotalMatchesTheHandComputedFixture() {
        seedPopulation();

        MetricTotals totals = repository.totals(EVERY_CLUB, FROM, NOW);

        // Three finished Published Events: 100 + 50 + 30 seats, 80 + 50 + 12 enrolled,
        // 60 + 40 + 9 attended, of which 10 + 0 + 5 were manual overrides.
        assertThat(totals)
                .isEqualTo(new MetricTotals(
                        3, // eventsRun
                        180, // capacity
                        142, // enrolled
                        109, // attended
                        10, // promoted: 6 + 4 + 0
                        22, // everQueued: 12 + 10 + 0
                        14, // unmetDemand: 5 + 9 + 0, the Waitlist's length at the end
                        15)); // manualAttendance
    }

    @Test
    void aCancelledEventAndADraftEventChangeNoNumberAndAreCountedInstead() {
        seedPopulation();
        MetricTotals withoutExclusions = repository.totals(EVERY_CLUB, FROM, NOW);
        List<ClubMonthTotals> activityWithoutExclusions = repository.clubMonthTotals(EVERY_CLUB, FROM, NOW, DUBLIN);

        seedExclusions();

        // The Cancelled Event carries 40 enrolled and 20 queued that never had the chance to attend, and
        // the Draft was never offered to anyone. Neither may move a single figure.
        assertThat(repository.totals(EVERY_CLUB, FROM, NOW)).isEqualTo(withoutExclusions);
        assertThat(repository.clubMonthTotals(EVERY_CLUB, FROM, NOW, DUBLIN)).isEqualTo(activityWithoutExclusions);
        assertThat(repository.excludedEvents(EVERY_CLUB, FROM, NOW)).isEqualTo(new ExcludedEvents(1, 1, 1));
    }

    @Test
    void waitlistConversionCountsTheStudentWhoJoinedTheQueueAndThenLeftIt() {
        // Robotics: 12 ever queued, 6 promoted, 5 still queued at the end. The missing one withdrew from
        // the Waitlist, and is in neither array afterwards — the case that broke the original arithmetic,
        // where the denominator was promoted + waitlist and so came to 11 instead of 12.
        insertEvent(published("club-a", "Robotics talk", "2026-03-10T20:00:00Z", 100, 80, 60, 10, 5, 6, 12));

        MetricTotals totals = repository.totals(EVERY_CLUB, FROM, NOW);

        assertThat(totals.everQueued()).isEqualTo(12);
        assertThat(totals.promoted()).isEqualTo(6);
        assertThat(totals.unmetDemand()).isEqualTo(5);
        assertThat(totals.promoted() + totals.unmetDemand()).isNotEqualTo(totals.everQueued());
    }

    @Test
    void anOfficerSeesOnlyTheirOwnClubsNumbers() {
        seedPopulation();
        seedExclusions();

        MetricTotals clubA = repository.totals(CLUB_A, FROM, NOW);

        // club-a's two finished Events only: 100 + 50 seats, 80 + 50 enrolled, 60 + 40 attended.
        assertThat(clubA.eventsRun()).isEqualTo(2);
        assertThat(clubA.capacity()).isEqualTo(150);
        assertThat(clubA.enrolled()).isEqualTo(130);
        assertThat(clubA.attended()).isEqualTo(100);
        assertThat(repository.clubMonthTotals(CLUB_A, FROM, NOW, DUBLIN))
                .extracting(ClubMonthTotals::clubId)
                .containsOnly("club-a");
        assertThat(repository.eventTotals(CLUB_A, FROM, NOW))
                .extracting(EventTotals::clubId)
                .containsOnly("club-a");
        // club-b's still-running Event is another Club's excluded row and must not be counted either.
        assertThat(repository.excludedEvents(CLUB_A, FROM, NOW))
                .isEqualTo(new ExcludedEvents(1, 1, 0));
    }

    @Test
    void anOfficerWithNoGrantsSeesNothingRatherThanEverything() {
        seedPopulation();

        assertThat(repository.totals(NO_CLUBS, FROM, NOW)).isEqualTo(MetricTotals.empty());
        assertThat(repository.clubMonthTotals(NO_CLUBS, FROM, NOW, DUBLIN)).isEmpty();
        assertThat(repository.eventTotals(NO_CLUBS, FROM, NOW)).isEmpty();
        assertThat(repository.excludedEvents(NO_CLUBS, FROM, NOW)).isEqualTo(ExcludedEvents.none());
    }

    @Test
    void clubActivityIsOneRowPerClubPerMonth() {
        seedPopulation();
        seedExclusions();

        // Robotics ended in March and Hack night in April, both club-a; Choir is club-b's one April
        // Event. Three Clubs' worth of months would collapse into two rows if either dimension were
        // dropped, which is what "per Club, per month" is there to prevent.
        assertThat(repository.clubMonthTotals(BOTH_CLUBS, FROM, NOW, DUBLIN))
                .containsExactly(
                        new ClubMonthTotals("club-a", "2026-03", 1, 100, 80, 60, 5),
                        new ClubMonthTotals("club-a", "2026-04", 1, 50, 50, 40, 9),
                        new ClubMonthTotals("club-b", "2026-04", 1, 30, 12, 9, 0));
    }

    @Test
    void aMonthBucketIsTheOneTheEventEndedInOnTheCampusClock() {
        // 00:30 Dublin on 1 May is 23:30 UTC on 30 April. It belongs to May, and the timezone is the only
        // thing that makes that true.
        insertEvent(published("club-b", "Late finish", "2026-04-30T23:30:00Z", 10, 4, 3, 0, 0, 0, 0));

        assertThat(repository.clubMonthTotals(BOTH_CLUBS, FROM, NOW, DUBLIN))
                .containsExactly(new ClubMonthTotals("club-b", "2026-05", 1, 10, 4, 3, 0));
    }

    @Test
    void theEventRowsArriveMostRecentlyFinishedFirstAndCarryTheirWaitlistLength() {
        seedPopulation();

        List<EventTotals> events = repository.eventTotals(BOTH_CLUBS, FROM, NOW);

        assertThat(events).extracting(EventTotals::title).containsExactly("Choir", "Hack night", "Robotics talk");
        assertThat(events)
                .extracting(EventTotals::enrolled, EventTotals::attended, EventTotals::unmetDemand)
                .containsExactly(
                        tuple(12L, 9L, 0L),
                        tuple(50L, 40L, 9L),
                        tuple(80L, 60L, 5L));
        assertThat(events.getFirst().eventId()).hasSize(24);
    }

    @Test
    void anEventThatStartedBeforeTheRangeAndHasNotFinishedIsStillCountedAsExcluded() {
        // The row that would otherwise go missing twice over: absent from every metric because it has
        // not finished, and absent from the count of what is missing because it did not start here.
        Document longRunning = published("club-a", "Reading week", "2026-08-20T18:00:00Z", 30, 20, 0, 0, 0, 0, 0)
                .append("startsAt", Date.from(Instant.parse("2025-12-01T09:00:00Z")));
        insertEvent(longRunning);

        assertThat(repository.excludedEvents(EVERY_CLUB, FROM, NOW)).isEqualTo(new ExcludedEvents(0, 0, 1));
    }

    @Test
    void anEventThatHasNotStartedYetIsNeitherCountedNorReportedAsMissing() {
        // Nothing about it is missing: it was never going to be in a window that ends now.
        Document notYet = published("club-a", "Next term", "2026-10-01T18:00:00Z", 30, 20, 0, 0, 0, 0, 0)
                .append("startsAt", Date.from(Instant.parse("2026-10-01T16:00:00Z")));
        insertEvent(notYet);

        assertThat(repository.excludedEvents(EVERY_CLUB, FROM, NOW)).isEqualTo(ExcludedEvents.none());
    }

    @Test
    void anEventThatEndedOutsideTheRangeIsNotInAnyOfIt() {
        seedPopulation();
        insertEvent(published("club-a", "Last year", "2025-06-01T20:00:00Z", 999, 999, 999, 999, 999, 999, 999));

        assertThat(repository.totals(EVERY_CLUB, FROM, NOW).eventsRun()).isEqualTo(3);
        assertThat(repository.eventTotals(EVERY_CLUB, FROM, NOW)).hasSize(3);
    }

    @Test
    void anEventStoredBeforeAttendanceOrTheWaitlistCountersExistedCountsAsZeroRatherThanFailing() {
        // The shape Mongock's backfills exist to fix, read in the window before they have run.
        Document legacy = new Document("clubId", "club-a")
                .append("title", "Legacy talk")
                .append("status", "PUBLISHED")
                .append("startsAt", Date.from(Instant.parse("2026-02-01T18:00:00Z")))
                .append("endsAt", Date.from(Instant.parse("2026-02-01T20:00:00Z")))
                .append("capacity", 40);
        insertEvent(legacy);

        MetricTotals totals = repository.totals(EVERY_CLUB, FROM, NOW);

        assertThat(totals).isEqualTo(new MetricTotals(1, 40, 0, 0, 0, 0, 0, 0));
        assertThat(repository.eventTotals(EVERY_CLUB, FROM, NOW))
                .extracting(EventTotals::enrolled, EventTotals::attended, EventTotals::unmetDemand)
                .containsExactly(tuple(0L, 0L, 0L));
    }

    // The three finished Published Events every hand-computed number above is taken from.
    private void seedPopulation() {
        insertEvent(published("club-a", "Robotics talk", "2026-03-10T20:00:00Z", 100, 80, 60, 10, 5, 6, 12));
        insertEvent(published("club-a", "Hack night", "2026-04-15T22:00:00Z", 50, 50, 40, 0, 9, 4, 10));
        insertEvent(published("club-b", "Choir", "2026-04-20T21:00:00Z", 30, 12, 9, 5, 0, 0, 0));
    }

    // One of each kind of Event the population leaves out, all inside the range.
    private void seedExclusions() {
        Document draft = published("club-a", "Unannounced", "2026-05-01T20:00:00Z", 60, 0, 0, 0, 0, 0, 0)
                .append("status", "DRAFT");
        Document cancelled = published("club-a", "Called off", "2026-05-02T20:00:00Z", 60, 40, 0, 0, 20, 0, 20)
                .append("status", "CANCELLED");
        // Started an hour ago, ends tonight: its attendance is mid-flight, and the door screen is where
        // that is watched.
        Document running = published("club-b", "Happening now", "2026-08-14T23:00:00Z", 80, 70, 30, 0, 0, 0, 0)
                .append("startsAt", Date.from(Instant.parse("2026-08-14T09:00:00Z")));
        insertEvent(draft);
        insertEvent(cancelled);
        insertEvent(running);
    }

    private static Document published(
            String clubId,
            String title,
            String endsAt,
            int capacity,
            int enrolled,
            int attended,
            int manual,
            int waitlist,
            int promotedCount,
            int everQueuedCount) {
        Instant ends = Instant.parse(endsAt);
        List<Document> attendance = new ArrayList<>();
        IntStream.range(0, attended)
                .forEach(index -> attendance.add(new Document("studentId", "student-" + index)
                        .append("at", Date.from(ends))
                        .append("method", index < manual ? "MANUAL" : "SCANNED")));
        return new Document("clubId", clubId)
                .append("title", title)
                .append("status", "PUBLISHED")
                .append("startsAt", Date.from(ends.minusSeconds(7200)))
                .append("endsAt", Date.from(ends))
                .append("capacity", capacity)
                .append("enrolled", studentEntries(enrolled))
                .append("waitlist", studentIds(waitlist))
                .append("attendance", attendance)
                .append("promotedCount", promotedCount)
                .append("everQueuedCount", everQueuedCount);
    }

    private static List<Document> studentEntries(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new Document("studentId", "student-" + index).append("via", "DIRECT"))
                .toList();
    }

    private static List<String> studentIds(int count) {
        return IntStream.range(0, count).mapToObj(index -> "queued-" + index).toList();
    }

    private void insertEvent(Document event) {
        mongoTemplate.getCollection("events").insertOne(event);
    }
}

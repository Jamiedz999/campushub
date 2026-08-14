package com.campushub.dashboard.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClients;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// The ADR's position is that computing every number on read is correct at this scale, and it made that
// position falsifiable: "a dashboard query exceeding roughly one second" is what would force
// pre-aggregation. This test is the measurement that claim is now held to, and the figure it prints is
// the baseline recorded in docs/adr/09-define-attendance-dashboard.md.
//
// It is a threshold assertion, not a benchmark. One second is an order of magnitude above what the
// pipelines take here, so a machine being busy cannot fail it — only the shape of the data or of the
// query changing by an order of magnitude can, which is exactly the event the ADR wants to hear about.
@Testcontainers
class DashboardPerformanceIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    private static final Instant FROM = Instant.parse("2025-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-14T10:15:00Z");

    /** The ADR's threshold: past this, profile and then choose between indexes, pre-aggregation and caching. */
    private static final Duration THRESHOLD = Duration.ofSeconds(1);

    private static final int EVENTS = 500;
    private static final int ATTENDANCE_PER_EVENT = 60;

    private static DashboardRepository repository;

    @BeforeAll
    static void seed() {
        MongoTemplate mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO_DB.getConnectionString()), "dashboard-perf-" + UUID.randomUUID());
        repository = new DashboardRepository(mongoTemplate);
        repository.ensureIndexes();

        List<Document> events = IntStream.range(0, EVENTS)
                .mapToObj(DashboardPerformanceIntegrationTest::event)
                .toList();
        mongoTemplate.getCollection("events").insertMany(events);
    }

    @Test
    void awholeDashboardReadStaysWellUnderTheOneSecondThresholdOnSeededData() {
        readEveryMetric(); // warm the connection and let the planner cache the winning plan.

        List<Long> runs = IntStream.range(0, 5)
                .mapToObj(run -> {
                    long start = System.nanoTime();
                    readEveryMetric();
                    return (System.nanoTime() - start) / 1_000_000;
                })
                .sorted()
                .toList();
        long median = runs.get(runs.size() / 2);

        System.out.printf(
                "dashboard read over %d Events and %d attendance entries: median %d ms across %s%n",
                EVENTS, EVENTS * ATTENDANCE_PER_EVENT, median, runs);
        assertThat(Duration.ofMillis(median)).isLessThan(THRESHOLD);
    }

    // All five pipelines, which is what one page load actually costs.
    private void readEveryMetric() {
        repository.totals(null, FROM, TO);
        repository.monthlyTotals(null, FROM, TO, DUBLIN);
        repository.clubTotals(null, FROM, TO);
        repository.eventTotals(null, FROM, TO);
        repository.excludedEvents(null, FROM, TO);
    }

    // Spread across twelve months and twenty Clubs, with one Event in ten Cancelled so the excluded
    // pipeline has something to count too.
    private static Document event(int index) {
        Instant ends = FROM.plus(Duration.ofHours(index * 16L));
        List<Document> attendance = new ArrayList<>();
        IntStream.range(0, ATTENDANCE_PER_EVENT)
                .forEach(seat -> attendance.add(new Document("studentId", "student-" + seat)
                        .append("at", Date.from(ends))
                        .append("method", seat % 8 == 0 ? "MANUAL" : "SCANNED")));
        return new Document("clubId", "club-" + (index % 20))
                .append("title", "Event " + index)
                .append("status", index % 10 == 0 ? "CANCELLED" : "PUBLISHED")
                .append("startsAt", Date.from(ends.minus(Duration.ofHours(2))))
                .append("endsAt", Date.from(ends))
                .append("capacity", 120)
                .append("enrolled", entries(80))
                .append("waitlist", IntStream.range(0, 15).mapToObj(seat -> "queued-" + seat).toList())
                .append("attendance", attendance)
                .append("promotedCount", 10)
                .append("everQueuedCount", 30);
    }

    private static List<Document> entries(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new Document("studentId", "student-" + index).append("via", "DIRECT"))
                .toList();
    }
}

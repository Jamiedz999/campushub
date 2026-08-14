package com.campushub.venue.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.venue.VenueModule;
import com.campushub.venue.VenueModule.SlotRequestOutcome;
import com.campushub.venue.persistence.VenueRepository;
import com.mongodb.client.MongoClients;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class VenueModuleImplIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private VenueModule venueModule;
    private VenueRepository repository;

    @BeforeEach
    void setUp() {
        MongoTemplate mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO_DB.getConnectionString()), "venue-module-test-" + UUID.randomUUID());
        repository = new VenueRepository(mongoTemplate);
        repository.ensureIndexes();
        venueModule = new VenueModuleImpl(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Europe/Dublin"));
    }

    @Test
    @Tag("concurrency")
    void parallelOverlappingSlotRequestsHaveExactlyOneWinner() throws Exception {
        String venueId = venueModule.createVenue("Sports Hall 2");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<SlotRequestOutcome> first = executor.submit(() -> requestAfterSignal(
                    ready,
                    start,
                    venueId,
                    "event-a",
                    Instant.parse("2026-03-20T10:00:00Z"),
                    Instant.parse("2026-03-20T12:00:00Z")));
            Future<SlotRequestOutcome> second = executor.submit(() -> requestAfterSignal(
                    ready,
                    start,
                    venueId,
                    "event-b",
                    Instant.parse("2026-03-20T11:00:00Z"),
                    Instant.parse("2026-03-20T13:00:00Z")));

            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(SlotRequestOutcome.ACQUIRED, SlotRequestOutcome.SLOT_TAKEN);
        }
    }

    @Test
    @Tag("concurrency")
    void parallelIdenticalRequestsForOneEventCreateOnlyOneBooking() throws Exception {
        String venueId = venueModule.createVenue("Double Click Hall");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Instant startsAt = Instant.parse("2026-03-20T10:00:00Z");
        Instant endsAt = Instant.parse("2026-03-20T11:00:00Z");

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<SlotRequestOutcome> first = executor.submit(
                    () -> requestAfterSignal(ready, start, venueId, "event-a", startsAt, endsAt));
            Future<SlotRequestOutcome> second = executor.submit(
                    () -> requestAfterSignal(ready, start, venueId, "event-a", startsAt, endsAt));

            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(SlotRequestOutcome.ACQUIRED, SlotRequestOutcome.SLOT_TAKEN);
        }
        assertThat(venueModule.findDay(venueId, LocalDate.parse("2026-03-20")).orElseThrow().bookings())
                .hasSize(1);
    }

    @Test
    void backToBackSlotsAtASharedBoundaryBothSucceed() {
        String venueId = venueModule.createVenue("Seminar Room");

        SlotRequestOutcome first = venueModule
                .requestSlot(
                        venueId,
                        "event-a",
                        Instant.parse("2026-03-20T10:00:00Z"),
                        Instant.parse("2026-03-20T11:00:00Z"))
                .outcome();
        SlotRequestOutcome second = venueModule
                .requestSlot(
                        venueId,
                        "event-b",
                        Instant.parse("2026-03-20T11:00:00Z"),
                        Instant.parse("2026-03-20T12:00:00Z"))
                .outcome();

        assertThat(List.of(first, second)).containsOnly(SlotRequestOutcome.ACQUIRED);
    }

    @Test
    void venueManagementListsInNameOrderAndNormalizesPaging() {
        String later = venueModule.createVenue("Zulu Hall");
        String earlier = venueModule.createVenue("Alpha Hall");

        VenueModule.VenuePage page = venueModule.listVenues(-3, 500);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.items())
                .extracting(VenueModule.VenueSummary::id)
                .containsExactly(earlier, later);
        assertThat(venueModule.renameVenue(earlier, "Alpha Hall")).isTrue();
        assertThat(venueModule.renameVenue("missing", "Nowhere")).isFalse();
        assertThat(venueModule.findVenue(earlier)).get().extracting(VenueModule.VenueSummary::name)
                .isEqualTo("Alpha Hall");
        assertThat(venueModule.findVenue("missing")).isEmpty();
    }

    @Test
    void bookingRefusesAnUnknownVenueAndARequestAtNow() {
        assertThat(venueModule
                        .requestSlot(
                                "missing",
                                "event-a",
                                Instant.parse("2026-03-20T10:00:00Z"),
                                Instant.parse("2026-03-20T11:00:00Z"))
                        .outcome())
                .isEqualTo(SlotRequestOutcome.NOT_FOUND);
        String venueId = venueModule.createVenue("Frozen Hall");
        assertThat(venueModule
                        .requestSlot(venueId, "event-a", NOW, NOW.plusSeconds(3_600))
                        .outcome())
                .isEqualTo(SlotRequestOutcome.SLOT_ALREADY_STARTED);
    }

    @Test
    void bookingRefusesAnEndThatIsNotAfterTheStart() {
        String venueId = venueModule.createVenue("Reverse Hall");

        assertThat(venueModule
                        .requestSlot(
                                venueId,
                                "event-a",
                                Instant.parse("2026-03-20T11:00:00Z"),
                                Instant.parse("2026-03-20T10:00:00Z"))
                        .outcome())
                .isEqualTo(SlotRequestOutcome.SLOT_CROSSES_MIDNIGHT);
    }

    @Test
    void aSlotCrossingCampusMidnightIsRefused() {
        String venueId = venueModule.createVenue("Late Lab");

        SlotRequestOutcome outcome = venueModule
                .requestSlot(
                        venueId,
                        "event-a",
                        Instant.parse("2026-03-20T23:30:00Z"),
                        Instant.parse("2026-03-21T00:30:00Z"))
                .outcome();

        assertThat(outcome).isEqualTo(SlotRequestOutcome.SLOT_CROSSES_MIDNIGHT);
    }

    @Test
    void springTransitionRefusesTheMissingHourAndAcceptsAnotherSlotThatDay() {
        venueModule = moduleAt("2026-03-29T00:00:00Z");
        String venueId = venueModule.createVenue("Spring Hall");

        SlotRequestOutcome throughMissingHour = venueModule
                .requestSlot(
                        venueId,
                        "event-a",
                        Instant.parse("2026-03-29T00:30:00Z"),
                        Instant.parse("2026-03-29T01:30:00Z"))
                .outcome();
        SlotRequestOutcome afterTransition = venueModule
                .requestSlot(
                        venueId,
                        "event-b",
                        Instant.parse("2026-03-29T01:30:00Z"),
                        Instant.parse("2026-03-29T02:30:00Z"))
                .outcome();

        assertThat(throughMissingHour).isEqualTo(SlotRequestOutcome.SLOT_IN_DST_TRANSITION);
        assertThat(afterTransition).isEqualTo(SlotRequestOutcome.ACQUIRED);
    }

    @Test
    void autumnTransitionRefusesBothCopiesOfTheRepeatedHourAndAcceptsAnotherSlotThatDay() {
        venueModule = moduleAt("2026-10-25T00:00:00Z");
        String venueId = venueModule.createVenue("Autumn Hall");

        SlotRequestOutcome firstCopy = venueModule
                .requestSlot(
                        venueId,
                        "event-a",
                        Instant.parse("2026-10-25T00:15:00Z"),
                        Instant.parse("2026-10-25T00:45:00Z"))
                .outcome();
        SlotRequestOutcome secondCopy = venueModule
                .requestSlot(
                        venueId,
                        "event-b",
                        Instant.parse("2026-10-25T01:15:00Z"),
                        Instant.parse("2026-10-25T01:45:00Z"))
                .outcome();
        SlotRequestOutcome afterTransition = venueModule
                .requestSlot(
                        venueId,
                        "event-c",
                        Instant.parse("2026-10-25T02:00:00Z"),
                        Instant.parse("2026-10-25T03:00:00Z"))
                .outcome();

        assertThat(firstCopy).isEqualTo(SlotRequestOutcome.SLOT_IN_DST_TRANSITION);
        assertThat(secondCopy).isEqualTo(SlotRequestOutcome.SLOT_IN_DST_TRANSITION);
        assertThat(afterTransition).isEqualTo(SlotRequestOutcome.ACQUIRED);
    }

    @Test
    void theVenueDayViewIsOrderedAndReleaseIsIdempotent() {
        String venueId = venueModule.createVenue("Timeline Hall");
        venueModule.requestSlot(
                venueId,
                "event-late",
                Instant.parse("2026-03-20T14:00:00Z"),
                Instant.parse("2026-03-20T15:00:00Z"));
        venueModule.requestSlot(
                venueId,
                "event-early",
                Instant.parse("2026-03-20T10:00:00Z"),
                Instant.parse("2026-03-20T11:00:00Z"));

        assertThat(venueModule.findDay(venueId, LocalDate.parse("2026-03-20")).orElseThrow().bookings())
                .extracting(VenueModule.DayBooking::eventId)
                .containsExactly("event-early", "event-late");

        venueModule.releaseSlot(
                venueId,
                "event-early",
                Instant.parse("2026-03-20T10:00:00Z"),
                Instant.parse("2026-03-20T11:00:00Z"));
        venueModule.releaseSlot(
                venueId,
                "event-early",
                Instant.parse("2026-03-20T10:00:00Z"),
                Instant.parse("2026-03-20T11:00:00Z"));

        assertThat(venueModule.findDay(venueId, LocalDate.parse("2026-03-20")).orElseThrow().bookings())
                .extracting(VenueModule.DayBooking::eventId)
                .containsExactly("event-late");
    }

    private VenueModule moduleAt(String instant) {
        return new VenueModuleImpl(
                repository,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                ZoneId.of("Europe/Dublin"));
    }

    private SlotRequestOutcome requestAfterSignal(
            CountDownLatch ready,
            CountDownLatch start,
            String venueId,
            String eventId,
            Instant startsAt,
            Instant endsAt)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return venueModule.requestSlot(venueId, eventId, startsAt, endsAt).outcome();
    }
}

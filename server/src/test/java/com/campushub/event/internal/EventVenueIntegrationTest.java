package com.campushub.event.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.SlotCommandOutcome;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventCommandResult;
import com.campushub.event.domain.EventStatus;
import com.campushub.event.persistence.EventRepository;
import com.campushub.venue.VenueModule;
import com.campushub.venue.VenueModule.Slot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@SpringBootTest
@Testcontainers
class EventVenueIntegrationTest {

    private static final Instant OLD_START = Instant.parse("2099-03-20T10:00:00Z");
    private static final Instant OLD_END = Instant.parse("2099-03-20T11:00:00Z");

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.mongodb.uri",
                () -> MONGO_DB.getConnectionString() + "/event-venue-test-" + UUID.randomUUID());
        registry.add("campushub.security.session-secret", () -> "event-venue-session-secret");
        registry.add("campushub.checkin.hmac-secret", () -> "event-venue-hmac-secret");
    }

    @Autowired
    private EventModule eventModule;

    @Autowired
    private VenueModule venueModule;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private Clock clock;

    @Test
    void aFailedAcquireLeavesTheEventsOriginalVenueAndTimestampsUntouched() {
        String clubId = "club-failed-acquire";
        String oldVenueId = venueModule.createVenue("Old Hall");
        String busyVenueId = venueModule.createVenue("Busy Hall");
        String eventId = createDraft(clubId, "Moving Event", OLD_START, OLD_END);
        String blockerId = createDraft(
                clubId,
                "Blocking Event",
                Instant.parse("2099-03-20T12:00:00Z"),
                Instant.parse("2099-03-20T14:00:00Z"));

        assertThat(eventModule.bookSlotAsOfficer(
                        eventId, Set.of(clubId), oldVenueId, OLD_START, OLD_END))
                .isEqualTo(SlotCommandOutcome.SUCCESS);
        assertThat(eventModule.bookSlotAsOfficer(
                        blockerId,
                        Set.of(clubId),
                        busyVenueId,
                        Instant.parse("2099-03-20T12:00:00Z"),
                        Instant.parse("2099-03-20T14:00:00Z")))
                .isEqualTo(SlotCommandOutcome.SUCCESS);

        SlotCommandOutcome outcome = eventModule.bookSlotAsOfficer(
                eventId,
                Set.of(clubId),
                busyVenueId,
                Instant.parse("2099-03-20T13:00:00Z"),
                Instant.parse("2099-03-20T15:00:00Z"));

        Event unchanged = eventModule.findForOfficer(eventId, Set.of(clubId)).orElseThrow();
        assertThat(outcome).isEqualTo(SlotCommandOutcome.SLOT_TAKEN);
        assertThat(unchanged.getVenueId()).isEqualTo(oldVenueId);
        assertThat(unchanged.getStartsAt()).isEqualTo(OLD_START);
        assertThat(unchanged.getEndsAt()).isEqualTo(OLD_END);
    }

    @Test
    void aFailedOldReleaseLeavesBothTheNewAndOldSlotsHeld() {
        String clubId = "club-failed-release";
        String oldVenueId = venueModule.createVenue("Old Release Hall");
        String newVenueId = venueModule.createVenue("New Release Hall");
        String eventId = createDraft(clubId, "Safe Move Event", OLD_START, OLD_END);
        eventModule.bookSlotAsOfficer(eventId, Set.of(clubId), oldVenueId, OLD_START, OLD_END);
        Instant newStart = Instant.parse("2099-03-21T12:00:00Z");
        Instant newEnd = Instant.parse("2099-03-21T13:00:00Z");
        VenueModule faultingVenueModule = spy(venueModule);
        doThrow(new IllegalStateException("injected old release failure"))
                .when(faultingVenueModule)
                .releaseReservation(eventId, new Slot(oldVenueId, OLD_START, OLD_END));
        EventModule faultingEventModule =
                new EventModuleImpl(eventRepository, clock, faultingVenueModule);

        assertThatThrownBy(() -> faultingEventModule.bookSlotAsOfficer(
                        eventId, Set.of(clubId), newVenueId, newStart, newEnd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected old release failure");

        Event moved = eventModule.findForOfficer(eventId, Set.of(clubId)).orElseThrow();
        assertThat(moved.getVenueId()).isEqualTo(newVenueId);
        assertThat(moved.getStartsAt()).isEqualTo(newStart);
        assertThat(venueModule.findDay(oldVenueId, LocalDate.parse("2099-03-20")).orElseThrow().bookings())
                .extracting(VenueModule.DayBooking::eventId)
                .containsExactly(eventId);
        assertThat(venueModule.findDay(newVenueId, LocalDate.parse("2099-03-21")).orElseThrow().bookings())
                .extracting(VenueModule.DayBooking::eventId)
                .containsExactly(eventId);
    }

    @Test
    void aCollisionWithACancelledEventsOrphanSlotSelfHealsAndRetriesOnce() {
        String clubId = "club-orphan-collision";
        String venueId = venueModule.createVenue("Healing Hall");
        String cancelledEventId = createDraft(clubId, "Cancelled Event", OLD_START, OLD_END);
        String newEventId = createDraft(clubId, "New Event", OLD_START, OLD_END);
        eventModule.publish(cancelledEventId, Set.of(clubId));
        eventModule.bookSlotAsOfficer(cancelledEventId, Set.of(clubId), venueId, OLD_START, OLD_END);
        eventRepository.cancelAsOfficer(
                cancelledEventId, Set.of(clubId), Instant.parse("2099-03-01T00:00:00Z"));

        SlotCommandOutcome outcome =
                eventModule.bookSlotAsOfficer(newEventId, Set.of(clubId), venueId, OLD_START, OLD_END);

        assertThat(outcome).isEqualTo(SlotCommandOutcome.SUCCESS);
        assertThat(eventModule.findVenueDay(venueId, LocalDate.parse("2099-03-20")).orElseThrow().bookings())
                .extracting(VenueModule.DayBooking::eventId)
                .containsExactly(newEventId);
    }

    @Test
    void renderingAVenueDayRemovesCancelledEventOrphans() {
        String clubId = "club-orphan-view";
        String venueId = venueModule.createVenue("View Hall");
        String eventId = createDraft(clubId, "Cancelled Event", OLD_START, OLD_END);
        eventModule.publish(eventId, Set.of(clubId));
        eventModule.bookSlotAsOfficer(eventId, Set.of(clubId), venueId, OLD_START, OLD_END);
        eventRepository.cancelAsOfficer(eventId, Set.of(clubId), Instant.parse("2099-03-01T00:00:00Z"));

        VenueModule.VenueDayView day =
                eventModule.findVenueDay(venueId, LocalDate.parse("2099-03-20")).orElseThrow();

        assertThat(day.bookings()).isEmpty();
    }

    @Test
    void cancellingAnEventChangesItsStatusBeforeReleasingEveryHeldSlot() {
        String clubId = "club-cancellation";
        String venueId = venueModule.createVenue("Cancellation Hall");
        String eventId = createDraft(clubId, "Published Event", OLD_START, OLD_END);
        eventModule.publish(eventId, Set.of(clubId));
        eventModule.bookSlotAsOfficer(eventId, Set.of(clubId), venueId, OLD_START, OLD_END);

        EventCommandResult outcome = eventModule.cancelAsOfficer(eventId, Set.of(clubId));

        assertThat(outcome).isEqualTo(EventCommandResult.SUCCESS);
        assertThat(eventModule.findForOfficer(eventId, Set.of(clubId)).orElseThrow().getStatus())
                .isEqualTo(EventStatus.CANCELLED);
        assertThat(venueModule.findDay(venueId, LocalDate.parse("2099-03-20")).orElseThrow().bookings())
                .isEmpty();
    }

    private String createDraft(String clubId, String title, Instant startsAt, Instant endsAt) {
        return eventModule.createDraft(
                clubId,
                title,
                "Description",
                Instant.parse("2099-03-01T00:00:00Z"),
                Instant.parse("2099-03-10T00:00:00Z"),
                startsAt,
                endsAt,
                20);
    }
}

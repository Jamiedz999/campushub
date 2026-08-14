package com.campushub.venue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Owns Venue and Venue-Day documents, including the atomic Slot overlap guard. */
public interface VenueModule {

    enum SlotRequestOutcome {
        ACQUIRED,
        NOT_FOUND,
        SLOT_TAKEN,
        SLOT_CROSSES_MIDNIGHT,
        SLOT_IN_DST_TRANSITION,
        SLOT_ALREADY_STARTED
    }

    record SlotRequestResult(SlotRequestOutcome outcome, List<String> conflictingEventIds) {

        public SlotRequestResult {
            conflictingEventIds = List.copyOf(conflictingEventIds);
        }
    }

    record VenueSummary(String id, String name) {}

    record VenuePage(List<VenueSummary> items, int page, int size, long total) {

        public VenuePage {
            items = List.copyOf(items);
        }
    }

    record DayBooking(String eventId, int startMinute, int endMinute) {}

    record VenueDayView(VenueSummary venue, LocalDate date, List<DayBooking> bookings) {

        public VenueDayView {
            bookings = List.copyOf(bookings);
        }
    }

    String createVenue(String name);

    VenuePage listVenues(int page, int size);

    Optional<VenueSummary> findVenue(String venueId);

    boolean renameVenue(String venueId, String name);

    SlotRequestResult requestSlot(String venueId, String eventId, Instant startsAt, Instant endsAt);

    Optional<VenueDayView> findDay(String venueId, LocalDate date);

    void releaseSlot(String venueId, String eventId, Instant startsAt, Instant endsAt);

    /** Removes the named orphan bookings from one Venue-Day. Safe to repeat. */
    void removeBookings(String venueId, Instant startsAt, Set<String> eventIds);

    /** Removes the named orphan bookings when the Venue-Day key is already known. */
    void removeBookings(String venueId, LocalDate date, Set<String> eventIds);

    /** Removes every Slot held by an Event, including leftovers from a failed reschedule release. */
    void releaseEventSlots(String eventId);
}

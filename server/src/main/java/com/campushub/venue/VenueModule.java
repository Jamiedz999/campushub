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

    /** One Event's requested Venue and half-open time interval. */
    record Slot(String venueId, Instant startsAt, Instant endsAt) {

        public Slot {
            if (!isMinuteAligned(startsAt) || !isMinuteAligned(endsAt)) {
                throw new IllegalArgumentException("Slot times must be aligned to whole minutes.");
            }
        }

        private static boolean isMinuteAligned(Instant value) {
            return value != null && value.getEpochSecond() % 60 == 0 && value.getNano() == 0;
        }
    }

    String createVenue(String name);

    VenuePage listVenues(int page, int size);

    Optional<VenueSummary> findVenue(String venueId);

    boolean renameVenue(String venueId, String name);

    SlotRequestResult requestSlot(String eventId, Slot slot);

    Optional<VenueDayView> findDay(String venueId, LocalDate date);

    /**
     * Removes one exact reservation during rollback or rescheduling. User-requested release and
     * cancellation use {@link #releaseEventSlots(String)} instead.
     */
    void releaseReservation(String eventId, Slot slot);

    /** Removes the named orphan bookings from one Venue-Day. Safe to repeat. */
    void removeBookings(String venueId, Instant startsAt, Set<String> eventIds);

    /** Removes the named orphan bookings when the Venue-Day key is already known. */
    void removeBookings(String venueId, LocalDate date, Set<String> eventIds);

    /** Removes every Slot held by an Event, including leftovers from a failed reschedule release. */
    void releaseEventSlots(String eventId);
}

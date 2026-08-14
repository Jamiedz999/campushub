package com.campushub.venue.internal;

import com.campushub.venue.VenueModule;
import com.campushub.venue.domain.VenueDay.Booking;
import com.campushub.venue.persistence.VenueRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class VenueModuleImpl implements VenueModule {

    private static final int MAX_PAGE_SIZE = 100;

    private final VenueRepository repository;
    private final Clock clock;
    private final ZoneId campusZone;

    VenueModuleImpl(VenueRepository repository, Clock clock, ZoneId campusZone) {
        this.repository = repository;
        this.clock = clock;
        this.campusZone = campusZone;
    }

    @Override
    public String createVenue(String name) {
        return repository.insertVenue(name);
    }

    @Override
    public VenuePage listVenues(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<VenueSummary> items = repository.listVenues(normalizedPage, normalizedSize).stream()
                .map(venue -> new VenueSummary(venue.getId(), venue.getName()))
                .toList();
        return new VenuePage(items, normalizedPage, normalizedSize, repository.countVenues());
    }

    @Override
    public Optional<VenueSummary> findVenue(String venueId) {
        return repository.findVenue(venueId).map(venue -> new VenueSummary(venue.getId(), venue.getName()));
    }

    @Override
    public boolean renameVenue(String venueId, String name) {
        return repository.renameVenue(venueId, name);
    }

    @Override
    public SlotRequestResult requestSlot(String venueId, String eventId, Instant startsAt, Instant endsAt) {
        if (!repository.venueExists(venueId)) {
            return result(SlotRequestOutcome.NOT_FOUND);
        }
        if (!startsAt.isAfter(clock.instant())) {
            return result(SlotRequestOutcome.SLOT_ALREADY_STARTED);
        }

        ZonedDateTime localStart = startsAt.atZone(campusZone);
        ZonedDateTime localEnd = endsAt.atZone(campusZone);
        if (!endsAt.isAfter(startsAt) || !localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            return result(SlotRequestOutcome.SLOT_CROSSES_MIDNIGHT);
        }
        if (intersectsDstTransition(startsAt, endsAt, localStart.toLocalDate())) {
            return result(SlotRequestOutcome.SLOT_IN_DST_TRANSITION);
        }

        int startMinute = minuteOfDay(localStart);
        int endMinute = minuteOfDay(localEnd);
        boolean acquired = repository.acquire(
                venueId, localStart.toLocalDate(), new Booking(eventId, startMinute, endMinute));
        if (acquired) {
            return result(SlotRequestOutcome.ACQUIRED);
        }
        List<String> conflicts = repository.conflictingEventIds(
                venueId, localStart.toLocalDate(), startMinute, endMinute);
        return new SlotRequestResult(SlotRequestOutcome.SLOT_TAKEN, conflicts);
    }

    @Override
    public Optional<VenueDayView> findDay(String venueId, LocalDate date) {
        return repository.findVenue(venueId).map(venue -> {
            List<DayBooking> bookings = repository.findDay(venueId, date).stream()
                    .flatMap(day -> day.getBookings().stream())
                    .sorted(java.util.Comparator.comparingInt(Booking::startMinute))
                    .map(booking ->
                            new DayBooking(booking.eventId(), booking.startMinute(), booking.endMinute()))
                    .toList();
            return new VenueDayView(new VenueSummary(venue.getId(), venue.getName()), date, bookings);
        });
    }

    @Override
    public void releaseSlot(String venueId, String eventId, Instant startsAt, Instant endsAt) {
        ZonedDateTime localStart = startsAt.atZone(campusZone);
        ZonedDateTime localEnd = endsAt.atZone(campusZone);
        repository.release(
                venueId,
                localStart.toLocalDate(),
                new Booking(eventId, minuteOfDay(localStart), minuteOfDay(localEnd)));
    }

    @Override
    public void removeBookings(String venueId, Instant startsAt, Set<String> eventIds) {
        repository.removeBookings(venueId, startsAt.atZone(campusZone).toLocalDate(), eventIds);
    }

    @Override
    public void removeBookings(String venueId, LocalDate date, Set<String> eventIds) {
        repository.removeBookings(venueId, date, eventIds);
    }

    @Override
    public void releaseEventSlots(String eventId) {
        repository.releaseEventSlots(eventId);
    }

    private boolean intersectsDstTransition(Instant startsAt, Instant endsAt, LocalDate date) {
        Instant dayStart = date.atStartOfDay(campusZone).toInstant();
        Instant nextDayStart = date.plusDays(1).atStartOfDay(campusZone).toInstant();
        ZoneRules rules = campusZone.getRules();
        ZoneOffsetTransition transition = rules.nextTransition(dayStart.minusNanos(1));
        if (transition == null || !transition.getInstant().isBefore(nextDayStart)) {
            return false;
        }

        Instant transitionInstant = transition.getInstant();
        Duration offsetChange = transition.getDuration();
        if (transition.isGap()) {
            // At the spring jump the forbidden local hour has no instants. A Slot projects through
            // that missing hour exactly when it starts before the jump and ends at or after it.
            return startsAt.isBefore(transitionInstant) && !endsAt.isBefore(transitionInstant);
        }

        Duration repeatedWidth = offsetChange.abs();
        Instant repeatedStart = transitionInstant.minus(repeatedWidth);
        Instant repeatedEnd = transitionInstant.plus(repeatedWidth);
        return startsAt.isBefore(repeatedEnd) && endsAt.isAfter(repeatedStart);
    }

    private static int minuteOfDay(ZonedDateTime value) {
        return value.getHour() * 60 + value.getMinute();
    }

    private static SlotRequestResult result(SlotRequestOutcome outcome) {
        return new SlotRequestResult(outcome, List.of());
    }
}

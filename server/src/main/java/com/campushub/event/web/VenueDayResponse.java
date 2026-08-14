package com.campushub.event.web;

import com.campushub.venue.VenueModule.VenueDayView;
import java.time.LocalDate;
import java.util.List;

record VenueDayResponse(VenueSummary venue, LocalDate date, List<Booking> bookings) {

    VenueDayResponse {
        bookings = List.copyOf(bookings);
    }

    static VenueDayResponse from(VenueDayView day) {
        return new VenueDayResponse(
                new VenueSummary(day.venue().id(), day.venue().name()),
                day.date(),
                day.bookings().stream()
                        .map(booking -> new Booking(
                                booking.eventId(), booking.startMinute(), booking.endMinute()))
                        .toList());
    }

    record VenueSummary(String id, String name) {}

    record Booking(String eventId, int startMinute, int endMinute) {}
}

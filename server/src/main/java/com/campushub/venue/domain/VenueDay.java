package com.campushub.venue.domain;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("venueDays")
public class VenueDay {

    @Id
    private String id;

    private String venueId;

    private String date;

    private List<Booking> bookings;

    public VenueDay() {}

    public String getVenueId() {
        return venueId;
    }

    public String getDate() {
        return date;
    }

    public List<Booking> getBookings() {
        return bookings == null ? List.of() : List.copyOf(bookings);
    }

    public record Booking(String eventId, int startMinute, int endMinute) {}
}

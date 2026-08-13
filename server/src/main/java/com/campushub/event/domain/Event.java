package com.campushub.event.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// The whole Event document: the three stored Status values, the four timestamps that the Registration
// Window and the lifecycle boundaries read against, capacity, and the Seat Ledger (enrolled, waitlist).
// Phase is deliberately not a field here — see Phase.of() — because a stored derived value can
// contradict the timestamps beside it. Every field below is set only through a guarded MongoTemplate
// write in event.persistence; this class carries no mutating methods.
@Document("events")
public class Event {

    @Id
    private String id;

    private String clubId;

    private String title;

    private String description;

    private EventStatus status;

    private Instant registrationOpensAt;

    private Instant registrationClosesAt;

    private Instant startsAt;

    private Instant endsAt;

    private int capacity;

    private List<EnrolledEntry> enrolled;

    private List<String> waitlist;

    public Event() {}

    public Event(
            String clubId,
            String title,
            String description,
            Instant registrationOpensAt,
            Instant registrationClosesAt,
            Instant startsAt,
            Instant endsAt,
            int capacity) {
        this.clubId = clubId;
        this.title = title;
        this.description = description;
        this.status = EventStatus.DRAFT;
        this.registrationOpensAt = registrationOpensAt;
        this.registrationClosesAt = registrationClosesAt;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
        this.enrolled = new ArrayList<>();
        this.waitlist = new ArrayList<>();
    }

    // Package-private: lets domain tests build an Event in any stored state directly, without a Mongo
    // round trip, for Phase derivation and other pure-function tests. Never used outside this module.
    Event(
            String id,
            String clubId,
            String title,
            String description,
            EventStatus status,
            Instant registrationOpensAt,
            Instant registrationClosesAt,
            Instant startsAt,
            Instant endsAt,
            int capacity,
            List<EnrolledEntry> enrolled,
            List<String> waitlist) {
        this.id = id;
        this.clubId = clubId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.registrationOpensAt = registrationOpensAt;
        this.registrationClosesAt = registrationClosesAt;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
        this.enrolled = new ArrayList<>(enrolled);
        this.waitlist = new ArrayList<>(waitlist);
    }

    public String getId() {
        return id;
    }

    public String getClubId() {
        return clubId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getRegistrationOpensAt() {
        return registrationOpensAt;
    }

    public Instant getRegistrationClosesAt() {
        return registrationClosesAt;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public List<EnrolledEntry> getEnrolled() {
        return List.copyOf(enrolled);
    }

    public List<String> getWaitlist() {
        return List.copyOf(waitlist);
    }
}

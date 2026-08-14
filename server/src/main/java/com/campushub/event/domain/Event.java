package com.campushub.event.domain;

import com.campushub.event.EventModule.RegistrationForm;
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

    private String venueId;

    private int capacity;

    private List<EnrolledEntry> enrolled;

    private List<String> waitlist;

    private int promotedCount;

    private int everQueuedCount;

    private long lastEnrollmentVersion;

    private RegistrationForm registrationForm;

    private int registrationFormRevision;

    private boolean registrationFormLocked;

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
        this.venueId = null;
        this.capacity = capacity;
        this.enrolled = new ArrayList<>();
        this.waitlist = new ArrayList<>();
        this.promotedCount = 0;
        this.everQueuedCount = 0;
        this.lastEnrollmentVersion = 0;
        this.registrationForm = RegistrationForm.empty();
        this.registrationFormRevision = 0;
        this.registrationFormLocked = false;
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
        this.registrationForm = RegistrationForm.empty();
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

    public String getVenueId() {
        return venueId;
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

    public int getEverQueuedCount() {
        return everQueuedCount;
    }

    public int getPromotedCount() {
        return promotedCount;
    }

    public long getLastEnrollmentVersion() {
        return lastEnrollmentVersion;
    }

    public RegistrationForm getRegistrationForm() {
        return registrationForm == null ? RegistrationForm.empty() : registrationForm;
    }

    public int getRegistrationFormRevision() {
        return registrationFormRevision;
    }

    public boolean isRegistrationFormLocked() {
        return registrationFormLocked || !enrolled.isEmpty();
    }

    /** Waitlist joins are the fixed denominator; leaving the Waitlist never makes demand disappear. */
    public double waitlistConversion() {
        return everQueuedCount == 0 ? 0.0 : (double) promotedCount / everQueuedCount;
    }
}

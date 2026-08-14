package com.campushub.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventBrowseQuery;
import com.campushub.event.domain.EventCommandResult;
import com.campushub.event.domain.EventEdit;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.RegistrationOutcome;
import com.campushub.venue.VenueModule.VenueDayView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// event owns the whole Event document — Status, the four timestamps, capacity and the Seat Ledger. See
// docs/adr/03-define-event-lifecycle.md and docs/planning/implementation/TECHNICAL-BASELINE.md.
//
// Every officer-facing method below takes the caller's officer Club grants as a parameter and scopes
// its query or guarded write with them — never load-then-check. See
// docs/adr/08-define-roles-and-resource-authorization.md. Callers (event.web) resolve those grants from
// identityaccess.CurrentActor first and are responsible for the create/edit/publish check that a create
// command has no existing resource to scope a query by (isOfficerOf the target Club).
public interface EventModule {

    /** Ordered, per-Event form definition stored inside the Event document. */
    record RegistrationForm(List<FormField> fields) {

        public RegistrationForm {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }

        public static RegistrationForm empty() {
            return new RegistrationForm(List.of());
        }
    }

    /**
     * The discriminator is part of the JSON contract. Jackson also uses it to rebuild the concrete
     * field shape submitted by the Officer form builder.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ShortTextField.class, name = "SHORT_TEXT"),
        @JsonSubTypes.Type(value = LongTextField.class, name = "LONG_TEXT"),
        @JsonSubTypes.Type(value = SingleChoiceField.class, name = "SINGLE_CHOICE"),
        @JsonSubTypes.Type(value = MultipleChoiceField.class, name = "MULTIPLE_CHOICE"),
        @JsonSubTypes.Type(value = NumberField.class, name = "NUMBER")
    })
    sealed interface FormField
            permits ShortTextField, LongTextField, SingleChoiceField, MultipleChoiceField, NumberField {

        String fieldId();

        String label();

        String helpText();

        boolean required();
    }

    record ShortTextField(String fieldId, String label, String helpText, boolean required, int maxLength)
            implements FormField {}

    record LongTextField(String fieldId, String label, String helpText, boolean required, int maxLength)
            implements FormField {}

    record SingleChoiceField(
            String fieldId, String label, String helpText, boolean required, List<String> options)
            implements FormField {

        public SingleChoiceField {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    record MultipleChoiceField(
            String fieldId, String label, String helpText, boolean required, List<String> options)
            implements FormField {

        public MultipleChoiceField {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    record NumberField(
            String fieldId,
            String label,
            String helpText,
            boolean required,
            BigDecimal minimum,
            BigDecimal maximum)
            implements FormField {}

    /** Stable outcomes of deleting the signed-in Student's registration sub-resource. */
    enum WithdrawalOutcome {
        SUCCESS,
        NOT_FOUND,
        EVENT_CANCELLED,
        EVENT_STARTED
    }

    /** Stable outcomes of replacing an Event's form definition. */
    enum FormUpdateOutcome {
        SUCCESS,
        NOT_FOUND,
        NOT_EDITABLE,
        FORM_LOCKED
    }

    /** Stable outcomes of acquiring or moving an Event's Venue Slot. */
    enum SlotCommandOutcome {
        SUCCESS,
        NOT_FOUND,
        NOT_EDITABLE,
        SLOT_TAKEN,
        SLOT_CROSSES_MIDNIGHT,
        SLOT_IN_DST_TRANSITION,
        SLOT_ALREADY_STARTED
    }

    /** Stable result returned to the Registration module's orchestration path. */
    enum SeatRequestOutcome {
        SUCCESS,
        NOT_FOUND,
        EVENT_CANCELLED,
        EVENT_STARTED,
        REGISTRATION_NOT_OPEN,
        REGISTRATION_CLOSED,
        ALREADY_ENROLLED,
        ALREADY_WAITLISTED,
        EVENT_FULL,
        FORM_CHANGED
    }

    /** Seat outcome plus the version assigned by the successful atomic Seat write, when there was one. */
    record SeatRequestResult(SeatRequestOutcome outcome, Long enrollmentVersion) {}

    /** Student-safe Event projection used by the Registration module; it contains no other Student ids. */
    record StudentRegistrationEvent(
            String id,
            String clubId,
            String title,
            String description,
            String phase,
            Instant registrationOpensAt,
            Instant registrationClosesAt,
            Instant startsAt,
            Instant endsAt,
            int capacity,
            int enrolledCount,
            int waitlistCount,
            boolean enrolled,
            String enrollmentVia,
            Long enrollmentVersion,
            Integer waitlistPosition,
            RegistrationForm registrationForm,
            int registrationFormRevision) {}

    record StudentRegistrationEventPage(
            List<StudentRegistrationEvent> items, int page, int size, long total) {

        public StudentRegistrationEventPage {
            items = List.copyOf(items);
        }
    }

    /** One enrolled Student, exposed only from the Officer-scoped query below. */
    record OfficerEnrollment(
            String studentId, String enrollmentVia, Instant enrolledAt, Long enrollmentVersion) {

        public OfficerEnrollment(String studentId, String enrollmentVia, Instant enrolledAt) {
            this(studentId, enrollmentVia, enrolledAt, null);
        }
    }

    /** Officer-only Event projection used to shape answer tables and CSV without exposing the Event document. */
    record OfficerRegistrationEvent(
            String id,
            String title,
            RegistrationForm registrationForm,
            List<OfficerEnrollment> enrolled) {

        public OfficerRegistrationEvent {
            enrolled = List.copyOf(enrolled);
        }
    }

    /** Creates a Draft in {@code clubId} and returns its id. The caller must already be that Club's Officer. */
    String createDraft(
            String clubId,
            String title,
            String description,
            Instant registrationOpensAt,
            Instant registrationClosesAt,
            Instant startsAt,
            Instant endsAt,
            int capacity);

    /** The Event, scoped to the caller's officer Clubs, whatever its Status. */
    Optional<Event> findForOfficer(String eventId, Set<String> callerOfficerClubIds);

    /** See docs/adr/03-define-event-lifecycle.md's "What may change, and when" — the guard lives in the write. */
    EventCommandResult edit(String eventId, Set<String> callerOfficerClubIds, EventEdit edit);

    /** Replaces the ordered form definition until the first Student wins a Seat. */
    FormUpdateOutcome updateRegistrationForm(
            String eventId, Set<String> callerOfficerClubIds, RegistrationForm registrationForm);

    /** Draft to Published only. Forward-only: there is no matching un-publish method. */
    EventCommandResult publish(String eventId, Set<String> callerOfficerClubIds);

    /** The owning Club's Officer. Published only, and only before endsAt. */
    EventCommandResult cancelAsOfficer(String eventId, Set<String> callerOfficerClubIds);

    /**
     * A University Admin, unscoped by Club — the one cross-Club write in the system. See
     * docs/adr/08-define-roles-and-resource-authorization.md.
     */
    EventCommandResult cancelAsAdmin(String eventId);

    /** Acquires the new Slot before changing the Event or releasing its old Slot. */
    SlotCommandOutcome bookSlotAsOfficer(
            String eventId,
            Set<String> callerOfficerClubIds,
            String venueId,
            Instant startsAt,
            Instant endsAt);

    /** University Admin path, unscoped by Club. */
    SlotCommandOutcome bookSlotAsAdmin(
            String eventId, String venueId, Instant startsAt, Instant endsAt);

    /** Idempotently releases all Slots for an Officer-scoped Event, until it starts. */
    SlotCommandOutcome releaseSlotAsOfficer(String eventId, Set<String> callerOfficerClubIds);

    /** University Admin release path, unscoped by Club. */
    SlotCommandOutcome releaseSlotAsAdmin(String eventId);

    /** Venue-day timeline after cancelled Event orphan bookings have been removed. */
    Optional<VenueDayView> findVenueDay(String venueId, LocalDate date);

    /** Published Events only, matching docs/adr/16-define-event-discovery.md's search/filter/sort/paging. */
    EventPage browse(EventBrowseQuery query);

    /**
     * The Student's own view of an Event, by id. Not scoped by Club — every signed-in account is a
     * Student — but a Draft is never visible: it has not been announced, the same way it is absent from
     * {@link #browse}.
     */
    Optional<Event> findForStudent(String eventId);

    /** Student-safe view carrying the form revision that answer validation must bind to. */
    Optional<StudentRegistrationEvent> findRegistrationForStudent(String eventId, String studentId);

    /** Officer-scoped Event/form/enrolment view for answer reporting. */
    Optional<OfficerRegistrationEvent> findRegistrationForOfficer(
            String eventId, Set<String> callerOfficerClubIds);

    /**
     * Taking a Seat — one guarded write, then (only on failure) one follow-up read to classify why. See
     * docs/adr/04-define-registration-capacity-and-waitlist.md. Correctness lives entirely in the write.
     */
    RegistrationOutcome register(String eventId, String studentId);

    /**
     * Taking a Seat against exactly the form revision already validated by Registration. A winning
     * atomic Seat write returns the enrollment version it assigned; a Waitlist success has no version.
     */
    SeatRequestResult requestSeat(String eventId, String studentId, int expectedFormRevision);

    /** Withdraws the Student from a held Seat or the Waitlist, until the Event starts. */
    WithdrawalOutcome withdraw(String eventId, String studentId);

    /** The Student's "my events": every Event, whatever its Status, where they hold a Seat. */
    EventPage findEnrolled(String studentId, int page, int size);

    /** Student-safe version of {@link #findEnrolled} for the Registration module. */
    StudentRegistrationEventPage findEnrolledRegistrations(String studentId, int page, int size);
}

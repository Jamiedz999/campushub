package com.campushub.event.internal;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.AttendanceOutcome;
import com.campushub.event.EventModule.AttendanceResult;
import com.campushub.event.EventModule.FormUpdateOutcome;
import com.campushub.event.EventModule.RegistrationForm;
import com.campushub.event.EventModule.SeatRequestOutcome;
import com.campushub.event.EventModule.SeatRequestResult;
import com.campushub.event.EventModule.SlotCommandOutcome;
import com.campushub.event.EventModule.WithdrawalOutcome;
import com.campushub.event.domain.AttendanceEntry;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventBrowseQuery;
import com.campushub.event.domain.EventCommandResult;
import com.campushub.event.domain.EventEdit;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.EventSort;
import com.campushub.event.domain.EventStatus;
import com.campushub.event.domain.Phase;
import com.campushub.event.domain.RegistrationOutcome;
import com.campushub.event.persistence.EventRepository;
import com.campushub.realtime.RealtimeModule;
import com.campushub.venue.VenueModule;
import com.campushub.venue.VenueModule.Slot;
import com.campushub.venue.VenueModule.SlotRequestOutcome;
import com.campushub.venue.VenueModule.SlotRequestResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Component;

@Component
class EventModuleImpl implements EventModule {

    private static final int MAX_PAGE_SIZE = 100;

    private final EventRepository repository;
    private final Clock clock;
    private final VenueModule venueModule;
    private final RealtimeModule realtimeModule;

    EventModuleImpl(
            EventRepository repository, Clock clock, VenueModule venueModule, RealtimeModule realtimeModule) {
        this.repository = repository;
        this.clock = clock;
        this.venueModule = venueModule;
        this.realtimeModule = realtimeModule;
    }

    @Override
    public String createDraft(
            String clubId,
            String title,
            String description,
            Instant registrationOpensAt,
            Instant registrationClosesAt,
            Instant startsAt,
            Instant endsAt,
            int capacity) {
        Event draft = new Event(
                clubId, title, description, registrationOpensAt, registrationClosesAt, startsAt, endsAt, capacity);
        return repository.insertDraft(draft);
    }

    @Override
    public Optional<Event> findForOfficer(String eventId, Set<String> callerOfficerClubIds) {
        return repository.findScopedById(eventId, callerOfficerClubIds);
    }

    @Override
    public EventCommandResult edit(String eventId, Set<String> callerOfficerClubIds, EventEdit edit) {
        boolean applied = repository.edit(eventId, callerOfficerClubIds, edit, clock.instant());
        return classify(applied, () -> repository.existsScoped(eventId, callerOfficerClubIds));
    }

    @Override
    public FormUpdateOutcome updateRegistrationForm(
            String eventId, Set<String> callerOfficerClubIds, RegistrationForm registrationForm) {
        RegistrationFormDefinitionValidator.validate(registrationForm);
        boolean applied = repository.updateRegistrationForm(
                eventId, callerOfficerClubIds, registrationForm, clock.instant());
        if (applied) {
            return FormUpdateOutcome.SUCCESS;
        }
        Optional<Event> event = repository.findScopedById(eventId, callerOfficerClubIds);
        if (event.isEmpty()) {
            return FormUpdateOutcome.NOT_FOUND;
        }
        return event.get().isRegistrationFormLocked()
                ? FormUpdateOutcome.FORM_LOCKED
                : FormUpdateOutcome.NOT_EDITABLE;
    }

    @Override
    public EventCommandResult publish(String eventId, Set<String> callerOfficerClubIds) {
        boolean applied = repository.publish(eventId, callerOfficerClubIds);
        return classify(applied, () -> repository.existsScoped(eventId, callerOfficerClubIds));
    }

    @Override
    public EventCommandResult cancelAsOfficer(String eventId, Set<String> callerOfficerClubIds) {
        Instant now = clock.instant();
        Optional<Event> cancelled = repository.cancelAsOfficer(eventId, callerOfficerClubIds, now);
        if (cancelled.filter(event -> now.isBefore(event.getStartsAt())).isPresent()) {
            venueModule.releaseEventSlots(eventId);
        }
        return classify(cancelled.isPresent(), () -> repository.existsScoped(eventId, callerOfficerClubIds));
    }

    @Override
    public EventCommandResult cancelAsAdmin(String eventId) {
        Instant now = clock.instant();
        Optional<Event> cancelled = repository.cancelAsAdmin(eventId, now);
        if (cancelled.filter(event -> now.isBefore(event.getStartsAt())).isPresent()) {
            venueModule.releaseEventSlots(eventId);
        }
        return classify(cancelled.isPresent(), () -> repository.exists(eventId));
    }

    @Override
    public SlotCommandOutcome bookSlotAsOfficer(
            String eventId,
            Set<String> callerOfficerClubIds,
            String venueId,
            Instant startsAt,
            Instant endsAt) {
        Optional<Event> current = repository.findScopedById(eventId, callerOfficerClubIds);
        if (current.isEmpty()) {
            return SlotCommandOutcome.NOT_FOUND;
        }
        Event event = current.get();
        if (event.getStatus() == EventStatus.CANCELLED) {
            return SlotCommandOutcome.NOT_EDITABLE;
        }
        if (!clock.instant().isBefore(event.getStartsAt())) {
            return SlotCommandOutcome.SLOT_ALREADY_STARTED;
        }
        Slot requestedSlot = new Slot(venueId, startsAt, endsAt);
        if (sameSlot(event, requestedSlot)) {
            return SlotCommandOutcome.SUCCESS;
        }

        SlotRequestResult request = requestSlotWithOrphanRepair(eventId, requestedSlot);
        if (request.outcome() != SlotRequestOutcome.ACQUIRED) {
            return map(request.outcome());
        }

        Optional<Event> previous =
                repository.moveToSlot(eventId, callerOfficerClubIds, requestedSlot, clock.instant());
        if (previous.isEmpty()) {
            venueModule.releaseReservation(eventId, requestedSlot);
            return repository.existsScoped(eventId, callerOfficerClubIds)
                    ? SlotCommandOutcome.NOT_EDITABLE
                    : SlotCommandOutcome.NOT_FOUND;
        }
        Event replaced = previous.get();
        if (replaced.getVenueId() != null && !sameSlot(replaced, requestedSlot)) {
            venueModule.releaseReservation(eventId, slotOf(replaced));
        }
        return SlotCommandOutcome.SUCCESS;
    }

    @Override
    public SlotCommandOutcome releaseSlotAsOfficer(
            String eventId, Set<String> callerOfficerClubIds) {
        Optional<Event> current = repository.findScopedById(eventId, callerOfficerClubIds);
        if (current.isEmpty()) {
            return SlotCommandOutcome.NOT_FOUND;
        }
        Event event = current.get();
        if (!clock.instant().isBefore(event.getStartsAt())) {
            return SlotCommandOutcome.SLOT_ALREADY_STARTED;
        }
        if (event.getVenueId() != null) {
            Optional<Event> cleared = repository.clearVenue(eventId, callerOfficerClubIds, clock.instant());
            if (cleared.isEmpty()) {
                return repository.existsScoped(eventId, callerOfficerClubIds)
                        ? SlotCommandOutcome.NOT_EDITABLE
                        : SlotCommandOutcome.NOT_FOUND;
            }
        }
        venueModule.releaseEventSlots(eventId);
        return SlotCommandOutcome.SUCCESS;
    }

    @Override
    public Optional<VenueModule.VenueDayView> findVenueDay(String venueId, LocalDate date) {
        Optional<VenueModule.VenueDayView> current = venueModule.findDay(venueId, date);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        Set<String> cancelled = repository.cancelledUpcomingEventIds(
                current.get().bookings().stream()
                        .map(VenueModule.DayBooking::eventId)
                        .toList(),
                clock.instant());
        if (cancelled.isEmpty()) {
            return current;
        }
        venueModule.removeBookings(venueId, date, cancelled);
        return venueModule.findDay(venueId, date);
    }

    private SlotRequestResult requestSlotWithOrphanRepair(String eventId, Slot slot) {
        SlotRequestResult first = venueModule.requestSlot(eventId, slot);
        if (first.outcome() != SlotRequestOutcome.SLOT_TAKEN) {
            return first;
        }
        Set<String> cancelled = repository.cancelledUpcomingEventIds(first.conflictingEventIds(), clock.instant());
        if (cancelled.isEmpty()) {
            return first;
        }
        venueModule.removeBookings(slot.venueId(), slot.startsAt(), cancelled);
        return venueModule.requestSlot(eventId, slot);
    }

    private static boolean sameSlot(Event event, Slot slot) {
        return slot.venueId().equals(event.getVenueId())
                && slot.startsAt().equals(event.getStartsAt())
                && slot.endsAt().equals(event.getEndsAt());
    }

    private static Slot slotOf(Event event) {
        return new Slot(event.getVenueId(), event.getStartsAt(), event.getEndsAt());
    }

    private static SlotCommandOutcome map(SlotRequestOutcome outcome) {
        return switch (outcome) {
            case ACQUIRED -> SlotCommandOutcome.SUCCESS;
            case NOT_FOUND -> SlotCommandOutcome.NOT_FOUND;
            case SLOT_TAKEN -> SlotCommandOutcome.SLOT_TAKEN;
            case SLOT_CROSSES_MIDNIGHT -> SlotCommandOutcome.SLOT_CROSSES_MIDNIGHT;
            case SLOT_IN_DST_TRANSITION -> SlotCommandOutcome.SLOT_IN_DST_TRANSITION;
            case SLOT_ALREADY_STARTED -> SlotCommandOutcome.SLOT_ALREADY_STARTED;
        };
    }

    @Override
    public EventPage browse(EventBrowseQuery query) {
        int size = clampSize(query.size());
        int page = clampPage(query.page());
        EventSort sort = query.sort() != null ? query.sort() : defaultSort(query.searchTerm());

        EventBrowseQuery normalized = new EventBrowseQuery(
                query.searchTerm(),
                query.clubId(),
                query.openForRegistration(),
                query.startsAtFrom(),
                query.startsAtTo(),
                query.hasFreeSeat(),
                query.sort(),
                page,
                size);

        return repository.browse(normalized, sort, clock.instant());
    }

    private static EventSort defaultSort(String searchTerm) {
        return searchTerm != null && !searchTerm.isBlank() ? EventSort.RELEVANCE : EventSort.STARTS_AT_ASC;
    }

    @Override
    public Optional<Event> findForStudent(String eventId) {
        return repository.findById(eventId).filter(event -> event.getStatus() != EventStatus.DRAFT);
    }

    @Override
    public Optional<StudentRegistrationEvent> findRegistrationForStudent(
            String eventId, String studentId) {
        return findForStudent(eventId).map(event -> studentRegistrationEvent(event, studentId));
    }

    @Override
    public Optional<OfficerRegistrationEvent> findRegistrationForOfficer(
            String eventId, Set<String> callerOfficerClubIds) {
        return repository.findScopedById(eventId, callerOfficerClubIds).map(event ->
                new OfficerRegistrationEvent(
                        event.getId(),
                        event.getTitle(),
                        event.getRegistrationForm(),
                        event.getEnrolled().stream()
                                .map(entry -> new OfficerEnrollment(
                                        entry.studentId(), entry.via().name(), entry.at(), entry.enrollmentVersion()))
                                .toList()));
    }

    @Override
    public RegistrationOutcome register(String eventId, String studentId) {
        Instant now = clock.instant();
        boolean applied = repository.takeSeat(eventId, studentId, now);
        if (applied) {
            return RegistrationOutcome.SUCCESS;
        }
        if (repository.joinWaitlist(eventId, studentId, now)) {
            return RegistrationOutcome.SUCCESS;
        }

        Optional<Event> event = repository.findById(eventId);
        // A Draft is invisible to Students the same way it is absent from browse — reported as NOT_FOUND
        // rather than a Seat Ledger reason, so a guessed id cannot confirm a Draft Event exists.
        if (event.isEmpty() || event.get().getStatus() == EventStatus.DRAFT) {
            return RegistrationOutcome.NOT_FOUND;
        }
        return RegistrationOutcome.classifyFailure(event.get(), studentId, now);
    }

    @Override
    public SeatRequestResult requestSeat(
            String eventId, String studentId, int expectedFormRevision) {
        Instant now = clock.instant();
        Optional<Long> enrollmentVersion =
                repository.takeSeatForForm(eventId, studentId, now, expectedFormRevision);
        if (enrollmentVersion.isPresent()) {
            return new SeatRequestResult(SeatRequestOutcome.SUCCESS, enrollmentVersion.get());
        }

        Optional<Event> event = repository.findById(eventId);
        if (event.isEmpty() || event.get().getStatus() == EventStatus.DRAFT) {
            return new SeatRequestResult(SeatRequestOutcome.NOT_FOUND, null);
        }
        if (event.get().getRegistrationFormRevision() != expectedFormRevision) {
            return new SeatRequestResult(SeatRequestOutcome.FORM_CHANGED, null);
        }
        if (repository.joinWaitlist(eventId, studentId, now)) {
            return new SeatRequestResult(SeatRequestOutcome.SUCCESS, null);
        }
        RegistrationOutcome classified = RegistrationOutcome.classifyFailure(event.get(), studentId, now);
        return new SeatRequestResult(SeatRequestOutcome.valueOf(classified.name()), null);
    }

    @Override
    public WithdrawalOutcome withdraw(String eventId, String studentId) {
        Instant now = clock.instant();
        boolean withdrawn = repository.withdrawEnrolled(eventId, studentId, now);
        if (!withdrawn) {
            withdrawn = repository.leaveWaitlist(eventId, studentId, now);
            if (!withdrawn) {
                // A promotion may have moved this Student from waitlist to enrolled between the two
                // writes. MongoDB serializes writes to the Event document, so one retry observes the
                // only membership transition that can make both original attempts miss.
                withdrawn = repository.withdrawEnrolled(eventId, studentId, now);
            }
        }
        if (withdrawn) {
            return WithdrawalOutcome.SUCCESS;
        }

        Optional<Event> event = repository.findById(eventId);
        if (event.isEmpty() || event.get().getStatus() == EventStatus.DRAFT) {
            return WithdrawalOutcome.NOT_FOUND;
        }
        return classifyWithdrawalFailure(event.get(), now);
    }

    @Override
    public Optional<DoorEvent> findDoorEventForOfficer(String eventId, Set<String> callerOfficerClubIds) {
        Instant now = clock.instant();
        return repository.findScopedById(eventId, callerOfficerClubIds).map(event -> new DoorEvent(
                event.getId(),
                event.getTitle(),
                checkInOpensAt(event),
                event.getEndsAt(),
                isCheckInOpen(event, now)));
    }

    @Override
    public Optional<AttendanceRoster> findAttendanceForOfficer(
            String eventId, Set<String> callerOfficerClubIds) {
        return repository.findScopedById(eventId, callerOfficerClubIds).map(event -> {
            List<AttendanceRosterEntry> items = event.getEnrolled().stream()
                    .map(seat -> attendanceOf(event, seat.studentId())
                            .map(entry -> new AttendanceRosterEntry(
                                    seat.studentId(), entry.at(), entry.method()))
                            .orElseGet(() -> new AttendanceRosterEntry(seat.studentId(), null, null)))
                    .toList();
            return new AttendanceRoster(event.getId(), event.getTitle(), items);
        });
    }

    @Override
    public AttendanceResult recordScannedAttendance(String eventId, String studentId) {
        Instant now = clock.instant();
        Optional<Event> written = repository.recordScannedAttendance(eventId, studentId, now);
        written.ifPresent(this::hintDoorScreens);
        return written.map(event -> succeeded(event, studentId))
                .orElseGet(() -> classifyAttendanceFailure(
                        repository.findById(eventId).filter(event -> event.getStatus() != EventStatus.DRAFT),
                        studentId,
                        now));
    }

    @Override
    public AttendanceResult recordManualAttendance(
            String eventId, String studentId, Set<String> callerOfficerClubIds) {
        Instant now = clock.instant();
        Optional<Event> written =
                repository.recordManualAttendance(eventId, callerOfficerClubIds, studentId, now);
        written.ifPresent(this::hintDoorScreens);
        return written.map(event -> succeeded(event, studentId))
                .orElseGet(() -> classifyAttendanceFailure(
                        repository.findScopedById(eventId, callerOfficerClubIds), studentId, now));
    }

    /**
     * Both attendance writes end here, and only when the guarded write actually changed something —
     * {@code written} is empty for a Student who was already checked in, and a hint for a door where
     * nothing moved would make every screen re-read for nothing.
     *
     * <p>It lives in the module rather than in the two controllers above it because this is the one
     * place that knows a Seat Ledger write succeeded. A hint published beside each caller would be a
     * rule maintained by whoever adds the third caller.
     */
    private void hintDoorScreens(Event event) {
        realtimeModule.publishAttendanceChanged(event.getId());
    }

    private static AttendanceResult succeeded(Event event, String studentId) {
        AttendanceEntry entry = attendanceOf(event, studentId).orElseThrow();
        return new AttendanceResult(
                AttendanceOutcome.SUCCESS, event.getTitle(), entry.at(), entry.method());
    }

    /**
     * One follow-up read, exactly as every other refused Seat Ledger command classifies itself — see
     * docs/adr/04-define-registration-capacity-and-waitlist.md. Correctness lives in the guarded write;
     * this only decides which of the door's five failure screens the Student is shown.
     */
    private static AttendanceResult classifyAttendanceFailure(
            Optional<Event> found, String studentId, Instant now) {
        if (found.isEmpty()) {
            return AttendanceResult.refused(AttendanceOutcome.NOT_FOUND, null);
        }
        Event event = found.get();
        Optional<AttendanceEntry> existing = attendanceOf(event, studentId);
        if (existing.isPresent()) {
            return new AttendanceResult(
                    AttendanceOutcome.ALREADY_CHECKED_IN,
                    event.getTitle(),
                    existing.get().at(),
                    existing.get().method());
        }
        boolean onRoster = event.getEnrolled().stream()
                .anyMatch(seat -> seat.studentId().equals(studentId));
        if (!onRoster) {
            return AttendanceResult.refused(AttendanceOutcome.NOT_ON_ROSTER, event.getTitle());
        }
        // Everything left is the window: not open yet, already ended, or an Event that was cancelled and
        // therefore has no door at all. The Student is told the door is closed, which is true of all three.
        return AttendanceResult.refused(AttendanceOutcome.CHECK_IN_WINDOW_CLOSED, event.getTitle());
    }

    private static Optional<AttendanceEntry> attendanceOf(Event event, String studentId) {
        return event.getAttendance().stream()
                .filter(entry -> entry.studentId().equals(studentId))
                .findFirst();
    }

    private static Instant checkInOpensAt(Event event) {
        return event.getStartsAt().minus(CHECK_IN_OPENS_BEFORE_START);
    }

    private static boolean isCheckInOpen(Event event, Instant now) {
        return event.getStatus() == EventStatus.PUBLISHED
                && !now.isBefore(checkInOpensAt(event))
                && now.isBefore(event.getEndsAt());
    }

    @Override
    public EventPage findEnrolled(String studentId, int page, int size) {
        return repository.findEnrolled(studentId, clampPage(page), clampSize(size));
    }

    @Override
    public StudentRegistrationEventPage findEnrolledRegistrations(
            String studentId, int page, int size) {
        EventPage result = findEnrolled(studentId, page, size);
        return new StudentRegistrationEventPage(
                result.items().stream()
                        .map(event -> studentRegistrationEvent(event, studentId))
                        .toList(),
                result.page(),
                result.size(),
                result.total());
    }

    private StudentRegistrationEvent studentRegistrationEvent(Event event, String studentId) {
        var enrollment = event.getEnrolled().stream()
                .filter(entry -> entry.studentId().equals(studentId))
                .findFirst();
        int waitlistIndex = event.getWaitlist().indexOf(studentId);
        return new StudentRegistrationEvent(
                event.getId(),
                event.getClubId(),
                event.getTitle(),
                event.getDescription(),
                Phase.of(event, clock.instant()).name(),
                event.getRegistrationOpensAt(),
                event.getRegistrationClosesAt(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getCapacity(),
                event.getEnrolled().size(),
                event.getWaitlist().size(),
                enrollment.isPresent(),
                enrollment.map(entry -> entry.via().name()).orElse(null),
                enrollment.map(entry -> entry.enrollmentVersion()).orElse(null),
                waitlistIndex < 0 ? null : waitlistIndex + 1,
                event.getRegistrationForm(),
                event.getRegistrationFormRevision());
    }

    // "Attempt first, then read once to classify" — see docs/adr/04-define-registration-capacity-and-waitlist.md.
    // Correctness lives entirely in the guarded write; this only decides which message the caller gets.
    private static EventCommandResult classify(boolean applied, BooleanSupplier existsInScope) {
        if (applied) {
            return EventCommandResult.SUCCESS;
        }
        return existsInScope.getAsBoolean() ? EventCommandResult.NOT_EDITABLE : EventCommandResult.NOT_FOUND;
    }

    private static WithdrawalOutcome classifyWithdrawalFailure(Event event, Instant now) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            return WithdrawalOutcome.EVENT_CANCELLED;
        }
        if (!now.isBefore(event.getStartsAt())) {
            return WithdrawalOutcome.EVENT_STARTED;
        }
        // DELETE is idempotent: before the freeze, an already-absent registration is the desired state.
        return WithdrawalOutcome.SUCCESS;
    }

    // Shared by every paged query — see docs/adr/15-define-http-api-and-time-contract.md: zero-indexed
    // page, size default 20 (the caller's job), hard cap 100.
    private static int clampPage(int page) {
        return Math.max(page, 0);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}

package com.campushub.event.internal;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.FormUpdateOutcome;
import com.campushub.event.EventModule.RegistrationForm;
import com.campushub.event.EventModule.SeatRequestOutcome;
import com.campushub.event.EventModule.SeatRequestResult;
import com.campushub.event.EventModule.WithdrawalOutcome;
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
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Component;

@Component
class EventModuleImpl implements EventModule {

    private static final int MAX_PAGE_SIZE = 100;

    private final EventRepository repository;
    private final Clock clock;

    EventModuleImpl(EventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
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
        boolean applied = repository.cancelAsOfficer(eventId, callerOfficerClubIds, clock.instant());
        return classify(applied, () -> repository.existsScoped(eventId, callerOfficerClubIds));
    }

    @Override
    public EventCommandResult cancelAsAdmin(String eventId) {
        boolean applied = repository.cancelAsAdmin(eventId, clock.instant());
        return classify(applied, () -> repository.exists(eventId));
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

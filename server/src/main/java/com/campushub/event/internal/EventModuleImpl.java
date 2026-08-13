package com.campushub.event.internal;

import com.campushub.event.EventModule;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventBrowseQuery;
import com.campushub.event.domain.EventCommandResult;
import com.campushub.event.domain.EventEdit;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.EventSort;
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
        int size = Math.min(Math.max(query.size(), 1), MAX_PAGE_SIZE);
        int page = Math.max(query.page(), 0);
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

    // "Attempt first, then read once to classify" — see docs/adr/04-define-registration-capacity-and-waitlist.md.
    // Correctness lives entirely in the guarded write; this only decides which message the caller gets.
    private static EventCommandResult classify(boolean applied, BooleanSupplier existsInScope) {
        if (applied) {
            return EventCommandResult.SUCCESS;
        }
        return existsInScope.getAsBoolean() ? EventCommandResult.NOT_EDITABLE : EventCommandResult.NOT_FOUND;
    }
}

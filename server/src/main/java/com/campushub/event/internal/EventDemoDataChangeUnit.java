package com.campushub.event.internal;

import com.campushub.club.ClubModule;
import com.campushub.event.domain.Event;
import com.campushub.event.persistence.EventRepository;
import com.campushub.identityaccess.IdentityAccessModule;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.context.annotation.Profile;

// Profile-gated, same rule as identityaccess-demo-data-004: never runs outside "development", so a
// seeded Event is never silently mistaken for real data. Runs in its own Club rather than one of
// identityaccess's demo Clubs — event has no way to look up a Club created by another module's
// migration, and creating its own keeps this change unit self-contained. The demo officer is granted
// rights on it so the officer console has something to show too. See Issue #16.
@ChangeUnit(id = "event-demo-data-009", order = "009")
@Profile("development")
public class EventDemoDataChangeUnit {

    @Execution
    public void execution(
            EventRepository eventRepository,
            ClubModule clubModule,
            IdentityAccessModule identityAccessModule,
            Clock clock) {
        String clubId = clubModule.createClub("Photography Club");
        identityAccessModule
                .findAccountIdByEmail("officer@demo.campushub")
                .ifPresent(officerId -> clubModule.grantOfficer(clubId, officerId));

        Instant now = Instant.now(clock);
        Event event = new Event(
                clubId,
                "Beginner Darkroom Workshop",
                "Hands-on introduction to developing black-and-white film — no experience needed, "
                        + "all materials provided.",
                now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(6)),
                now.plus(Duration.ofDays(7)),
                now.plus(Duration.ofDays(7)).plus(Duration.ofHours(2)),
                40);

        String eventId = eventRepository.insertDraft(event);
        eventRepository.publish(eventId, Set.of(clubId));
    }

    @RollbackExecution
    public void rollback() {}
}

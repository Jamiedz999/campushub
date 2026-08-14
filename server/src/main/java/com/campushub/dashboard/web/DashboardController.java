package com.campushub.dashboard.web;

import com.campushub.club.ClubModule;
import com.campushub.dashboard.DashboardModule;
import com.campushub.dashboard.DashboardModule.DashboardView;
import com.campushub.dashboard.web.DashboardResponse.DashboardScope;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.shared.NotFoundException;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The ADR's two views behind one endpoint, because they are the same numbers over different Clubs. The
// only thing that differs is the scope, and the scope is decided here — from the caller's grants, never
// from what the caller asked for.
//
// A Club Officer who names another Club's id gets a 404, not a 403: the query would have been scoped to
// a Club they hold no grant in, so there is genuinely nothing there to find. Same for a plain Student,
// who officers nothing and so has no dashboard at all. See
// docs/adr/08-define-roles-and-resource-authorization.md and
// docs/adr/15-define-http-api-and-time-contract.md.
@RestController
class DashboardController {

    private static final String NO_DASHBOARD =
            "No dashboard for this caller — a dashboard is scoped to the Clubs an account officers.";

    private final IdentityAccessModule identityAccessModule;
    private final DashboardModule dashboardModule;
    private final ClubModule clubModule;

    DashboardController(
            IdentityAccessModule identityAccessModule, DashboardModule dashboardModule, ClubModule clubModule) {
        this.identityAccessModule = identityAccessModule;
        this.dashboardModule = dashboardModule;
        this.clubModule = clubModule;
    }

    @GetMapping("/api/dashboard")
    DashboardResponse read(
            @RequestParam(required = false) String clubId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        CurrentActor actor = identityAccessModule.currentActor();

        DashboardScope scope = scopeFor(actor, clubId);
        DashboardView view = scope == DashboardScope.ALL_CLUBS
                ? dashboardModule.findAcrossAllClubs(from, to)
                : dashboardModule.findForClubs(clubsFor(actor, clubId), from, to);

        return DashboardResponse.from(view, scope, clubModule.clubNames(clubIdsIn(view)));
    }

    private static DashboardScope scopeFor(CurrentActor actor, String clubId) {
        if (clubId != null) {
            if (!actor.isEntitledToClub(clubId)) {
                throw new NotFoundException(NO_DASHBOARD);
            }
            return DashboardScope.CLUB;
        }
        if (actor.isUniversityAdmin()) {
            return DashboardScope.ALL_CLUBS;
        }
        if (actor.officerClubIds().isEmpty()) {
            throw new NotFoundException(NO_DASHBOARD);
        }
        return DashboardScope.CLUB;
    }

    private static Set<String> clubsFor(CurrentActor actor, String clubId) {
        return clubId == null ? actor.officerClubIds() : Set.of(clubId);
    }

    // Only the Clubs the scoped read actually returned rows for, so the label lookup never reaches
    // wider than the numbers beside it.
    private static Set<String> clubIdsIn(DashboardView view) {
        return Stream.concat(
                        view.clubMonths().stream().map(row -> row.clubId()),
                        view.events().stream().map(event -> event.clubId()))
                .collect(Collectors.toUnmodifiableSet());
    }
}

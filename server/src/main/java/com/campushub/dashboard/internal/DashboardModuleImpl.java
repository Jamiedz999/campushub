package com.campushub.dashboard.internal;

import com.campushub.dashboard.DashboardModule;
import com.campushub.dashboard.persistence.DashboardRepository;
import com.campushub.shared.CampusProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Component;

// Five pipelines, one window. The window is resolved once here and handed to all of them, so no two
// numbers on the same screen can be computed over different ranges — the failure this assembly exists
// to make impossible. See docs/adr/09-define-attendance-dashboard.md.
@Component
class DashboardModuleImpl implements DashboardModule {

    private final DashboardRepository repository;
    private final CampusProperties campusProperties;
    private final Clock clock;

    DashboardModuleImpl(DashboardRepository repository, CampusProperties campusProperties, Clock clock) {
        this.repository = repository;
        this.campusProperties = campusProperties;
        this.clock = clock;
    }

    @Override
    public DashboardView findForClubs(Set<String> clubIds, Instant from, Instant to) {
        return read(Set.copyOf(clubIds), from, to);
    }

    @Override
    public DashboardView findAcrossAllClubs(Instant from, Instant to) {
        return read(null, from, to);
    }

    // null clubIds is the repository's "every Club"; it is unreachable from findForClubs, which copies
    // whatever set it was given, so the unscoped read has exactly one caller and it is named for it.
    private DashboardView read(Set<String> clubIds, Instant from, Instant to) {
        DashboardRange range = DashboardRange.resolve(from, to, clock.instant(), campusProperties.getZone());
        return new DashboardView(
                range.from(),
                range.to(),
                repository.totals(clubIds, range.from(), range.to()),
                repository.monthlyTotals(clubIds, range.from(), range.to(), campusProperties.getZone()),
                repository.clubTotals(clubIds, range.from(), range.to()),
                repository.eventTotals(clubIds, range.from(), range.to()),
                repository.excludedEvents(clubIds, range.from(), range.to()));
    }
}

package com.campushub.dashboard.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.dashboard.DashboardModule.ClubTotals;
import com.campushub.dashboard.DashboardModule.DashboardView;
import com.campushub.dashboard.DashboardModule.EventTotals;
import com.campushub.dashboard.DashboardModule.ExcludedEvents;
import com.campushub.dashboard.DashboardModule.MetricTotals;
import com.campushub.dashboard.DashboardModule.MonthTotals;
import com.campushub.dashboard.persistence.DashboardRepository;
import com.campushub.shared.CampusProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardModuleImplTest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    private static final Instant NOW = Instant.parse("2026-08-14T10:15:00Z");
    private static final Instant DEFAULT_FROM = Instant.parse("2025-08-31T23:00:00Z");
    private static final Set<String> CLUB_A = Set.of("club-a");

    @Mock
    private DashboardRepository repository;

    private DashboardModuleImpl module;

    @BeforeEach
    void setUp() {
        CampusProperties campusProperties = new CampusProperties();
        campusProperties.setZone(DUBLIN);
        module = new DashboardModuleImpl(repository, campusProperties, Clock.fixed(NOW, DUBLIN));
    }

    @Test
    void everyPipelineIsAskedForTheSameWindowSoNoTwoNumbersCoverDifferentRanges() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");

        module.findForClubs(CLUB_A, from, to);

        verify(repository).totals(CLUB_A, from, to);
        verify(repository).monthlyTotals(CLUB_A, from, to, DUBLIN);
        verify(repository).clubTotals(CLUB_A, from, to);
        verify(repository).eventTotals(CLUB_A, from, to);
        verify(repository).excludedEvents(CLUB_A, from, to);
    }

    @Test
    void theCrossClubViewIsTheOnlyUnscopedReadAndTheViewReportsItsOwnRange() {
        when(repository.totals(any(), any(), any())).thenReturn(new MetricTotals(4, 100, 90, 70, 3, 9, 8, 6));
        when(repository.monthlyTotals(any(), any(), any(), any()))
                .thenReturn(List.of(new MonthTotals("2026-07", 4, 100, 90, 70)));
        when(repository.clubTotals(any(), any(), any()))
                .thenReturn(List.of(new ClubTotals("club-a", 4, 100, 90, 70, 8)));
        when(repository.eventTotals(any(), any(), any()))
                .thenReturn(List.of(new EventTotals("event-1", "Talk", "club-a", NOW, 25, 22, 18, 2)));
        when(repository.excludedEvents(any(), any(), any())).thenReturn(new ExcludedEvents(1, 2, 0));

        DashboardView view = module.findAcrossAllClubs(null, null);

        ArgumentCaptor<Set<String>> scope = ArgumentCaptor.captor();
        verify(repository).totals(scope.capture(), eq(DEFAULT_FROM), eq(NOW));
        assertThat(scope.getValue()).isNull();
        assertThat(view.from()).isEqualTo(DEFAULT_FROM);
        assertThat(view.to()).isEqualTo(NOW);
        assertThat(view.totals().eventsRun()).isEqualTo(4);
        assertThat(view.months()).hasSize(1);
        assertThat(view.clubs()).hasSize(1);
        assertThat(view.events()).hasSize(1);
        assertThat(view.excluded()).isEqualTo(new ExcludedEvents(1, 2, 0));
    }

    @Test
    void aClubScopedReadIsNeverTheUnscopedOneEvenWhenTheCallerHasNoGrants() {
        module.findForClubs(Set.of(), null, null);

        verify(repository).totals(Set.of(), DEFAULT_FROM, NOW);
    }
}

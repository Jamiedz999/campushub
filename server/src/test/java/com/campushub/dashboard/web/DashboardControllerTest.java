package com.campushub.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.club.ClubModule;
import com.campushub.dashboard.DashboardModule;
import com.campushub.dashboard.DashboardModule.ClubMonthTotals;
import com.campushub.dashboard.DashboardModule.DashboardView;
import com.campushub.dashboard.DashboardModule.EventTotals;
import com.campushub.dashboard.DashboardModule.ExcludedEvents;
import com.campushub.dashboard.DashboardModule.MetricTotals;
import com.campushub.dashboard.web.DashboardResponse.DashboardScope;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.shared.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-14T10:15:00Z");
    private static final Instant ENDS = Instant.parse("2026-03-10T20:00:00Z");

    @Mock
    private IdentityAccessModule identityAccessModule;

    @Mock
    private DashboardModule dashboardModule;

    @Mock
    private ClubModule clubModule;

    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(identityAccessModule, dashboardModule, clubModule);
    }

    @Test
    void anOfficerSeesTheirOwnClubsAndTheirNamesAreAttachedToEveryRow() {
        when(identityAccessModule.currentActor()).thenReturn(officerOf("club-a"));
        when(dashboardModule.findForClubs(Set.of("club-a"), FROM, TO)).thenReturn(view());
        when(clubModule.clubNames(Set.of("club-a"))).thenReturn(Map.of("club-a", "Robotics Society"));

        DashboardResponse response = controller.read(null, FROM, TO);

        assertThat(response.scope()).isEqualTo(DashboardScope.CLUB);
        assertThat(response.clubMonths())
                .singleElement()
                .extracting(DashboardResponse.ClubMonth::clubName)
                .isEqualTo("Robotics Society");
        assertThat(response.events())
                .singleElement()
                .extracting(DashboardResponse.Event::clubName)
                .isEqualTo("Robotics Society");
        verify(dashboardModule, never()).findAcrossAllClubs(any(), any());
    }

    @Test
    void anOfficerAskingForAnotherClubsMetricsFindsNothingRatherThanBeingRefused() {
        when(identityAccessModule.currentActor()).thenReturn(officerOf("club-a"));

        assertThatThrownBy(() -> controller.read("club-b", FROM, TO)).isInstanceOf(NotFoundException.class);

        verify(dashboardModule, never()).findForClubs(any(), any(), any());
        verify(dashboardModule, never()).findAcrossAllClubs(any(), any());
    }

    @Test
    void aStudentWhoOfficersNothingHasNoDashboardAtAll() {
        when(identityAccessModule.currentActor())
                .thenReturn(new CurrentActor("s1", "s@demo", "S. Kaur", SystemRole.STUDENT, Set.of()));

        assertThatThrownBy(() -> controller.read(null, FROM, TO)).isInstanceOf(NotFoundException.class);

        verify(dashboardModule, never()).findForClubs(any(), any(), any());
    }

    @Test
    void aUniversityAdminReadsAcrossEveryClub() {
        when(identityAccessModule.currentActor()).thenReturn(admin());
        when(dashboardModule.findAcrossAllClubs(FROM, TO)).thenReturn(view());
        when(clubModule.clubNames(Set.of("club-a"))).thenReturn(Map.of("club-a", "Robotics Society"));

        DashboardResponse response = controller.read(null, FROM, TO);

        assertThat(response.scope()).isEqualTo(DashboardScope.ALL_CLUBS);
        assertThat(response.totals().everQueued()).isEqualTo(9);
        assertThat(response.excluded()).isEqualTo(new DashboardResponse.Excluded(1, 2, 0));
    }

    @Test
    void aUniversityAdminNarrowingToOneClubGetsThatClubScopedRead() {
        when(identityAccessModule.currentActor()).thenReturn(admin());
        when(dashboardModule.findForClubs(Set.of("club-a"), FROM, TO)).thenReturn(view());
        when(clubModule.clubNames(Set.of("club-a"))).thenReturn(Map.of("club-a", "Robotics Society"));

        DashboardResponse response = controller.read("club-a", FROM, TO);

        assertThat(response.scope()).isEqualTo(DashboardScope.CLUB);
        verify(dashboardModule, never()).findAcrossAllClubs(any(), any());
    }

    @Test
    void aRowWhoseClubNameIsMissingKeepsItsIdRatherThanGoingBlank() {
        when(identityAccessModule.currentActor()).thenReturn(admin());
        when(dashboardModule.findAcrossAllClubs(null, null)).thenReturn(view());
        when(clubModule.clubNames(Set.of("club-a"))).thenReturn(Map.of());

        DashboardResponse response = controller.read(null, null, null);

        assertThat(response.clubMonths())
                .singleElement()
                .extracting(DashboardResponse.ClubMonth::clubName)
                .isEqualTo("club-a");
        assertThat(response.clubMonths())
                .containsExactly(
                        new DashboardResponse.ClubMonth("club-a", "club-a", "2026-03", 1, 25, 22, 18, 2));
        assertThat(response.from()).isEqualTo(FROM);
        assertThat(response.to()).isEqualTo(TO);
    }

    private static DashboardView view() {
        return new DashboardView(
                FROM,
                TO,
                new MetricTotals(1, 25, 22, 18, 3, 9, 2, 4),
                List.of(new ClubMonthTotals("club-a", "2026-03", 1, 25, 22, 18, 2)),
                List.of(new EventTotals("event-1", "Robotics talk", "club-a", ENDS, 25, 22, 18, 2)),
                new ExcludedEvents(1, 2, 0));
    }

    private static CurrentActor officerOf(String clubId) {
        return new CurrentActor("o1", "o@demo", "R. Nolan", SystemRole.STUDENT, Set.of(clubId));
    }

    private static CurrentActor admin() {
        return new CurrentActor("a1", "admin@demo", "Registrar", SystemRole.UNIVERSITY_ADMIN, Set.of());
    }
}

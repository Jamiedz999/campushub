package com.campushub.identityaccess.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.club.ClubModule;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.shared.NotFoundException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClubOfficerControllerTest {

    @Mock
    private IdentityAccessModule identityAccessModule;

    @Mock
    private ClubModule clubModule;

    private ClubOfficerController controller;

    @BeforeEach
    void setUp() {
        controller = new ClubOfficerController(identityAccessModule, clubModule);
    }

    @Test
    void aUniversityAdminSeesTheOfficersOfAnyClub() {
        when(identityAccessModule.currentActor())
                .thenReturn(new CurrentActor(
                        "admin-1", "admin@demo.campushub", "Admin", SystemRole.UNIVERSITY_ADMIN, Set.of()));
        when(clubModule.officersOf("club-a")).thenReturn(List.of("account-1"));

        ClubOfficersResponse response = controller.officersOf("club-a");

        assertThat(response.officerAccountIds()).containsExactly("account-1");
    }

    @Test
    void aClubsOwnOfficerSeesItsOfficers() {
        when(identityAccessModule.currentActor())
                .thenReturn(new CurrentActor(
                        "account-1", "officer@demo.campushub", "Officer", SystemRole.STUDENT, Set.of("club-a")));
        when(clubModule.officersOf("club-a")).thenReturn(List.of("account-1"));

        ClubOfficersResponse response = controller.officersOf("club-a");

        assertThat(response.officerAccountIds()).containsExactly("account-1");
    }

    @Test
    void aStudentWithNoGrantsIsRefusedWithNotFound() {
        when(identityAccessModule.currentActor())
                .thenReturn(new CurrentActor(
                        "account-2", "student@demo.campushub", "Student", SystemRole.STUDENT, Set.of()));

        assertThatThrownBy(() -> controller.officersOf("club-a")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anOfficerOfAnotherClubIsRefusedWithNotFound() {
        when(identityAccessModule.currentActor())
                .thenReturn(new CurrentActor(
                        "account-3", "officer@demo.campushub", "Officer", SystemRole.STUDENT, Set.of("club-b")));

        assertThatThrownBy(() -> controller.officersOf("club-a")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aUniversityAdminCanGrantOfficerRights() {
        when(identityAccessModule.currentActor())
                .thenReturn(new CurrentActor(
                        "admin-1", "admin@demo.campushub", "Admin", SystemRole.UNIVERSITY_ADMIN, Set.of()));

        controller.grantOfficer("club-a", new GrantOfficerRequest("account-1"));

        verify(clubModule).grantOfficer("club-a", "account-1");
    }

    @Test
    void aStudentCannotGrantOfficerRights() {
        when(identityAccessModule.currentActor())
                .thenReturn(new CurrentActor(
                        "account-2", "student@demo.campushub", "Student", SystemRole.STUDENT, Set.of()));

        assertThatThrownBy(() -> controller.grantOfficer("club-a", new GrantOfficerRequest("account-1")))
                .isInstanceOf(NotFoundException.class);
        verify(clubModule, never()).grantOfficer(any(), any());
    }

    @Test
    void aClubsOwnOfficerCannotGrantOfficerRightsEither() {
        when(identityAccessModule.currentActor())
                .thenReturn(new CurrentActor(
                        "account-3", "officer@demo.campushub", "Officer", SystemRole.STUDENT, Set.of("club-a")));

        assertThatThrownBy(() -> controller.grantOfficer("club-a", new GrantOfficerRequest("account-1")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aUniversityAdminCanRevokeOfficerRights() {
        when(identityAccessModule.currentActor())
                .thenReturn(new CurrentActor(
                        "admin-1", "admin@demo.campushub", "Admin", SystemRole.UNIVERSITY_ADMIN, Set.of()));

        controller.revokeOfficer("club-a", "account-1");

        verify(clubModule).revokeOfficer("club-a", "account-1");
    }

    @Test
    void aStudentCannotRevokeOfficerRights() {
        when(identityAccessModule.currentActor())
                .thenReturn(new CurrentActor(
                        "account-2", "student@demo.campushub", "Student", SystemRole.STUDENT, Set.of()));

        assertThatThrownBy(() -> controller.revokeOfficer("club-a", "account-1")).isInstanceOf(NotFoundException.class);
    }
}

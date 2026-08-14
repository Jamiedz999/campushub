package com.campushub.club.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.club.persistence.ClubOfficerGrantRepository;
import com.campushub.club.persistence.ClubRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClubModuleImplTest {

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private ClubOfficerGrantRepository grantRepository;

    private ClubModuleImpl module;

    @BeforeEach
    void setUp() {
        module = new ClubModuleImpl(clubRepository, grantRepository);
    }

    @Test
    void clubNamesDelegatesToTheRepository() {
        when(clubRepository.namesOf(Set.of("club-1"))).thenReturn(Map.of("club-1", "Chess Club"));

        assertThat(module.clubNames(Set.of("club-1"))).containsExactly(entry("club-1", "Chess Club"));
    }

    @Test
    void createClubDelegatesToTheRepository() {
        when(clubRepository.create("Chess Club")).thenReturn("club-1");

        String id = module.createClub("Chess Club");

        assertThat(id).isEqualTo("club-1");
    }

    @Test
    void grantOfficerDelegatesToTheGrantRepository() {
        module.grantOfficer("club-1", "account-1");

        verify(grantRepository).grant("club-1", "account-1");
    }

    @Test
    void revokeOfficerDelegatesToTheGrantRepository() {
        module.revokeOfficer("club-1", "account-1");

        verify(grantRepository).revoke("club-1", "account-1");
    }

    @Test
    void officersOfDelegatesToTheGrantRepository() {
        when(grantRepository.officerAccountIdsOf("club-1")).thenReturn(List.of("account-1", "account-2"));

        assertThat(module.officersOf("club-1")).containsExactly("account-1", "account-2");
    }

    @Test
    void officerClubIdsForDelegatesToTheGrantRepository() {
        when(grantRepository.clubIdsOfficeredBy("account-1")).thenReturn(Set.of("club-1", "club-2"));

        assertThat(module.officerClubIdsFor("account-1")).containsExactlyInAnyOrder("club-1", "club-2");
    }
}

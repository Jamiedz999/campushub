package com.campushub.identityaccess.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CurrentActorTest {

    @Test
    void aUniversityAdminIsEntitledToEveryClub() {
        CurrentActor admin = new CurrentActor(
                "account-1", "admin@demo.campushub", "Admin", SystemRole.UNIVERSITY_ADMIN, Set.of());

        assertThat(admin.isUniversityAdmin()).isTrue();
        assertThat(admin.isEntitledToClub("any-club-id")).isTrue();
    }

    @Test
    void aStudentIsEntitledOnlyToClubsTheyOfficer() {
        CurrentActor student = new CurrentActor(
                "account-2", "student@demo.campushub", "Student", SystemRole.STUDENT, Set.of("club-a", "club-b"));

        assertThat(student.isUniversityAdmin()).isFalse();
        assertThat(student.isOfficerOf("club-a")).isTrue();
        assertThat(student.isEntitledToClub("club-a")).isTrue();
        assertThat(student.isEntitledToClub("club-b")).isTrue();
        assertThat(student.isEntitledToClub("club-c")).isFalse();
    }

    @Test
    void aStudentWithNoGrantsIsEntitledToNoClub() {
        CurrentActor student =
                new CurrentActor("account-3", "nobody@demo.campushub", "Nobody", SystemRole.STUDENT, Set.of());

        assertThat(student.isOfficerOf("club-a")).isFalse();
        assertThat(student.isEntitledToClub("club-a")).isFalse();
    }
}

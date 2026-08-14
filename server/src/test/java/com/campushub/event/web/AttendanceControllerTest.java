package com.campushub.event.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.campushub.event.EventModule;
import com.campushub.event.EventModule.AttendanceMethod;
import com.campushub.event.EventModule.AttendanceOutcome;
import com.campushub.event.EventModule.AttendanceResult;
import com.campushub.event.EventModule.AttendanceRoster;
import com.campushub.event.EventModule.AttendanceRosterEntry;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    private static final Instant AT = Instant.parse("2026-03-20T18:04:00Z");
    private static final Set<String> CLUB_IDS = Set.of("club-a");

    @Mock
    private IdentityAccessModule identityAccessModule;

    @Mock
    private EventModule eventModule;

    private AttendanceController controller;

    @BeforeEach
    void setUp() {
        controller = new AttendanceController(identityAccessModule, eventModule);
    }

    @Test
    void theRosterNamesTheStudentsAndKeepsScannedAndManualApart() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(eventModule.findAttendanceForOfficer("event-1", CLUB_IDS)).thenReturn(Optional.of(roster()));
        when(identityAccessModule.displayNames(Set.of("scanned-student", "manual-student", "absent-student")))
                .thenReturn(Map.of("scanned-student", "R. Nolan", "manual-student", "S. Kaur"));

        AttendanceRosterResponse response = controller.roster("event-1");

        assertThat(response.attendedCount()).isEqualTo(2);
        assertThat(response.items())
                .containsExactly(
                        new AttendanceRosterResponse.AttendeeResponse(
                                "scanned-student", "R. Nolan", AT, AttendanceMethod.SCANNED),
                        new AttendanceRosterResponse.AttendeeResponse(
                                "manual-student", "S. Kaur", AT, AttendanceMethod.MANUAL),
                        // An account whose display name is missing falls back to its id rather than a blank.
                        new AttendanceRosterResponse.AttendeeResponse("absent-student", "absent-student", null, null));
    }

    @Test
    void anOfficerOfAnotherClubGetsNotFoundRatherThanForbidden() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(eventModule.findAttendanceForOfficer("event-1", CLUB_IDS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.roster("event-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anOverrideMarksTheStudentPresentThroughTheCallersOwnClubGrants() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(eventModule.recordManualAttendance("event-1", "student-1", CLUB_IDS))
                .thenReturn(new AttendanceResult(
                        AttendanceOutcome.SUCCESS, "Intro to Climbing", AT, AttendanceMethod.MANUAL));

        assertThatCode(() -> controller.markPresent("event-1", "student-1")).doesNotThrowAnyException();
    }

    @Test
    void markingSomeoneWhoIsAlreadyPresentIsAnIdempotentNoOpRatherThanAnError() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(eventModule.recordManualAttendance("event-1", "student-1", CLUB_IDS))
                .thenReturn(new AttendanceResult(
                        AttendanceOutcome.ALREADY_CHECKED_IN, "Intro to Climbing", AT, AttendanceMethod.SCANNED));

        assertThatCode(() -> controller.markPresent("event-1", "student-1")).doesNotThrowAnyException();
    }

    @Test
    void aWaitlistedStudentCannotBeMarkedPresentEvenByTheirClubsOfficer() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(eventModule.recordManualAttendance("event-1", "hopeful", CLUB_IDS))
                .thenReturn(AttendanceResult.refused(AttendanceOutcome.NOT_ON_ROSTER, "Intro to Climbing"));

        assertThatThrownBy(() -> controller.markPresent("event-1", "hopeful"))
                .isInstanceOf(ConflictException.class)
                .extracting(thrown -> ((ConflictException) thrown).code())
                .isEqualTo(ErrorCode.NOT_ON_ROSTER);
    }

    @Test
    void anOverrideOutsideTheCheckInWindowIsRefusedWithItsOwnCode() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(eventModule.recordManualAttendance("event-1", "student-1", CLUB_IDS))
                .thenReturn(AttendanceResult.refused(AttendanceOutcome.CHECK_IN_WINDOW_CLOSED, "Intro to Climbing"));

        assertThatThrownBy(() -> controller.markPresent("event-1", "student-1"))
                .isInstanceOf(ConflictException.class)
                .extracting(thrown -> ((ConflictException) thrown).code())
                .isEqualTo(ErrorCode.CHECK_IN_WINDOW_CLOSED);
    }

    @Test
    void anUnknownEventIsNotFound() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(eventModule.recordManualAttendance("event-1", "student-1", CLUB_IDS))
                .thenReturn(AttendanceResult.refused(AttendanceOutcome.NOT_FOUND, null));

        assertThatThrownBy(() -> controller.markPresent("event-1", "student-1"))
                .isInstanceOf(NotFoundException.class);
    }

    private static AttendanceRoster roster() {
        return new AttendanceRoster(
                "event-1",
                "Intro to Climbing",
                40,
                3,
                2,
                List.of(
                        new AttendanceRosterEntry("scanned-student", AT, AttendanceMethod.SCANNED),
                        new AttendanceRosterEntry("manual-student", AT, AttendanceMethod.MANUAL),
                        new AttendanceRosterEntry("absent-student", null, null)));
    }

    private static CurrentActor officer() {
        return new CurrentActor("officer-1", "officer@campushub", "Officer", SystemRole.STUDENT, CLUB_IDS);
    }
}

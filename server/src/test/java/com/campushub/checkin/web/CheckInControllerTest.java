package com.campushub.checkin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.campushub.checkin.CheckInModule;
import com.campushub.checkin.CheckInModule.DoorCode;
import com.campushub.checkin.CheckInModule.ScanOutcome;
import com.campushub.checkin.CheckInModule.ScanResult;
import com.campushub.event.EventModule.AttendanceMethod;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.shared.ConflictException;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.NotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Five of the door's six states are failures, and each one has to arrive at the phone as its own stable
// `code` — see docs/adr/15-define-http-api-and-time-contract.md. That mapping is what this test pins.
@ExtendWith(MockitoExtension.class)
class CheckInControllerTest {

    private static final Instant AT = Instant.parse("2026-03-20T18:04:00Z");

    @Mock
    private IdentityAccessModule identityAccessModule;

    @Mock
    private CheckInModule checkInModule;

    private CheckInController controller;

    @BeforeEach
    void setUp() {
        controller = new CheckInController(identityAccessModule, checkInModule);
    }

    @Test
    void theDoorScreenIsIssuedAgainstTheCallersOwnClubGrants() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(checkInModule.issueDoorCode("event-1", Set.of("club-a"))).thenReturn(Optional.of(doorCode()));

        DoorCodeResponse response = controller.doorCode("event-1");

        assertThat(response.token()).isEqualTo("event-1.29566667.signature");
        assertThat(response.checkInOpen()).isTrue();
    }

    @Test
    void anOfficerOfAnotherClubGetsNotFoundRatherThanForbidden() {
        when(identityAccessModule.currentActor()).thenReturn(officer());
        when(checkInModule.issueDoorCode("event-1", Set.of("club-a"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.doorCode("event-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aStudentIsIdentifiedByTheirSessionAndNeverByTheRequestBody() {
        when(identityAccessModule.currentActor()).thenReturn(student());
        when(checkInModule.checkIn("event-1", "a-scanned-code", "student-1"))
                .thenReturn(new ScanResult(ScanOutcome.SUCCESS, "Intro to Climbing", AT, AttendanceMethod.SCANNED));

        CheckInResponse response = controller.checkIn("event-1", new ScanRequest("a-scanned-code"));

        assertThat(response)
                .isEqualTo(new CheckInResponse("event-1", "Intro to Climbing", AT, AttendanceMethod.SCANNED));
    }

    @ParameterizedTest
    @MethodSource("refusals")
    void eachRefusalCarriesItsOwnCode(ScanOutcome outcome, ErrorCode code) {
        when(identityAccessModule.currentActor()).thenReturn(student());
        when(checkInModule.checkIn("event-1", "a-scanned-code", "student-1"))
                .thenReturn(ScanResult.refused(outcome));

        assertThatThrownBy(() -> controller.checkIn("event-1", new ScanRequest("a-scanned-code")))
                .isInstanceOf(ConflictException.class)
                .extracting(thrown -> ((ConflictException) thrown).code())
                .isEqualTo(code);
    }

    @Test
    void aSecondScanIsToldWhenTheFirstOneWasSoItCanSaySo() {
        when(identityAccessModule.currentActor()).thenReturn(student());
        when(checkInModule.checkIn("event-1", "a-scanned-code", "student-1"))
                .thenReturn(new ScanResult(
                        ScanOutcome.ALREADY_CHECKED_IN, "Intro to Climbing", AT, AttendanceMethod.SCANNED));

        assertThatThrownBy(() -> controller.checkIn("event-1", new ScanRequest("a-scanned-code")))
                .isInstanceOf(ConflictException.class)
                .satisfies(thrown -> {
                    ConflictException conflict = (ConflictException) thrown;
                    assertThat(conflict.code()).isEqualTo(ErrorCode.ALREADY_CHECKED_IN);
                    assertThat(conflict.extensions())
                            .containsEntry("at", AT)
                            .containsEntry("method", AttendanceMethod.SCANNED);
                });
    }

    @Test
    void anUnknownEventIsNotFound() {
        when(identityAccessModule.currentActor()).thenReturn(student());
        when(checkInModule.checkIn("event-1", "a-scanned-code", "student-1"))
                .thenReturn(ScanResult.refused(ScanOutcome.NOT_FOUND));

        assertThatThrownBy(() -> controller.checkIn("event-1", new ScanRequest("a-scanned-code")))
                .isInstanceOf(NotFoundException.class);
    }

    private static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(ScanOutcome.TOKEN_INVALID, ErrorCode.TOKEN_INVALID),
                Arguments.of(ScanOutcome.TOKEN_EXPIRED, ErrorCode.TOKEN_EXPIRED),
                Arguments.of(ScanOutcome.NOT_ON_ROSTER, ErrorCode.NOT_ON_ROSTER),
                Arguments.of(ScanOutcome.CHECK_IN_WINDOW_CLOSED, ErrorCode.CHECK_IN_WINDOW_CLOSED));
    }

    private static DoorCode doorCode() {
        return new DoorCode(
                "event-1",
                "Intro to Climbing",
                "event-1.29566667.signature",
                AT.plusSeconds(41),
                AT.minusSeconds(1500),
                AT.plusSeconds(3600),
                true);
    }

    private static CurrentActor officer() {
        return new CurrentActor("officer-1", "officer@campushub", "Officer", SystemRole.STUDENT, Set.of("club-a"));
    }

    private static CurrentActor student() {
        return new CurrentActor("student-1", "student@campushub", "Student", SystemRole.STUDENT, Set.of());
    }
}

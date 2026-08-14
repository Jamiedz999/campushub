package com.campushub.checkin.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campushub.checkin.CheckInModule.DoorCode;
import com.campushub.checkin.CheckInModule.ScanOutcome;
import com.campushub.checkin.CheckInModule.ScanResult;
import com.campushub.event.EventModule;
import com.campushub.event.EventModule.AttendanceMethod;
import com.campushub.event.EventModule.AttendanceOutcome;
import com.campushub.event.EventModule.AttendanceResult;
import com.campushub.event.EventModule.DoorEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

// The seam itself: this module proves presence and identity, and then hands a verified pair to event.
// What is asserted below is as much what it does not do — no Seat Ledger write is even attempted for a
// code that fails to verify — as what it does. See docs/adr/07-define-qr-checkin-and-anti-fraud.md.
@ExtendWith(MockitoExtension.class)
class CheckInModuleImplTest {

    private static final String SECRET = "test-only-checkin-hmac-secret";
    private static final String EVENT_ID = "68a1b2c3d4e5f60718293a4b";
    private static final Instant NOW = Instant.ofEpochSecond(1_774_000_040L);
    private static final Set<String> CLUB_IDS = Set.of("club-a");

    private final CheckInTokenCodec codec = new CheckInTokenCodec(SECRET);

    @Mock
    private EventModule eventModule;

    private CheckInModuleImpl module;

    @BeforeEach
    void setUp() {
        module = new CheckInModuleImpl(codec, eventModule, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theDoorScreenGetsTheCodeForThisInstantAndTheEventsOwnWindow() {
        when(eventModule.findDoorEventForOfficer(EVENT_ID, CLUB_IDS)).thenReturn(Optional.of(doorEvent()));

        DoorCode code = module.issueDoorCode(EVENT_ID, CLUB_IDS).orElseThrow();

        assertThat(code.token()).isEqualTo(codec.issue(EVENT_ID, NOW));
        assertThat(code.rotatesAt()).isEqualTo(codec.rotatesAt(NOW));
        assertThat(code.title()).isEqualTo("Intro to Climbing");
        assertThat(code.checkInOpen()).isTrue();
        assertThat(code.attendedCount()).isEqualTo(23);
    }

    @Test
    void aCodeIsStillDerivedBeforeTheWindowOpensSoTheOfficerCanSetUpTheRoom() {
        DoorEvent notOpenYet = new DoorEvent(
                EVENT_ID, "Intro to Climbing", NOW.plusSeconds(3600), NOW.plusSeconds(7200),
                NOW.plusSeconds(2700), NOW.plusSeconds(7200), false, 40, 30, 0);
        when(eventModule.findDoorEventForOfficer(EVENT_ID, CLUB_IDS)).thenReturn(Optional.of(notOpenYet));

        DoorCode code = module.issueDoorCode(EVENT_ID, CLUB_IDS).orElseThrow();

        assertThat(code.token()).isNotBlank();
        assertThat(code.checkInOpen()).isFalse();
    }

    @Test
    void anOfficerOfAnotherClubIsToldNothingAtAll() {
        when(eventModule.findDoorEventForOfficer(EVENT_ID, CLUB_IDS)).thenReturn(Optional.empty());

        assertThat(module.issueDoorCode(EVENT_ID, CLUB_IDS)).isEmpty();
    }

    @Test
    void aRotatedCodeIsReportedAsExpiredAndNeverReachesTheSeatLedger() {
        String stale = codec.issue(EVENT_ID, NOW.minusSeconds(180));

        ScanResult result = module.checkIn(EVENT_ID, stale, "student-1");

        assertThat(result).isEqualTo(ScanResult.refused(ScanOutcome.TOKEN_EXPIRED));
        verify(eventModule, never()).recordScannedAttendance(anyString(), anyString());
    }

    @Test
    void aCodeThisServerDidNotSignIsReportedAsInvalidAndNeverReachesTheSeatLedger() {
        String forged = new CheckInTokenCodec("someone-elses-secret").issue(EVENT_ID, NOW);

        ScanResult result = module.checkIn(EVENT_ID, forged, "student-1");

        assertThat(result).isEqualTo(ScanResult.refused(ScanOutcome.TOKEN_INVALID));
        verify(eventModule, never()).recordScannedAttendance(anyString(), anyString());
    }

    @Test
    void aValidCodeForAnotherEventDoesNotCheckAnyoneInHere() {
        String otherDoor = codec.issue("68a1b2c3d4e5f60718293a4c", NOW);

        ScanResult result = module.checkIn(EVENT_ID, otherDoor, "student-1");

        assertThat(result).isEqualTo(ScanResult.refused(ScanOutcome.TOKEN_INVALID));
        verify(eventModule, never()).recordScannedAttendance(anyString(), anyString());
    }

    @Test
    void aVerifiedScanHandsTheEventAndTheStudentToTheModuleThatOwnsTheSeatLedger() {
        when(eventModule.recordScannedAttendance(EVENT_ID, "student-1"))
                .thenReturn(new AttendanceResult(
                        AttendanceOutcome.SUCCESS, "Intro to Climbing", NOW, AttendanceMethod.SCANNED));

        ScanResult result = module.checkIn(EVENT_ID, codec.issue(EVENT_ID, NOW), "student-1");

        assertThat(result)
                .isEqualTo(new ScanResult(
                        ScanOutcome.SUCCESS, "Intro to Climbing", NOW, AttendanceMethod.SCANNED));
    }

    @Test
    void aCodeFromThePreviousWindowStillAdmitsTheStudent() {
        when(eventModule.recordScannedAttendance(EVENT_ID, "student-1"))
                .thenReturn(new AttendanceResult(
                        AttendanceOutcome.SUCCESS, "Intro to Climbing", NOW, AttendanceMethod.SCANNED));

        ScanResult result = module.checkIn(EVENT_ID, codec.issue(EVENT_ID, NOW.minusSeconds(60)), "student-1");

        assertThat(result.outcome()).isEqualTo(ScanOutcome.SUCCESS);
    }

    @ParameterizedTest
    @MethodSource("seatLedgerOutcomes")
    void everySeatLedgerRefusalReachesTheStudentAsItsOwnDoorState(
            AttendanceOutcome written, ScanOutcome scanned) {
        when(eventModule.recordScannedAttendance(EVENT_ID, "student-1"))
                .thenReturn(AttendanceResult.refused(written, "Intro to Climbing"));

        ScanResult result = module.checkIn(EVENT_ID, codec.issue(EVENT_ID, NOW), "student-1");

        assertThat(result.outcome()).isEqualTo(scanned);
        assertThat(result.eventTitle()).isEqualTo("Intro to Climbing");
    }

    private static Stream<Arguments> seatLedgerOutcomes() {
        return Stream.of(
                Arguments.of(AttendanceOutcome.NOT_FOUND, ScanOutcome.NOT_FOUND),
                Arguments.of(AttendanceOutcome.NOT_ON_ROSTER, ScanOutcome.NOT_ON_ROSTER),
                Arguments.of(AttendanceOutcome.ALREADY_CHECKED_IN, ScanOutcome.ALREADY_CHECKED_IN),
                Arguments.of(AttendanceOutcome.CHECK_IN_WINDOW_CLOSED, ScanOutcome.CHECK_IN_WINDOW_CLOSED));
    }

    private static DoorEvent doorEvent() {
        return new DoorEvent(
                EVENT_ID,
                "Intro to Climbing",
                NOW.minusSeconds(600),
                NOW.plusSeconds(3600),
                NOW.minusSeconds(1500),
                NOW.plusSeconds(3600),
                true,
                40,
                30,
                23);
    }

    // Guards the assertion above that a rejected code never reaches event: any() would pass even if the
    // module started calling it with nulls.
    @Test
    void nothingElseOnTheEventModuleIsTouchedByARejectedScan() {
        module.checkIn(EVENT_ID, "not-a-token", "student-1");

        verify(eventModule, never()).recordScannedAttendance(any(), any());
        verify(eventModule, never()).findDoorEventForOfficer(any(), any());
    }
}

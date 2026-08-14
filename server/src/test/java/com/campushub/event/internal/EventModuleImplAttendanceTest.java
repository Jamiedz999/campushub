package com.campushub.event.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.campushub.event.EventModule.AttendanceMethod;
import com.campushub.event.EventModule.AttendanceOutcome;
import com.campushub.event.EventModule.AttendanceResult;
import com.campushub.event.EventModule.AttendanceRoster;
import com.campushub.event.EventModule.AttendanceRosterEntry;
import com.campushub.event.EventModule.DoorEvent;
import com.campushub.event.domain.AttendanceEntry;
import com.campushub.event.domain.EnrolledEntry;
import com.campushub.event.domain.EnrollmentVia;
import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventStatus;
import com.campushub.event.persistence.EventRepository;
import com.campushub.venue.VenueModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

// Correctness lives in the guarded write, which the repository's integration test proves. What is
// proven here is the other half: which of the door's five failure screens a refused write is turned
// into, and that the classification is one follow-up read scoped exactly like the write it explains.
@ExtendWith(MockitoExtension.class)
class EventModuleImplAttendanceTest {

    private static final Instant NOW = Instant.parse("2026-03-20T18:10:00Z");
    private static final Instant STARTS = Instant.parse("2026-03-20T18:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-03-20T20:00:00Z");
    private static final Set<String> CLUB_IDS = Set.of("club-a");

    @Mock
    private EventRepository repository;

    @Mock
    private VenueModule venueModule;

    private EventModuleImpl module;

    @BeforeEach
    void setUp() {
        module = new EventModuleImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC), venueModule);
    }

    @Test
    void aWinningScanReportsTheRecordItJustWroteWithoutASecondRead() {
        Event written = event(List.of(seat("student-1")), List.of(scannedAt(NOW, "student-1")));
        when(repository.recordScannedAttendance("event-1", "student-1", NOW)).thenReturn(Optional.of(written));

        AttendanceResult result = module.recordScannedAttendance("event-1", "student-1");

        assertThat(result)
                .isEqualTo(new AttendanceResult(AttendanceOutcome.SUCCESS, "Title", NOW, AttendanceMethod.SCANNED));
    }

    @Test
    void aSecondScanIsReportedAsAlreadyCheckedInCarryingTheFirstRecordsTime() {
        Instant firstScan = NOW.minusSeconds(300);
        Event event = event(List.of(seat("student-1")), List.of(scannedAt(firstScan, "student-1")));
        when(repository.recordScannedAttendance("event-1", "student-1", NOW)).thenReturn(Optional.empty());
        when(repository.findById("event-1")).thenReturn(Optional.of(event));

        AttendanceResult result = module.recordScannedAttendance("event-1", "student-1");

        assertThat(result)
                .isEqualTo(new AttendanceResult(
                        AttendanceOutcome.ALREADY_CHECKED_IN, "Title", firstScan, AttendanceMethod.SCANNED));
    }

    @Test
    void aStudentWhoHoldsNoSeatIsToldTheyAreNotOnTheRosterRatherThanThatTheDoorIsShut() {
        Event event = event(List.of(seat("someone-else")), List.of());
        when(repository.recordScannedAttendance("event-1", "hopeful", NOW)).thenReturn(Optional.empty());
        when(repository.findById("event-1")).thenReturn(Optional.of(event));

        AttendanceResult result = module.recordScannedAttendance("event-1", "hopeful");

        assertThat(result).isEqualTo(AttendanceResult.refused(AttendanceOutcome.NOT_ON_ROSTER, "Title"));
    }

    @Test
    void anEnrolledStudentOutsideTheWindowIsToldTheDoorIsShut() {
        Event event = event(List.of(seat("student-1")), List.of());
        when(repository.recordScannedAttendance("event-1", "student-1", NOW)).thenReturn(Optional.empty());
        when(repository.findById("event-1")).thenReturn(Optional.of(event));

        AttendanceResult result = module.recordScannedAttendance("event-1", "student-1");

        assertThat(result).isEqualTo(AttendanceResult.refused(AttendanceOutcome.CHECK_IN_WINDOW_CLOSED, "Title"));
    }

    @Test
    void anUnknownEventIsNotFound() {
        when(repository.recordScannedAttendance("event-1", "student-1", NOW)).thenReturn(Optional.empty());
        when(repository.findById("event-1")).thenReturn(Optional.empty());

        assertThat(module.recordScannedAttendance("event-1", "student-1"))
                .isEqualTo(AttendanceResult.refused(AttendanceOutcome.NOT_FOUND, null));
    }

    @Test
    void aDraftEventIsNotFoundToAStudentTheSameWayItIsAbsentFromBrowse() {
        Event draft = event(List.of(seat("student-1")), List.of());
        when(draft.getStatus()).thenReturn(EventStatus.DRAFT);
        when(repository.recordScannedAttendance("event-1", "student-1", NOW)).thenReturn(Optional.empty());
        when(repository.findById("event-1")).thenReturn(Optional.of(draft));

        assertThat(module.recordScannedAttendance("event-1", "student-1"))
                .isEqualTo(AttendanceResult.refused(AttendanceOutcome.NOT_FOUND, null));
    }

    @Test
    void aManualOverrideIsScopedByTheCallersClubGrantsInBothTheWriteAndItsClassification() {
        when(repository.recordManualAttendance("event-1", CLUB_IDS, "student-1", NOW))
                .thenReturn(Optional.empty());
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.empty());

        assertThat(module.recordManualAttendance("event-1", "student-1", CLUB_IDS))
                .isEqualTo(AttendanceResult.refused(AttendanceOutcome.NOT_FOUND, null));
    }

    @Test
    void aWinningOverrideReportsTheManualRecord() {
        Event written = event(
                List.of(seat("student-1")),
                List.of(new AttendanceEntry("student-1", NOW, AttendanceMethod.MANUAL)));
        when(repository.recordManualAttendance("event-1", CLUB_IDS, "student-1", NOW))
                .thenReturn(Optional.of(written));

        assertThat(module.recordManualAttendance("event-1", "student-1", CLUB_IDS))
                .isEqualTo(new AttendanceResult(AttendanceOutcome.SUCCESS, "Title", NOW, AttendanceMethod.MANUAL));
    }

    @Test
    void theDoorScreenSeesTheWindowItsCodeWillBeJudgedAgainst() {
        Event event = event(List.of(seat("student-1")), List.of(scannedAt(NOW, "student-1")));
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(event));

        DoorEvent door = module.findDoorEventForOfficer("event-1", CLUB_IDS).orElseThrow();

        assertThat(door.checkInOpensAt()).isEqualTo(STARTS.minusSeconds(900));
        assertThat(door.checkInClosesAt()).isEqualTo(ENDS);
        assertThat(door.checkInOpen()).isTrue();
        assertThat(door.enrolledCount()).isEqualTo(1);
        assertThat(door.attendedCount()).isEqualTo(1);
        assertThat(door.capacity()).isEqualTo(40);
    }

    @Test
    void theDoorIsShutBeforeTheWindowOpensAndAfterTheEventEnds() {
        Event early = event(List.of(), List.of());
        when(early.getStartsAt()).thenReturn(NOW.plusSeconds(1000));
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(early));
        assertThat(module.findDoorEventForOfficer("event-1", CLUB_IDS).orElseThrow().checkInOpen())
                .isFalse();

        Event finished = event(List.of(), List.of());
        when(finished.getEndsAt()).thenReturn(NOW.minusSeconds(1));
        when(repository.findScopedById("event-2", CLUB_IDS)).thenReturn(Optional.of(finished));
        assertThat(module.findDoorEventForOfficer("event-2", CLUB_IDS).orElseThrow().checkInOpen())
                .isFalse();
    }

    @Test
    void aCancelledEventsDoorIsShutWhateverTheClockSays() {
        Event cancelled = event(List.of(), List.of());
        when(cancelled.getStatus()).thenReturn(EventStatus.CANCELLED);
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(cancelled));

        assertThat(module.findDoorEventForOfficer("event-1", CLUB_IDS).orElseThrow().checkInOpen())
                .isFalse();
    }

    @Test
    void theDoorScreenIsScopedByTheCallersClubGrants() {
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.empty());

        assertThat(module.findDoorEventForOfficer("event-1", CLUB_IDS)).isEmpty();
        assertThat(module.findAttendanceForOfficer("event-1", CLUB_IDS)).isEmpty();
    }

    @Test
    void theRosterIsTheSeatLedgerWithAttendanceAgainstItAndNoOneElse() {
        Instant scannedAt = NOW.minusSeconds(60);
        Event event = event(
                List.of(seat("present-student"), seat("absent-student")),
                List.of(scannedAt(scannedAt, "present-student")));
        when(repository.findScopedById("event-1", CLUB_IDS)).thenReturn(Optional.of(event));

        AttendanceRoster roster = module.findAttendanceForOfficer("event-1", CLUB_IDS).orElseThrow();

        assertThat(roster.enrolledCount()).isEqualTo(2);
        assertThat(roster.attendedCount()).isEqualTo(1);
        assertThat(roster.items())
                .containsExactly(
                        new AttendanceRosterEntry("present-student", scannedAt, AttendanceMethod.SCANNED),
                        new AttendanceRosterEntry("absent-student", null, null));
    }

    private static EnrolledEntry seat(String studentId) {
        return new EnrolledEntry(studentId, EnrollmentVia.DIRECT, STARTS.minusSeconds(86_400), 1L);
    }

    private static AttendanceEntry scannedAt(Instant at, String studentId) {
        return new AttendanceEntry(studentId, at, AttendanceMethod.SCANNED);
    }

    // Event's rich-state constructor is package-private to event.domain on purpose — production code
    // never builds one outside a guarded MongoTemplate write — so a mock stands in for a stored Event,
    // the same way the rest of this module's tests do.
    private static Event event(List<EnrolledEntry> enrolled, List<AttendanceEntry> attendance) {
        // Lenient because one stored Event answers many questions and no single test asks all of them.
        Event event = mock(Event.class, withSettings().strictness(Strictness.LENIENT));
        when(event.getId()).thenReturn("event-1");
        when(event.getTitle()).thenReturn("Title");
        when(event.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(event.getStartsAt()).thenReturn(STARTS);
        when(event.getEndsAt()).thenReturn(ENDS);
        when(event.getCapacity()).thenReturn(40);
        when(event.getEnrolled()).thenReturn(enrolled);
        when(event.getAttendance()).thenReturn(attendance);
        return event;
    }
}

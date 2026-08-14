package com.campushub.event.internal;

import com.campushub.club.ClubModule;
import com.campushub.event.EventModule;
import com.campushub.event.persistence.EventRepository;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.venue.VenueModule;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;

/**
 * The demo scene: a contended Event, a finished one, and a Venue whose day is not empty.
 *
 * <p>Profile-gated exactly like the two demo change units before it, so none of this is ever silently
 * the production data. It is a second change unit rather than an edit to {@code event-demo-data-009}
 * because Mongock records that one as executed by id — editing its body would leave every existing
 * database unchanged.
 *
 * <p>Issue #12 asks for a seed that makes the product legible in thirty seconds. One empty published
 * Event does not: the Waitlist, the attendance split and the Venue timeline are the three things this
 * project is actually about, and all three read as zero on a fresh clone. So this seed produces state
 * that only exists after real use — a full Seat Ledger with someone queued behind it, an Event that has
 * happened and whose attendance is neither perfect nor entirely scanned, and a Venue day with two
 * Events in it (one booking shows a room; two show a timeline).
 *
 * <p>Every write below goes through the same guarded writes the application uses, so the seeded state
 * is reachable state rather than a hand-forged document. The past Event is the one place that needs the
 * repository directly: its Seat Ledger writes have to be told the historical instant they happened at,
 * since every one of those guards is written against a Registration Window and a {@code startsAt} that
 * are now in the past.
 */
@ChangeUnit(id = "event-demo-scene-012", order = "012")
@Profile("development")
public class EventDemoSceneChangeUnit {

    private static final String OFFICER_EMAIL = "officer@demo.campushub";

    // The account the README publishes. It is deliberately the one left queued rather than enrolled on
    // the contended Event: signing in as the demo Student should show what the Waitlist looks like from
    // the inside, which is the half of the registration story a screenshot cannot tell.
    private static final String DEMO_STUDENT_EMAIL = "student@demo.campushub";

    private static final List<String> OTHER_STUDENT_EMAILS =
            List.of("alex.student@demo.campushub", "sam.student@demo.campushub", "priya.officer@demo.campushub");

    @Execution
    public void execution(
            EventModule eventModule,
            EventRepository eventRepository,
            ClubModule clubModule,
            IdentityAccessModule identityAccessModule,
            VenueModule venueModule,
            Clock clock,
            ZoneId campusZone) {
        String clubId = clubModule.createClub("Film Society");
        String officerId = accountId(identityAccessModule, OFFICER_EMAIL);
        clubModule.grantOfficer(clubId, officerId);
        Set<String> officerClubIds = Set.of(clubId);

        String demoStudentId = accountId(identityAccessModule, DEMO_STUDENT_EMAIL);
        List<String> otherStudentIds =
                OTHER_STUDENT_EMAILS.stream().map(email -> accountId(identityAccessModule, email)).toList();

        LocalDate today = LocalDate.ofInstant(clock.instant(), campusZone);
        String venueId = venueModule.createVenue("Lecture Theatre A");

        seedContendedEvent(
                eventModule,
                clubId,
                officerClubIds,
                venueId,
                campusZone,
                today,
                demoStudentId,
                otherStudentIds);
        seedSecondBooking(eventModule, clubId, officerClubIds, venueId, campusZone, today, otherStudentIds);
        seedFinishedEvent(
                eventModule,
                eventRepository,
                clubId,
                officerClubIds,
                campusZone,
                today,
                demoStudentId,
                otherStudentIds);
    }

    /**
     * Full, with the demo Student queued behind it. Capacity is exactly the number of other demo
     * Students, so the Event fills and the account the README publishes is the one that has to queue —
     * and it queues by losing a real {@code takeSeat}, since {@link EventModule#register} is the same
     * call a Student's browser makes. The seed never writes the Waitlist directly.
     */
    private void seedContendedEvent(
            EventModule eventModule,
            String clubId,
            Set<String> officerClubIds,
            String venueId,
            ZoneId campusZone,
            LocalDate today,
            String demoStudentId,
            List<String> otherStudentIds) {
        Instant startsAt = at(today.plusDays(3), LocalTime.of(18, 30), campusZone);
        Instant endsAt = at(today.plusDays(3), LocalTime.of(20, 30), campusZone);
        String eventId = eventModule.createDraft(
                clubId,
                "Sold-Out Screening: Rear Window",
                "A 35mm print, introduced by the Film Society committee. Three seats, and they went "
                        + "in an afternoon — join the queue and you are promoted the moment anyone withdraws.",
                at(today.minusDays(2), LocalTime.of(9, 0), campusZone),
                at(today.plusDays(2), LocalTime.of(23, 0), campusZone),
                startsAt,
                endsAt,
                otherStudentIds.size());
        eventModule.publish(eventId, officerClubIds);

        otherStudentIds.forEach(studentId -> eventModule.register(eventId, studentId));
        eventModule.register(eventId, demoStudentId);

        eventModule.bookSlotAsOfficer(eventId, officerClubIds, venueId, startsAt, endsAt);
    }

    /** Earlier the same day in the same room, so the Venue's day reads as a timeline rather than a fact. */
    private void seedSecondBooking(
            EventModule eventModule,
            String clubId,
            Set<String> officerClubIds,
            String venueId,
            ZoneId campusZone,
            LocalDate today,
            List<String> otherStudentIds) {
        Instant startsAt = at(today.plusDays(3), LocalTime.of(10, 0), campusZone);
        Instant endsAt = at(today.plusDays(3), LocalTime.of(12, 0), campusZone);
        String eventId = eventModule.createDraft(
                clubId,
                "Open Projector Night",
                "Bring a short film, or bring nothing and watch. Seats are not contended here — this "
                        + "one exists to show a room with more than one Event in its day.",
                at(today.minusDays(2), LocalTime.of(9, 0), campusZone),
                at(today.plusDays(2), LocalTime.of(23, 0), campusZone),
                startsAt,
                endsAt,
                30);
        eventModule.publish(eventId, officerClubIds);

        eventModule.register(eventId, otherStudentIds.getFirst());

        eventModule.bookSlotAsOfficer(eventId, officerClubIds, venueId, startsAt, endsAt);
    }

    /**
     * Already over, so the dashboard has something to report — and it reports a history rather than a
     * headcount. This Event filled, queued someone, lost a Student and promoted the queue's head into
     * the Seat they freed, which is the mechanism this whole project is built around and the one thing a
     * dashboard of empty Events cannot show: {@code promotedCount} and {@code everQueuedCount} are read
     * from counters, so a Waitlist that is empty at the end still remembers that it was used.
     *
     * <p>Two of the three enrolled Students then turned up, one of them marked present by the Officer
     * rather than by a scan. Both halves are deliberate: a demo where everybody attended has no
     * attendance rate worth reading, and one where every record is a scan hides the distinction
     * docs/adr/07-define-qr-checkin-and-anti-fraud.md exists to keep visible.
     */
    private void seedFinishedEvent(
            EventModule eventModule,
            EventRepository eventRepository,
            String clubId,
            Set<String> officerClubIds,
            ZoneId campusZone,
            LocalDate today,
            String demoStudentId,
            List<String> otherStudentIds) {
        Instant startsAt = at(today.minusDays(4), LocalTime.of(18, 0), campusZone);
        String eventId = eventModule.createDraft(
                clubId,
                "Intro to Super 8",
                "Loading, shooting and hand-processing a cartridge of Super 8 — the Film Society's "
                        + "termly beginners' session.",
                at(today.minusDays(11), LocalTime.of(9, 0), campusZone),
                at(today.minusDays(5), LocalTime.of(9, 0), campusZone),
                startsAt,
                at(today.minusDays(4), LocalTime.of(20, 0), campusZone),
                3);
        eventModule.publish(eventId, officerClubIds);

        // Every write here is told the instant it happened at, because every Seat Ledger guard is
        // written against a Registration Window and a startsAt that are now in the past. Given the real
        // instant, the real guards run — the Waitlist join below is a genuine losing takeSeat.
        Instant registeredAt = at(today.minusDays(6), LocalTime.of(12, 0), campusZone);
        eventRepository.takeSeat(eventId, demoStudentId, registeredAt);
        eventRepository.takeSeat(eventId, otherStudentIds.get(0), registeredAt);
        eventRepository.takeSeat(eventId, otherStudentIds.get(1), registeredAt);
        eventRepository.joinWaitlist(eventId, otherStudentIds.get(2), registeredAt);

        // The withdrawal and the promotion are one write, so the queue's head is enrolled the moment the
        // Seat is freed — the claim the README leads with, left behind in data a reader can look at.
        eventRepository.withdrawEnrolled(eventId, otherStudentIds.get(1), registeredAt.plus(Duration.ofDays(1)));

        Instant doorsOpen = startsAt.plus(Duration.ofMinutes(6));
        eventRepository.recordScannedAttendance(eventId, demoStudentId, doorsOpen);
        // A dead phone at the door, which is exactly what the manual override is for — and it is the
        // promoted Student's, so the Officer's list and the promotion story meet in one row. The
        // remaining enrolled Student never arrives, so the attendance rate is a number rather than 100%.
        eventRepository.recordManualAttendance(
                eventId, officerClubIds, otherStudentIds.get(2), doorsOpen.plus(Duration.ofMinutes(9)));
    }

    private static String accountId(IdentityAccessModule identityAccessModule, String email) {
        return identityAccessModule
                .findAccountIdByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Demo account " + email + " is missing; it is seeded by identityaccess-demo-data-004."));
    }

    private static Instant at(LocalDate date, LocalTime time, ZoneId campusZone) {
        return date.atTime(time).atZone(campusZone).toInstant();
    }

    @RollbackExecution
    public void rollback() {}
}

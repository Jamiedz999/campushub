package com.campushub.event.internal;

import com.campushub.club.ClubModule;
import com.campushub.event.EventModule;
import com.campushub.event.domain.EventCommandResult;
import com.campushub.event.domain.RegistrationOutcome;
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
import java.util.Set;
import org.springframework.context.annotation.Profile;

// The demo scene: a contended Event, a finished one, and a Venue whose day is not empty.
//
// Profile-gated exactly like the two demo change units before it, so none of this is ever silently the
// production data. It is a second change unit rather than an edit to event-demo-data-009 because
// Mongock records that one as executed by id — editing its body would leave every existing database
// unchanged.
//
// Issue #12 asks for a seed that makes the product legible in thirty seconds. One empty published Event
// does not: the Waitlist, the attendance split and the Venue timeline are the three things this project
// is actually about, and all three read as zero on a fresh clone. So this seed produces state that only
// exists after real use — a full Seat Ledger with someone queued behind it, an Event that has happened
// and whose attendance is neither perfect nor entirely scanned, and a Venue day holding two Slots (one
// Slot shows a room; two show a timeline).
//
// Every write below goes through the same guarded writes the application uses, so the seeded state is
// reachable state rather than a hand-forged document — and every one of them is checked, because a
// guard that refuses here would otherwise seed nothing while Mongock still recorded this change unit as
// executed. The finished Event is the one place that needs the repository directly: its Seat Ledger
// writes have to be told the historical instant they happened at, since every one of those guards is
// written against a Registration Window and a startsAt that are now in the past.
@ChangeUnit(id = "event-demo-scene-012", order = "012")
@Profile({"development", "demo"})
public class EventDemoSceneChangeUnit {

    private static final String OFFICER_EMAIL = "officer@demo.campushub";

    // The account the README publishes. It is deliberately the one left queued rather than enrolled on
    // the contended Event: signing in as the demo Student should show what the Waitlist looks like from
    // the inside, which is the half of the registration story a screenshot cannot tell.
    private static final String DEMO_STUDENT_EMAIL = "student@demo.campushub";

    private static final String ALEX_EMAIL = "alex.student@demo.campushub";
    private static final String SAM_EMAIL = "sam.student@demo.campushub";
    private static final String PRIYA_EMAIL = "priya.officer@demo.campushub";

    private static final LocalTime MORNING = LocalTime.of(10, 0);
    private static final LocalTime EVENING = LocalTime.of(18, 30);

    /**
     * Everything the three scenes below are written against: the Club they belong to, the room they book
     * and the campus day they are placed relative to. It travels as one value because it is one setting —
     * passing the eight fields separately made every scene's signature a copy of the others'.
     */
    private record Scene(
            EventModule eventModule,
            EventRepository eventRepository,
            VenueModule venueModule,
            String clubId,
            Set<String> officerClubIds,
            String venueId,
            ZoneId campusZone,
            LocalDate today,
            String demoStudentId) {

        Instant at(LocalDate date, LocalTime time) {
            return date.atTime(time).atZone(campusZone).toInstant();
        }
    }

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
        clubModule.grantOfficer(clubId, accountId(identityAccessModule, OFFICER_EMAIL));

        Scene scene = new Scene(
                eventModule,
                eventRepository,
                venueModule,
                clubId,
                Set.of(clubId),
                venueModule.createVenue("Lecture Theatre A"),
                campusZone,
                LocalDate.ofInstant(clock.instant(), campusZone),
                accountId(identityAccessModule, DEMO_STUDENT_EMAIL));

        seedContendedEvent(scene, identityAccessModule);
        seedSecondSlotInTheSameRoom(scene, identityAccessModule);
        seedFinishedEvent(scene, identityAccessModule);
    }

    /**
     * Full, with the demo Student queued behind it. Capacity is three and exactly three other demo
     * Students take those Seats, so the account the README publishes is the one that has to queue — and
     * it queues by losing a real {@code takeSeat}, since {@link EventModule#register} is the same call a
     * Student's browser makes. The seed never writes the Waitlist directly.
     */
    private void seedContendedEvent(Scene scene, IdentityAccessModule identityAccessModule) {
        String eventId = publishUpcomingEvent(
                scene,
                "Sold-Out Screening: Rear Window",
                "A 35mm print, introduced by the Film Society committee. Three seats, and they went "
                        + "in an afternoon — join the queue and you are promoted the moment anyone withdraws.",
                EVENING,
                3);

        for (String email : new String[] {ALEX_EMAIL, SAM_EMAIL, PRIYA_EMAIL}) {
            requireRegistered(scene.eventModule().register(eventId, accountId(identityAccessModule, email)));
        }
        requireRegistered(scene.eventModule().register(eventId, scene.demoStudentId()));
    }

    /** Earlier the same day in the same room, so the Venue's day reads as a timeline rather than a fact. */
    private void seedSecondSlotInTheSameRoom(Scene scene, IdentityAccessModule identityAccessModule) {
        String eventId = publishUpcomingEvent(
                scene,
                "Open Projector Night",
                "Bring a short film, or bring nothing and watch. Seats are not contended here — this "
                        + "one exists to show a room holding more than one Event in its day.",
                MORNING,
                30);

        requireRegistered(scene.eventModule().register(eventId, accountId(identityAccessModule, ALEX_EMAIL)));
    }

    /**
     * Publishes a two-hour Event three days out, open for registration now, and puts it in the room. Both
     * upcoming scenes are this shape and differ only in what happens to their Seats afterwards.
     */
    private String publishUpcomingEvent(
            Scene scene, String title, String description, LocalTime startTime, int capacity) {
        LocalDate day = scene.today().plusDays(3);
        Instant startsAt = scene.at(day, startTime);
        Instant endsAt = scene.at(day, startTime.plusHours(2));

        String eventId = scene.eventModule()
                .createDraft(
                        scene.clubId(),
                        title,
                        description,
                        scene.at(scene.today().minusDays(2), LocalTime.of(9, 0)),
                        scene.at(scene.today().plusDays(2), LocalTime.of(23, 0)),
                        startsAt,
                        endsAt,
                        capacity);
        require(
                scene.eventModule().publish(eventId, scene.officerClubIds()) == EventCommandResult.SUCCESS,
                "publishing the demo Event " + title);
        require(
                scene.eventModule()
                                .bookSlotAsOfficer(eventId, scene.officerClubIds(), scene.venueId(), startsAt, endsAt)
                        == EventModule.SlotCommandOutcome.SUCCESS,
                "putting the demo Event " + title + " in its room");
        return eventId;
    }

    /**
     * Already over, so the dashboard has something to report — and it reports a history rather than a
     * headcount. This Event filled, queued someone, lost a Student and promoted the queue's head into the
     * Seat they freed, which is the mechanism this whole project is built around and the one thing a
     * dashboard of empty Events cannot show: {@code promotedCount} and {@code everQueuedCount} are read
     * from counters, so a Waitlist that is empty at the end still remembers that it was used.
     *
     * <p>Two of the three enrolled Students then turned up, one of them marked present by the Officer
     * rather than by a scan. Both halves are deliberate: a demo where everybody attended has no
     * attendance rate worth reading, and one where every record is a scan hides the distinction
     * docs/adr/07-define-qr-checkin-and-anti-fraud.md exists to keep visible.
     */
    private void seedFinishedEvent(Scene scene, IdentityAccessModule identityAccessModule) {
        LocalDate day = scene.today().minusDays(4);
        Instant startsAt = scene.at(day, LocalTime.of(18, 0));
        String eventId = scene.eventModule()
                .createDraft(
                        scene.clubId(),
                        "Intro to Super 8",
                        "Loading, shooting and hand-processing a cartridge of Super 8 — the Film "
                                + "Society's termly beginners' session.",
                        scene.at(scene.today().minusDays(11), LocalTime.of(9, 0)),
                        scene.at(scene.today().minusDays(5), LocalTime.of(9, 0)),
                        startsAt,
                        scene.at(day, LocalTime.of(20, 0)),
                        3);
        require(
                scene.eventModule().publish(eventId, scene.officerClubIds()) == EventCommandResult.SUCCESS,
                "publishing the finished demo Event");

        String whoStayed = accountId(identityAccessModule, ALEX_EMAIL);
        String whoWithdrew = accountId(identityAccessModule, SAM_EMAIL);
        String whoWasPromoted = accountId(identityAccessModule, PRIYA_EMAIL);

        // Every write here is told the instant it happened at, because every Seat Ledger guard is written
        // against a Registration Window and a startsAt that are now in the past. Given the real instant,
        // the real guards run — the Waitlist join below is a genuine losing takeSeat.
        EventRepository repository = scene.eventRepository();
        Instant registeredAt = scene.at(scene.today().minusDays(6), LocalTime.of(12, 0));
        require(repository.takeSeat(eventId, scene.demoStudentId(), registeredAt), "seating the demo Student");
        require(repository.takeSeat(eventId, whoStayed, registeredAt), "seating the Student who stays");
        require(repository.takeSeat(eventId, whoWithdrew, registeredAt), "seating the Student who withdraws");
        require(repository.joinWaitlist(eventId, whoWasPromoted, registeredAt), "queueing the Student who is promoted");

        // The withdrawal and the promotion are one write, so the queue's head is enrolled the moment the
        // Seat is freed — the claim the README leads with, left behind in data a reader can look at.
        require(
                repository.withdrawEnrolled(eventId, whoWithdrew, registeredAt.plus(Duration.ofDays(1))),
                "freeing the Seat that promotes the queue's head");

        Instant doorsOpen = startsAt.plus(Duration.ofMinutes(6));
        require(
                repository.recordScannedAttendance(eventId, scene.demoStudentId(), doorsOpen).isPresent(),
                "recording the demo Student's scan");
        // A dead phone at the door, which is exactly what the manual override is for — and it is the
        // promoted Student's, so the Officer's list and the promotion story meet in one row. The Student
        // who stayed never arrives, so the attendance rate is a number rather than a flat 100%.
        require(
                repository
                        .recordManualAttendance(
                                eventId,
                                scene.officerClubIds(),
                                whoWasPromoted,
                                doorsOpen.plus(Duration.ofMinutes(9)))
                        .isPresent(),
                "recording the Officer's manual override");
    }

    private static String accountId(IdentityAccessModule identityAccessModule, String email) {
        return identityAccessModule
                .findAccountIdByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Demo account " + email + " is missing; it is seeded by identityaccess-demo-data-004."));
    }

    private static void requireRegistered(RegistrationOutcome outcome) {
        require(outcome == RegistrationOutcome.SUCCESS, "registering a demo Student: " + outcome);
    }

    /**
     * A refused guard leaves this change unit half-applied and recorded as executed, which is the one
     * failure a seed can have that nobody notices. So every write above is checked, and a refusal stops
     * the migration instead of shipping a scene that quietly is not one.
     */
    private static void require(boolean applied, String what) {
        if (!applied) {
            throw new IllegalStateException("The demo scene could not be seeded — " + what + " was refused.");
        }
    }

    @RollbackExecution
    public void rollback() {}
}

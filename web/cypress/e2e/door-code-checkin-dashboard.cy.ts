import {
  OFFICER,
  STUDENT,
  api,
  minutesFromNow,
  publishEvent,
  signIn,
  signInThroughTheUi,
  uniqueRun,
} from "../support/campus";

/**
 * Journey 3 — the door screen, a check-in, and the dashboard counting it.
 *
 * **The camera is the one link this journey does not cover.** A headless browser cannot read a QR
 * code, so where a Student would point a phone at the screen, this takes the same code the screen is
 * showing from the endpoint the screen got it from and posts it to the check-in endpoint. Everything
 * on either side of that hop is real: the code is derived by the server and is worthless a minute
 * later, and who checked in still comes from the session, never from the code. Named here rather than
 * left to look covered — see docs/planning/13-set-core-boundary-and-sprints.md and
 * docs/adr/07-define-qr-checkin-and-anti-fraud.md.
 */
describe("the door code, the check-in and the dashboard", () => {
  it("admits a Student at the door and counts them on the Officer's dashboard", () => {
    const run = uniqueRun();
    const title = `Door journey ${run}`;

    // Starting in ten minutes: check-in opens fifteen minutes before an Event starts, so the door is
    // already open, while startsAt is still ahead — which is what leaves the Seat Ledger unfrozen long
    // enough for the Student to hold a Seat at all.
    const times = {
      registrationOpensAt: minutesFromNow(-60),
      registrationClosesAt: minutesFromNow(5),
      startsAt: minutesFromNow(10),
      endsAt: minutesFromNow(70),
    };

    let eventId = "";

    signInThroughTheUi(OFFICER);
    publishEvent({ title, description: `At the door. ${run}`, capacity: 1, times }).then((id) => {
      eventId = id;
    });

    cy.then(() => {
      // Registration through the API rather than the screens: journey 1 is where registering is the
      // thing being shown, and here it is only how someone comes to be on the Roster.
      signIn(STUDENT);
      api("POST", `/api/events/${eventId}/registration`, { answers: {} });

      // The Officer opens the door screen. The code is up, so the window is open, and the Roster
      // already knows who may come in and that nobody has yet.
      signInThroughTheUi(OFFICER);
      cy.visit(`/officer/events/${eventId}/door`);
      cy.contains("h1", `Door · ${title}`).should("be.visible");
      cy.get("section[aria-label='Door code'] svg").should("exist");
      cy.get("ul[aria-label='Enrolled students']")
        .should("contain", STUDENT.displayName)
        .and("contain", "Not in");

      // The code the screen is showing, taken from where the screen takes it — see the note above the
      // describe: this is the camera's hop, and the only one the journey stands in for.
      api<{ token: string }>("GET", `/api/events/${eventId}/door-code`).then((response) => {
        const token = response.body.token;

        // The Student's own check-in screen posts it, as themselves, from their own session.
        signInThroughTheUi(STUDENT);
        cy.visit(`/checkin/${eventId}?token=${encodeURIComponent(token)}`);
        cy.contains("Checked in").should("be.visible");
        cy.contains(title).should("be.visible");
      });

      // The Officer's screen says how they got in, not merely that they did.
      signInThroughTheUi(OFFICER);
      cy.visit(`/officer/events/${eventId}/door`);
      cy.get("ul[aria-label='Enrolled students']").should("contain", "Scanned");

      // The dashboard counts finished Events — Published, and already over — so the Event has to be
      // over before it can appear on one. The Officer moves it into the past with the same PATCH any
      // Officer may use while an Event has not started. Nothing about the Seat Ledger or the
      // attendance record is touched: the only thing that changes is that the Event is now behind us,
      // which waiting an hour would also have achieved. See
      // docs/adr/09-define-attendance-dashboard.md.
      api("PATCH", `/api/events/${eventId}`, {
        registrationClosesAt: minutesFromNow(-30),
        startsAt: minutesFromNow(-20),
        endsAt: minutesFromNow(-10),
      });

      // One Seat, one person through the door and nobody missing — read off the Event's own row, in
      // the columns its header names: Event, Club, Enrolled, Attended, Fill, Attendance, No-show.
      cy.visit("/dashboard");
      // Longer than anything else here waits, and for a reason the app states: the dashboard route is
      // loaded on demand because ECharts is by far the largest thing this app ships (see the comment
      // on the route in web/src/app/router.tsx). Fetching and parsing that chunk is the one step in
      // these journeys that is slow by design rather than by accident.
      cy.contains("h1", "Attendance for your Clubs", { timeout: 30_000 }).should("be.visible");
      cy.get("section[aria-label='Every Event']")
        .contains("tr", title)
        .within(() => {
          cy.get("td").eq(2).should("have.text", "1 / 1");
          cy.get("td").eq(3).should("have.text", "1");
          cy.get("td").eq(5).should("have.text", "100%");
          cy.get("td").eq(6).should("have.text", "0%");
        });
    });
  });
});

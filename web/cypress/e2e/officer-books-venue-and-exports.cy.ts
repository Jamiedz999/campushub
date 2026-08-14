import {
  ADMIN,
  ALEX,
  OFFICER,
  api,
  askOneQuestion,
  createVenue,
  middayHour,
  minutesFromNow,
  publishEvent,
  signIn,
  signInThroughTheUi,
  uniqueRun,
} from "../support/campus";

/**
 * Journey 2 — the Club Officer publishes, books a Venue, is refused a second claim on it, and takes
 * the answers away as a file.
 *
 * The refusal is the point of the middle act. Two Events wanting the same Venue at the same time is
 * the other contended write in this system, and the only honest way to show it is refused is to ask
 * for it on the screen and read what the screen says back.
 */
describe("the Officer publishes, books a Venue and exports the answers", () => {
  it("refuses the second claim on one Venue and exports what the Students answered", () => {
    const run = uniqueRun();
    const venueName = `Lecture Hall ${run}`;
    const bookedTitle = `Venue booked ${run}`;
    const clashingTitle = `Venue clash ${run}`;
    const question = "Which module are you taking?";
    const answer = "Photographic Practice";

    // The identical hour for both Events, which is what makes the second booking a collision rather
    // than a coincidence.
    const times = {
      registrationOpensAt: minutesFromNow(-60),
      registrationClosesAt: minutesFromNow(24 * 60),
      ...middayHour(2),
    };

    let bookedEventId = "";
    let clashingEventId = "";

    // Venues are the University Admin's records and Core gives them no screen, so this one is recorded
    // over the API by the only role entitled to record it.
    signIn(ADMIN);
    createVenue(venueName);

    signInThroughTheUi(OFFICER);
    publishEvent({
      title: bookedTitle,
      description: `Wants the Venue. ${run}`,
      capacity: 5,
      times,
    }).then((eventId) => {
      bookedEventId = eventId;
      askOneQuestion(eventId, question);
    });
    publishEvent({
      title: clashingTitle,
      description: `Wants the same Venue. ${run}`,
      capacity: 5,
      times,
    }).then((eventId) => {
      clashingEventId = eventId;
    });

    cy.then(() => {
      // Someone has to have answered something for there to be an export worth downloading, and a
      // typed answer is the only kind this export can honestly claim to carry.
      signInThroughTheUi(ALEX);
      cy.visit(`/events/${bookedEventId}`);
      cy.contains("label", question).find("input").type(answer);
      cy.contains("button", "Register").click();
      cy.contains("registered for this Event").should("be.visible");

      signInThroughTheUi(OFFICER);

      // The Venue is free for that hour, and the Officer takes it.
      cy.visit(`/officer/events/${bookedEventId}/venue`);
      cy.contains("label", "Venue").find("select").select(venueName);
      cy.contains("li", "No bookings yet.").should("be.visible");
      cy.contains("button", "Book this venue").click();
      cy.contains("Venue booked.").should("be.visible");

      // The second Event asks for the same Venue and the same hour. The timeline shows the Slot is
      // taken before the click, and the server — not the screen — is what refuses the write.
      cy.visit(`/officer/events/${clashingEventId}/venue`);
      cy.contains("label", "Venue").find("select").select(venueName);
      cy.contains("ol", `Event ${bookedEventId}`).should("be.visible");
      cy.contains("button", "Book this venue").click();
      cy.contains("That slot was taken moments ago").should("be.visible");
      cy.contains("Venue booked.").should("not.exist");

      // And the refused Event still holds no Venue, which is what "your Event has not changed" has to
      // mean if the refusal is worth anything.
      api<{ venueId: string | null }>("GET", `/api/events/${clashingEventId}`).then((response) => {
        expect(response.body.venueId).to.equal(null);
      });

      // The answers, on the screen and then as the file the Officer actually leaves with.
      cy.visit(`/officer/events/${bookedEventId}/registration-answers`);
      cy.contains("td", ALEX.displayName).should("be.visible");
      cy.contains("td", answer).should("be.visible");
      cy.contains("a", "Download CSV").click();
      cy.readFile(`cypress/downloads/event-${bookedEventId}-registration-answers.csv`, {
        timeout: 20_000,
      }).should((csv: string) => {
        expect(csv).to.contain(question);
        expect(csv).to.contain(ALEX.displayName);
        expect(csv).to.contain(answer);
      });
    });
  });
});

import { ALEX, OFFICER, SAM, minutesFromNow, publishEvent, signInThroughTheUi } from "../support/campus";

/**
 * Journey 1 — register → Waitlist → Promotion.
 *
 * The Seat Ledger's whole argument, driven by three people through the screens they would really use:
 * one Seat, two Students who want it, and the queue deciding who gets it when the first one leaves.
 * Nothing here reads the answer out of the API — every claim is what a person is shown.
 */
describe("register, then the Waitlist, then Promotion", () => {
  it("hands the freed Seat to the Student who queued for it", () => {
    // Unique per run, which is what lets this spec run twice in a row against one long-lived stack.
    // It is also one $text term, so the search box below can find this Event and only this Event.
    const run = `journeyrun${Date.now()}`;
    const title = `Last Seat ${run}`;
    const question = "What are you hoping to learn?";

    signInThroughTheUi(OFFICER);
    publishEvent({
      title,
      description: `A one-Seat Event. ${run}`,
      capacity: 1,
      times: {
        registrationOpensAt: minutesFromNow(-60),
        registrationClosesAt: minutesFromNow(120),
        startsAt: minutesFromNow(180),
        endsAt: minutesFromNow(240),
      },
    }).then((eventId) => {
      // The Officer builds the custom form on the console, because "a Student fills a custom form"
      // is only worth anything if a person put the question there.
      cy.visit(`/officer/events/${eventId}/registration-form`);
      cy.contains("label", "Field type").find("select").select("Short text");
      cy.contains("button", "Add field").click();
      cy.get("fieldset[aria-label='Field 1']").within(() => {
        cy.contains("label", "Label").find("input").clear().type(question);
        cy.contains("label", "Required").find("input").check();
      });
      cy.contains("button", "Save registration form").click();
      cy.contains("Registration form saved.").should("be.visible");

      // Alex finds the Event the way a Student would, answers the question and takes the only Seat.
      signInThroughTheUi(ALEX);
      cy.visit("/events");
      cy.get("input[aria-label='Search events']").type(`${run}{enter}`);
      cy.contains("a", title).click();
      cy.location("pathname").should("equal", `/events/${eventId}`);
      cy.contains("1 of 1 seats left").should("be.visible");
      cy.contains("label", question).find("input").type("How to develop film by hand");
      cy.contains("button", "Register").click();
      cy.contains("registered for this Event").should("be.visible");

      // Sam arrives to a full Event. The same form, a different button, and a place in the queue.
      signInThroughTheUi(SAM);
      cy.visit(`/events/${eventId}`);
      cy.contains("This Event is full").should("be.visible");
      cy.contains("label", question).find("input").type("Whether the darkroom smells");
      cy.contains("button", "Join the Waitlist").click();
      cy.contains("number 1 on the Waitlist").should("be.visible");

      // Alex withdraws. The Seat does not come back to Alex — the Event is full again the instant the
      // page re-reads it, because the queue was served before Alex's own screen finished refreshing.
      signInThroughTheUi(ALEX);
      cy.visit(`/events/${eventId}`);
      cy.contains("button", "Withdraw from Event").click();
      cy.contains("button", "Join the Waitlist").should("be.visible");
      cy.contains("registered for this Event").should("not.exist");

      // Sam was promoted without being asked for anything, and is told which way they got in.
      signInThroughTheUi(SAM);
      cy.visit(`/events/${eventId}`);
      cy.contains("You were on the Waitlist").should("be.visible");
      cy.visit("/events/mine");
      cy.contains("li", title).should("contain", "You were on the Waitlist");
    });
  });
});

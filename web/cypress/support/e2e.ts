/**
 * Loaded before every spec file.
 *
 * The one thing every journey shares is where it starts: signed out. These run against a long-lived
 * composed stack that outlives any single spec, and a journey that inherited whoever the previous one
 * left signed in would pass or fail for a reason its own file does not show.
 *
 * Nothing else is global. There is no seeding hook and no database reset — each journey creates the
 * Event it acts on, which is also what lets the whole suite run twice in a row against one stack.
 */
beforeEach(() => {
  cy.clearAllCookies();
});

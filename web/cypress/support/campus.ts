/**
 * The vocabulary the three journeys share: who the demo accounts are, how to become one of them, and
 * the handful of setup calls Core gives no screen for.
 *
 * Everything here talks to the same server the journeys drive. There is no stub, no fixture and no
 * write straight into MongoDB — a helper that reached past the API would be testing a database, not a
 * stack. Where a helper does over HTTP what a person would do on a screen, it is because **Core has no
 * such screen** (Event authoring and Venue records are both API-only), and it is never a step the
 * journey it serves is there to prove.
 */

export interface Account {
  readonly email: string;
  readonly password: string;
  readonly displayName: string;
}

// The published demo credentials from the README, seeded by the `development` Spring profile that
// .env.example activates. There is no self-service sign-up, so these are the only way in.
const PASSWORD = "123456";

export const ADMIN: Account = {
  email: "admin@demo.campushub",
  password: PASSWORD,
  displayName: "University Admin",
};

export const OFFICER: Account = {
  email: "officer@demo.campushub",
  password: PASSWORD,
  displayName: "Demo Officer",
};

export const STUDENT: Account = {
  email: "student@demo.campushub",
  password: PASSWORD,
  displayName: "Demo Student",
};

export const ALEX: Account = {
  email: "alex.student@demo.campushub",
  password: PASSWORD,
  displayName: "Alex Student",
};

export const SAM: Account = {
  email: "sam.student@demo.campushub",
  password: PASSWORD,
  displayName: "Sam Student",
};

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

/**
 * A request carrying the session cookie Cypress already holds and the CSRF header the server requires
 * — the same pair the app's own axios instance sends (`web/src/lib/httpClient.ts`).
 *
 * The unauthenticated GET first is what guarantees the cookie is there to read: Spring writes
 * XSRF-TOKEN on every response, and it regenerates the token on sign-in, so it is read fresh for each
 * request rather than captured once at the top of a spec.
 */
function withCsrf<T>(options: Partial<Cypress.RequestOptions>): Cypress.Chainable<Cypress.Response<T>> {
  return cy
    .request({ method: "GET", url: "/api/system" })
    .then(() => cy.getCookie(CSRF_COOKIE))
    .then((cookie) =>
      cy.request<T>({ ...options, headers: { [CSRF_HEADER]: cookie?.value ?? "" } }),
    );
}

export function api<T = unknown>(
  method: Method,
  url: string,
  body?: Cypress.RequestBody,
): Cypress.Chainable<Cypress.Response<T>> {
  return withCsrf<T>({ method, url, body });
}

/**
 * Becomes this account without going through the sign-in screen. Used where a journey needs a second
 * or third person on stage — an Officer setting up before the doors open, an Admin recording a Venue —
 * and the act of signing in is not what the journey is showing.
 *
 * Clearing the cookies first is the switch: sessions here are cookie-backed, so the previous person is
 * still signed in until their cookie is gone.
 */
export function signIn(account: Account): void {
  cy.clearAllCookies();
  withCsrf({
    method: "POST",
    url: "/api/auth/login",
    // Spring's form login reads the credentials as url-encoded parameters, not as JSON.
    form: true,
    body: { email: account.email, password: account.password },
  });
}

/** The same, typed into the real form, for the person whose journey it is. */
export function signInThroughTheUi(account: Account): void {
  cy.clearAllCookies();
  cy.visit("/sign-in");
  cy.contains("label", "Email").find("input").type(account.email);
  cy.contains("label", "Password").find("input").type(account.password, { log: false });
  cy.contains("button", "Sign in").click();
  cy.location("pathname").should("equal", "/");
}

interface CurrentActor {
  accountId: string;
  displayName: string;
  officerClubIds: string[];
}

/** The Club the signed-in Officer will author in. Fails loudly rather than silently authoring nowhere. */
function officerClubId(): Cypress.Chainable<string> {
  return api<CurrentActor>("GET", "/api/auth/me").then((response) => {
    const clubId = response.body.officerClubIds[0];
    expect(clubId, "the signed-in account holds a Club Officer grant").to.be.a("string");
    return String(clubId);
  });
}

export interface EventTimes {
  registrationOpensAt: string;
  registrationClosesAt: string;
  startsAt: string;
  endsAt: string;
}

/**
 * A token unique to this run, and one `$text` search term.
 *
 * Every journey puts it in the titles it creates. That is what lets the same journey run twice in a
 * row against one long-lived stack without meeting what its own last run left behind, and what lets
 * the browse screen's search box find one Event rather than a family of them. One word with no
 * punctuation in it, because MongoDB's text index splits on everything else.
 */
export function uniqueRun(): string {
  return `journeyrun${Date.now()}`;
}

/** An instant relative to now, as the API's ISO-8601 UTC. Negative goes backwards. */
export function minutesFromNow(minutes: number): string {
  return new Date(Date.now() + minutes * 60_000).toISOString();
}

/**
 * One hour in the middle of a day some days from now.
 *
 * For the journey that books a Venue. A Slot may cross neither campus midnight nor the daylight-saving
 * transition hour (docs/adr/06-define-venue-slot-booking.md), and "two days and an hour from now"
 * walks into the first of those whenever CI happens to start late in the evening — a one-run-in-24
 * failure with the wrong reason attached. Noon UTC is 12:00 or 13:00 in Europe/Dublin, comfortably
 * inside a campus day either way.
 */
export function middayHour(daysFromNow: number): { startsAt: string; endsAt: string } {
  const start = new Date();
  start.setUTCDate(start.getUTCDate() + daysFromNow);
  start.setUTCHours(12, 0, 0, 0);
  return {
    startsAt: start.toISOString(),
    endsAt: new Date(start.getTime() + 60 * 60_000).toISOString(),
  };
}

export interface NewEvent {
  title: string;
  description: string;
  capacity: number;
  times: EventTimes;
}

/**
 * Drafts an Event in the signed-in Officer's Club and publishes it, both over the real API with the
 * Officer's own session and both scoped by their own grants.
 *
 * Core has no authoring screen — an Officer creates and publishes over HTTP — so this is not a
 * shortcut past a screen, it is the only door there is. Issue #18 adds journeys, not features.
 */
export function publishEvent(event: NewEvent): Cypress.Chainable<string> {
  return officerClubId()
    .then((clubId) =>
      api<{ id: string }>("POST", `/api/clubs/${clubId}/events`, {
        title: event.title,
        description: event.description,
        capacity: event.capacity,
        ...event.times,
      }),
    )
    .then((response) => {
      const eventId = response.body.id;
      return api("POST", `/api/events/${eventId}/publication`).then(() => eventId);
    });
}

/**
 * A Venue record, which only a University Admin may create and which Core gives no screen for. The
 * name is how the journey picks it out again — the Venue console names Venues, it does not identify
 * them — so nothing here needs its id.
 *
 * A run-named Venue is left behind, because Core has no way to delete one. CI composes a fresh stack
 * per run so nothing accumulates there; against a stack kept alive for a hundred-odd local runs the
 * newest Venue falls off the console's first page of a hundred, sorted by name, and the journey fails
 * loudly on the missing option rather than passing on the wrong Venue.
 */
export function createVenue(name: string): void {
  api("POST", "/api/venues", { name });
}

/** A 24-character hexadecimal field id, which is what the server accepts — see the form builder's own. */
function newFieldId(): string {
  return Array.from({ length: 24 }, () => Math.floor(Math.random() * 16).toString(16)).join("");
}

/**
 * One required short-text question on an Event's registration form.
 *
 * The form builder screen exists and journey 1 drives it. This is for the journeys that need a form to
 * be *there* without the building of it being what they are showing.
 */
export function askOneQuestion(eventId: string, label: string): void {
  api("PUT", `/api/events/${eventId}/registration-form`, {
    fields: [
      {
        fieldId: newFieldId(),
        type: "SHORT_TEXT",
        label,
        helpText: "",
        required: true,
        maxLength: 100,
      },
    ],
  });
}

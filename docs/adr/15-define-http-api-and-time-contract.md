# Define the HTTP API and time contract

Type: grilling
Status: resolved
Blocked by: 12

## Question

What shape do identifiers, errors, collection responses and time take across the whole API, so that every Issue does not invent its own?

## Answer

### Why this is one decision rather than twelve

Every implementation Issue builds REST endpoints. None of the resolved decisions says what an identifier looks like in a URL, what a refused registration returns, or which timezone converts an instant into a calendar date. Left unstated, each Issue answers those questions locally and answers them differently — and the answers are not independently reversible, because the frontend, the tests and the OpenAPI document all encode whichever answer arrived first.

This ADR exists because the cost of the drift is paid once per Issue and the cost of the decision is paid once.

### Identifiers

**Every identifier in the API is the 24-character hexadecimal string form of a MongoDB `ObjectId`.**

It is the identifier the database already generates, it is URL-safe without encoding, it needs no second unique index, and it carries a creation timestamp that makes "newest first" free. Rejected: a separate UUID field, which is a second identity for every document and a second index to maintain; and exposing the BSON `ObjectId` type across the wire, which forces every client to know a MongoDB type.

Identifiers are **opaque strings to the frontend**. No client parses one, sorts by one, or derives a timestamp from one.

### Errors

**Every error response is `application/problem+json`, per RFC 9457**, carrying `type`, `title`, `status`, `detail` and `instance` — plus one extension member, **`code`**, which is a stable machine-readable string.

`detail` is a human sentence and may be reworded freely. **`code` is the contract.** The frontend switches on `code` and never on `detail` or on the HTTP status alone, because the failures that matter here are several distinct outcomes sharing one status: a registration refused because the Event is full, because the Student is already enrolled, because the Registration Window has closed and because the Event is Cancelled are all `409`, and the Student must be told which.

The codes each failure taxonomy owes:

| Area | Codes |
|---|---|
| Seat Ledger | `EVENT_FULL`, `ALREADY_ENROLLED`, `ALREADY_WAITLISTED`, `REGISTRATION_NOT_OPEN`, `REGISTRATION_CLOSED`, `EVENT_CANCELLED`, `EVENT_STARTED` |
| Registration form | `FORM_LOCKED`, `FORM_VALIDATION_FAILED` (with a per-`fieldId` breakdown in an extension member), `UNDEFINED_OPTION` |
| Venue and Slot | `SLOT_TAKEN`, `SLOT_CROSSES_MIDNIGHT`, `SLOT_IN_DST_TRANSITION`, `SLOT_ALREADY_STARTED` |
| Check-in | `TOKEN_INVALID`, `TOKEN_EXPIRED`, `NOT_ON_ROSTER`, `ALREADY_CHECKED_IN`, `CHECK_IN_WINDOW_CLOSED` |
| Authorization | `NOT_FOUND` only — see below |

**Authorization failures are `404` with `code: NOT_FOUND`, never `403`.** [The authorization decision](08-define-roles-and-resource-authorization.md) enforces ownership by scoping the query, so a Club Officer asking for another Club's Event genuinely finds nothing. Returning `403` would require loading the resource first to discover who owns it, which is exactly the load-then-check pattern that decision rejected. The status code follows the enforcement mechanism rather than being chosen for expressiveness.

### Collections

**Every collection response is the same envelope**: `{ items, page, size, total }`, zero-indexed `page`, `size` defaulting to 20 and capped at 100. Requests carry `?page=&size=`.

An envelope rather than bare arrays with `Link` headers, because the frontend needs `total` to render a count and TanStack Query caches a body more naturally than headers. The cap exists so that no caller can turn a paged endpoint into a full export.

### Time

**Instants are instants. Calendar values are computed in one campus timezone.**

- Every stored moment — `registrationOpensAt`, `registrationClosesAt`, `startsAt`, `endsAt`, `at` on an enrolled or attendance entry — is a UTC instant. The API serialises them as ISO-8601 with an explicit offset and accepts the same.
- **The campus timezone is `Europe/Dublin`**, held as one configured constant and injected wherever a calendar value is derived. It is never hardcoded at a call site and never read from a request.
- **`now` always comes from the injected `Clock`**, as the technical baseline already requires. A client never sends a time that the server treats as authoritative.

The only place a calendar value is derived is the Venue-Day document, which projects an instant onto `(date, minuteOfDay)` so that [a day's bookings live in one document](06-define-venue-slot-booking.md). That projection is where the timezone becomes load-bearing, and where it is lossy.

### The two days a year the Venue-Day projection is lossy

`Europe/Dublin` observes daylight saving. Two consequences, both verified against tzdata rather than assumed:

- **On the spring transition** (2026-03-29, at 01:00 UTC) the local clock jumps from 01:00 to 02:00. **Local times in `[01:00, 02:00)` do not exist.** A naive conversion silently moves them forward an hour.
- **On the autumn transition** (2026-10-25, at 01:00 UTC) the local clock falls from 02:00 back to 01:00. **Local times in `[01:00, 02:00)` occur twice.** Two instants an hour apart project onto the same `(date, minuteOfDay)`.

The second one is a genuine defect in the model, not a curiosity: the overlap guard compares minutes from midnight, so on that one hour of that one day it cannot distinguish two bookings that are an hour apart in real time, and would refuse the second as a collision.

**A Slot may not intersect the local interval `[01:00, 02:00)` on a daylight-saving transition date.** Validation refuses it with `SLOT_CROSSES_MIDNIGHT`'s sibling, `SLOT_IN_DST_TRANSITION`, and a fixed-clock test covers both dates.

This is the same class of constraint, for the same reason, as the existing rule that an Event may not span midnight: the Venue-Day model buys atomicity by keeping a day's arithmetic inside one document, and both rules are the price. Stating the price is worth more than a conversion that is quietly wrong twice a year.

Rejected: storing the UTC offset alongside each booking, which makes the overlap predicate compare offset-adjusted values and puts timezone arithmetic back inside the guard the model exists to keep simple. The constraint costs two lines of validation; the alternative costs the model's central claim.

**A note on `Europe/Dublin` specifically.** In tzdata, Irish Standard Time (UTC+1, summer) is modelled as the *standard* offset and winter as a **negative** daylight offset — `dst()` returns `-1 hour` in January and `0` in July. `java.time` conversions are unaffected, but older calendar APIs report daylight saving inverted. The implementation owes one assertion pinning the JDK's actual behaviour rather than trusting either reading of it.

### URL and payload conventions

- Resource paths are plural nouns under `/api`: `/api/events`, `/api/events/{eventId}/registrations`, `/api/venues/{venueId}/days/{date}`.
- **State changes that are not CRUD are named sub-resources, not verbs on the parent**: `POST /api/events/{eventId}/registration`, `DELETE /api/events/{eventId}/registration`, `POST /api/events/{eventId}/attendance`. Naming them makes each one a separate authorization surface and a separate OpenAPI operation.
- Request and response bodies are `camelCase` JSON. Field names match the ubiquitous language in [`CONTEXT.md`](../../CONTEXT.md) — an API field is never called `attendees` when the glossary says `enrolled`.
- **Controllers return role-specific DTOs**, as the technical baseline requires. A Student's view of an Event and an Officer's view are different types, not one type with fields blanked.

### Configuration and secrets

- Every environment-supplied value is a named configuration property with no production default: the MongoDB URI, the session secret, and **the check-in HMAC secret**.
- The HMAC secret has a **development default only**, so a fresh clone runs. It is required in any other profile and the application fails to start without it.
- **Rotating the HMAC secret invalidates every door code in flight.** The effective loss is under two minutes of scanning, and a restart is not an operation that happens during an Event. This is accepted rather than solved with key versioning.

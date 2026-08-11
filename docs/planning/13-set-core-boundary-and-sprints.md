# Set the Core boundary and Sprint split

Type: grilling
Status: resolved
Blocked by: 12

## Question

What is in Core, what is deferred, how does Core divide into deployable Sprints, and what evidence closes it?

## Answer

### Core

Everything the resolved decisions specify, and nothing else:

- Sign-in with server-side sessions; three roles, with Club Officer held per Club and granted by a University Admin.
- Clubs and Events: Draft → Published → Cancelled, four timestamps, all other Phases derived.
- **The Seat Ledger** — capacity guard, direct registration, ordered Waitlist, immediate promotion on withdrawal, `via` and `promotedCount`, capacity raise triggering promotion.
- Per-Event custom registration forms: five field types, server-side validation, locked once anyone registers, CSV export.
- Venues and Venue-Day Slot booking with the atomic overlap guard; idempotent release on cancellation.
- QR check-in: rotating HMAC door code, session-proven identity, Roster check, idempotent attendance, distinguishable manual override.
- Live attendee count over WebSocket.
- Attendance dashboard: fixed denominators, live aggregation, Club and University Admin views.
- Search, filter, sort and paging over Events — the rubric's beyond-CRUD gate, and the one Core feature no ADR needed because nothing about it is contested.
- Docker image, GitHub Actions pipeline, and a deployed public URL.

### Future Work

Designed, argued and deliberately not built. Each is recoverable from the ADR that produced it.

| Deferred | Why it is out of Core |
|---|---|
| University approval of Events | Adds a status, a rejection branch and a notification; breaks the single-account demo |
| Promotion confirmation with holds and timeout release | The most expensive complexity available, protecting against a problem free events do not have |
| Reconciliation sweeps for promotion and orphan bookings | Safety nets for mechanisms that are already atomic |
| Versioned form definitions | Real machinery; the lock-on-first-registration rule removes the need |
| File upload as a field type | A storage subsystem behind one field |
| Venue buffers, recurring bookings, cross-midnight Events | Each reintroduces a multi-document write |
| Offline check-in queueing | Lets a device hold attendance the server cannot see |
| In-app notification inbox | Promotion is silent; the `via` badge is the cheap substitute |
| Measured performance experiment, then a cache if it earns one | Profile before optimising; this is the only honest route to Redis |
| Shared broker for multi-instance WebSocket | Core is one instance and says so |

### Sprints

One week each, ten to twelve focused hours, **each ending in something deployed and demonstrable**. No horizontal layer is ever a Sprint.

| Sprint | Delivers | Demonstrable at the end |
|---:|---|---|
| **1** | Walking skeleton — React, Boot, MongoDB, Mongock, Docker, CI green with no business logic. Then sign-in, seeded Clubs and officer grants, Event Draft → Published with derived Phases, student browse with search and filter | A signed-in officer publishes an Event; a student finds it |
| **2** | **The Seat Ledger.** Register, capacity guard, Waitlist, withdrawal, immediate promotion, `via`, `promotedCount`, raise-capacity promotion, and the concurrency tests that prove it | Two concurrent registrations for one seat; one wins, one queues, a withdrawal promotes |
| **3** | Custom registration forms end to end, plus Venues and Slot booking with the overlap guard. **MVP** | An officer builds a form, books a room, and exports the answers |
| **4** | Check-in: token codec, door screen, scan page, attendance on the Seat Ledger, manual override, live count over WebSocket | A phone scans the projected code and the count moves |
| **5** | Dashboard, then **Core Acceptance**: Cypress journeys, negative authorization tests, coverage gates, README, deployment | A stranger clones it, runs it, and reads real numbers |

About 50–60 focused hours. Sprint 3 is the point at which the product loop is complete; Sprints 4 and 5 make it presentable.

**The tooling rule:** nothing is installed before the Sprint that needs it. Cypress arrives in Sprint 5, WebSocket in Sprint 4, Mongock in Sprint 1 because indexes exist from the first collection.

### Core Acceptance

Core is done when all of these hold at once. Any one failing means Core is not done, regardless of features.

- `./server/mvnw verify` green from a clean checkout, **JaCoCo ≥ 90%**.
- `npm --prefix web run check` green, **Vitest global ≥ 90%**.
- Checkstyle, SpotBugs and ESLint failing the build on violation, all running in CI.
- Testcontainers integration tests covering every REST endpoint.
- **Concurrency tests** proving the capacity guard, the promotion, the check-in idempotency guard and the Venue overlap guard each behave under parallel callers. These are the project's central claim; an untested claim is a story, not evidence.
- **A negative authorization test for every row of the permission matrix.**
- Cypress running in CI over three journeys: register → waitlist → promotion; officer publishes → books a venue → exports answers; door code → scan → dashboard reflects it.
- Deployed at a public URL, reachable, with seeded demo data.
- README with the positioning line, three screenshots, an architecture diagram, and a `docker compose up` that works for someone who has never seen the repo.
- Every ADR link in the repository resolves.

### Predicted rubric score

Gate: **7.5 / 8**. The half is `Spring Data JPA (多表)` — this project uses Spring Data MongoDB, a trade the map recorded deliberately and which BookInn covers at portfolio level. Every other gate item is met, including Swagger and axios, which are named explicitly and are cheap to lose by accident.

Bonus: Docker + cloud ⭐+2, CI/CD ⭐+2, linters and tests in CI +1, Dashboard +1, Cypress E2E in CI +1 = **7 points 🔵**. No Team, no Map, no Microservice, no Kafka or Redis — all four deliberate.

This matches the portfolio's original 7-point prediction for this project while replacing the Team point, which no longer exists, with the Cypress point.

### What is explicitly not promised

No sixth Sprint. No Redis or Kafka, whatever the measured experiment eventually says. No mobile app. No notifications. No second datastore. Scope creep during the build is measured against this paragraph.

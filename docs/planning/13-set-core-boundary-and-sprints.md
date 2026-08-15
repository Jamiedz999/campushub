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
- **The Seat Ledger** — capacity guard, direct registration, ordered Waitlist, immediate promotion on withdrawal, `via`, `promotedCount` and `everQueuedCount`, capacity raise triggering promotion, and the `startsAt` freeze carried in every guard.
- Per-Event custom registration forms: five field types, server-side validation, locked once anyone registers, CSV export.
- Venues and Venue-Day Slot booking with the atomic overlap guard; idempotent release on cancellation.
- QR check-in: rotating HMAC door code, session-proven identity, Roster check, idempotent attendance, distinguishable manual override.
- Live attendee count over WebSocket.
- Attendance dashboard: fixed denominators, live aggregation, Club and University Admin views.
- Search, filter, sort and paging over Events — the rubric's beyond-CRUD gate, and the one Core feature no ADR needed because nothing about it is contested.
- Docker image, GitHub Actions pipeline, and a deployed public URL.

### Future Work

Designed, argued and deliberately not built. Each is recoverable from the ADR that produced it.

**This table is scoped to the work currently planned — Core, through v1.0.** It records what is excluded from the plan that exists today, and it is not a prediction about the project's whole future. The project is expected to continue after v1.0; when it does, this table is the roadmap it starts from, because every row already carries the reasoning for why it was left out and what it would cost to put in.

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

Ten to twelve focused hours each, **every one ending in something demonstrable**. No horizontal layer is ever a Sprint.

There is no calendar deadline on this project. The Sprints are an ordering and a size, not a schedule.

| Sprint | Delivers | Demonstrable at the end |
|---:|---|---|
| **1** | Walking skeleton — React, Boot, MongoDB, Mongock, Docker, CI green with no business logic. Then sign-in, seeded Clubs and officer grants, Event Draft → Published with derived Phases, student browse with search and filter | A signed-in officer publishes an Event; a student finds it |
| **2** | **The Seat Ledger.** Register, capacity guard, Waitlist, withdrawal, immediate promotion, `via`, `promotedCount`, `everQueuedCount`, raise-capacity promotion, and the concurrency tests that prove it | Two concurrent registrations for one seat; one wins, one queues, a withdrawal promotes |
| **3** | Custom registration forms end to end, Venues and Slot booking with the overlap guard, then the README, screenshots and architecture diagram. **v0.1 — the product loop is complete and the repository is presentable** | An officer builds a form, books a room, and exports the answers — and a stranger reading the README understands all of it |
| **4** | Check-in: token codec, door screen, scan page, attendance on the Seat Ledger, manual override, live count over WebSocket | A phone scans the projected code and the count moves |
| **5** | Dashboard, then **Core Acceptance**: Cypress journeys, negative authorization tests, coverage gates, deployment. **v1.0** | A stranger clones it, runs it, and reads real numbers |

About 50–60 focused hours.

**Two milestones, and they are what "done" means.**

- **v0.1 — end of Sprint 3.** The product loop is complete and the repository reads well: README, screenshots, architecture diagram, `docker compose up` that works for someone who has never seen it. **This is the point at which the project can go on a CV** — the highlights are real, the code is readable, and the concurrency claims already have their tests. There is no public URL yet, deliberately.
- **v1.0 — end of Sprint 5.** Core Acceptance passes in full, including the public deployment.

Splitting the milestone this way is what stops the CV waiting on the deployment. The README moved out of the release Issue for exactly this reason.

**The public URL arrives only at the release Issue.** Every earlier Sprint is demonstrable locally, through `docker compose up`, and nothing before that Issue may depend on a deployment target existing. The application stays host-agnostic — one container plus a `MONGODB_URI` — so the release Issue can pick any host without rework.

**The tooling rule:** nothing is installed before the Sprint that needs it. Cypress arrives in Sprint 5, WebSocket in Sprint 4, Mongock in Sprint 1 because indexes exist from the first collection.

### Core Acceptance

Core is done when all of these hold at once. Any one failing means Core is not done, regardless of features.

- `./server/mvnw verify` green from a clean checkout, **JaCoCo ≥ 90% on both line and branch**, enforced from the scaffold Issue onward and never lowered.
- `npm --prefix web run check` green, **Vitest global ≥ 90%**.
- Checkstyle, SpotBugs and ESLint failing the build on violation, all running in CI.
- Testcontainers integration tests covering every REST endpoint.
- **Concurrency tests** proving the capacity guard, the promotion, the check-in idempotency guard and the Venue overlap guard each behave under parallel callers. These are the project's central claim; an untested claim is a story, not evidence.
- **A negative authorization test for every row of the permission matrix.**
- Cypress running in CI over three journeys: register → waitlist → promotion; officer publishes → books a venue → exports answers; door code → check-in → dashboard reflects it. **The third journey posts a server-derived token to the check-in endpoint rather than scanning** — a headless browser cannot read a QR code, so the camera is the one link in that chain the E2E does not cover, and it is named here rather than left to look covered.
- Deployed at a public URL, reachable, with seeded demo data.
- README with the positioning line, three screenshots, an architecture diagram, and a `docker compose up` that works for someone who has never seen the repo.
- Every ADR link in the repository resolves.

### Predicted rubric score

Checked afterwards, item by item, in [`RUBRIC.md`](implementation/RUBRIC.md) — including the two rows
that diverged.

Gate: **7.5 / 8**. The half is `Spring Data JPA (多表)` — this project uses Spring Data MongoDB, a trade the map recorded deliberately and which BookInn covers at portfolio level. Every other gate item is met, including Swagger and axios, which are named explicitly and are cheap to lose by accident.

Bonus: Docker + cloud ⭐+2, CI/CD ⭐+2, linters and tests in CI +1, Dashboard +1, Cypress E2E in CI +1 = **7 points 🔵**. No Team, no Map, no Microservice, no Kafka or Redis — all four deliberate.

This matches the portfolio's original 7-point prediction for this project while replacing the Team point, which no longer exists, with the Cypress point.

### What is explicitly not promised

**Within the work planned here**, there is no sixth Sprint, no Redis, no Kafka, no second datastore, no mobile app and no notifications. Scope creep during the build is measured against this paragraph.

The scope is the plan's, not the project's lifetime. Earlier wording said "no Redis or Kafka, whatever the measured experiment eventually says", which contradicted the Future Work table two sections above — that table names the measured performance experiment as the legitimate route to a cache. A rule that forbids the thing the plan elsewhere describes how to earn is not a rule, it is an ambiguity. **The binding statement is the narrow one: these are excluded from the work being planned now, and nothing here decides what a later plan may contain.**

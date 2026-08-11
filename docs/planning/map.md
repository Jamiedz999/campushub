# Define a solo, document-modelled campus club events platform

Label: wayfinder:map

## Destination

Produce a build-ready, numbered implementation queue that takes this project from an empty repository to a deployed Core: a club publishes an Event, students register or join a Waitlist that auto-promotes, a Venue slot is booked without collision, attendance is captured by a rotating QR code, and an attendance dashboard reports on it — on React + Spring Boot + MongoDB. The map is done when every Core decision is recorded and the first implementation Issue can be opened without a further decision.

## Notes

### Portfolio context

- This is portfolio project 3, after **BookInn** (done, MySQL + JPA + pessimistic locking) and **Delivery Glance** (in build, PostgreSQL + `JdbcClient` + SSE + MapLibre). Every decision here must be checked against those two: the stack may deliberately repeat, the **interview story must not**.
- Scoring reference: `document/Side-Project-要求与评分Rubric.md` (Must-have gate + Nice-to-have points) and `document/portfolio1.html`.
- **This project is solo.** The Team/Group point is deliberately abandoned. This project is never to be presented, on a resume or in an interview, as a team project, and collaboration experience from any other project is never transplanted onto it. What may honestly be claimed is the real工程 practice used here: PR-based workflow, branch protection, CI gates, ADRs.
- The Team point may still be earned later, but only by real collaboration (open-source PRs, or a real second contributor). That is out of scope for this map.

### Gate obligations (non-negotiable, do not discount)

- Backend unit tests with JaCoCo ≥ 90%; frontend Vitest global ≥ 90%.
- Integration tests over the REST API with Testcontainers (MongoDB).
- Checkstyle + SpotBugs + ESLint, all wired into CI.
- Swagger/OpenAPI via springdoc — **Delivery Glance currently ships neither JPA nor springdoc** (`server/pom.xml` has `spring-boot-starter-jdbc`, no `spring-boot-starter-data-jpa`, no springdoc), so this project must not also skip it.
- Business logic beyond CRUD: search / filtering / sorting / paging on Events.
- Docker image + deployed to a public URL; GitHub Actions pipeline.

### MongoDB guardrails — the anti-resume-driven-design contract

MongoDB is chosen because the portfolio lacks it, so it has to **earn** its place. Two justifications were accepted and both are binding:

1. **Per-Event custom registration forms.** Each Event defines its own registration fields (T-shirt size, year of study, team members). A relational schema would need EAV tables or a JSON column — i.e. a document store inside a relational one. **If this feature is ever cut, MongoDB loses its justification.** It is not optional.
2. **Concurrency collapsed into single-document atomicity.** Seat allocation, the Waitlist and Venue slot booking are modelled so the race is contained inside one document and resolved by an atomic conditional update (`findOneAndUpdate` with a guard filter, `$inc`, ordered array `$push`/`$pop`) — no locks.

Binding consequences:

- **One database.** No second datastore. No Redis, no Kafka, no Elasticsearch in Core.
- Rich text alone is **not** a valid justification for MongoDB and must never be cited as one.
- Three ADRs are owed: why a document model here; why no locking this time; and where multi-document transactions are genuinely required. If the answer to the third turns out to be "almost everywhere", the modelling is wrong and must be revisited — recording that honestly is part of the ADR's value.
- Choosing MongoDB means **giving up the Spring Data JPA gate item** on this project; BookInn covers JPA at the portfolio level. This is a conscious trade, recorded here so it is never re-litigated as an oversight.

### The three-project concurrency arc (the main interview asset)

| Project | Same class of problem | Solution |
|---|---|---|
| BookInn | Overlapping date ranges, double booking | Pessimistic lock, `SELECT … FOR UPDATE` |
| Delivery Glance | Two couriers claim one delivery | Atomic conditional update (optimistic) |
| **This project** | Seat capacity / Venue slot collision | **Document modelling contains the race; atomicity removes the lock** |

Venue slot booking is retained even though it is structurally the same problem BookInn solved. Its value is *not* code reuse — **BookInn's implementation cannot be reused**, since it is JPA + `SELECT … FOR UPDATE` + a relational transaction. What carries over is **domain knowledge** (interval open/closed boundaries, the edge cases already paid for). The story is "I solved this a third time and a different data model made the lock disappear", never "I copied my old code".

### Standing preferences for this effort

- **The user has delegated business-logic ticket decisions to the agent's recommended option.** Grill the question properly, then decide and record it; do not stop to ask the user to choose. Escalate only where a decision needs authority or input the agent genuinely cannot supply — money, accounts, deployment credentials, or a reversal of something already recorded in this map.
- Domain language lives in [CONTEXT.md](../../CONTEXT.md) and is kept current as terms resolve.
- Human decisions use the `grilling` and `domain-modeling` skills. Prototype tickets use `prototype`. Research tickets are AFK `/research` subagents.
- Planning artefacts follow the Delivery Glance layout established on 2026-08-10: open decision tickets in `docs/planning/tickets/` (now empty — all resolved), resolved decisions move to `docs/adr/`, evidence in `docs/planning/research/`, prototypes in `docs/planning/prototypes/`, and the implementation queue in `docs/planning/issues/`. Nothing planning-related goes in `.scratch/`.
- Documents are written in English; the working conversation is in Chinese.
- **Plan only.** This map produces decisions and specs. No application code is written under this map; the first line of code is written against the first implementation Issue.
- No repository, git history or GitHub remote exists yet. The name is settled: **CampusHub**, base package `com.campushub`, positioned as _"Campus club events, from signup to the door."_

## Decisions so far

<!-- One line per resolved ticket: gist plus a link into docs/adr/ or docs/planning/research/. -->

- [Research project name collisions](research/01-research-project-name-collisions.md) — `CampusHub` fails the screen on a function twin and a stack twin at once; `ClubRoster`, `SignupDesk` and `SeatQueue` pass and go to ticket 02.
- [Choose the public project name](../adr/02-choose-public-project-name.md) — `CampusHub`, positioned as _"Campus club events, from signup to the door."_, adopted despite the collisions found by the screen.
- [Define the Event lifecycle](../adr/03-define-event-lifecycle.md) — only Draft, Published and Cancelled are stored; the other five phases are derived from four timestamps and the Seat Ledger, closing registration early just moves a timestamp, and University approval is Future Work.
- [Define registration, capacity and Waitlist auto-promotion](../adr/04-define-registration-capacity-and-waitlist.md) — only the Seat is atomic; a Seat Ledger on the Event document resolves every race in one write, withdrawal and promotion are the same operation, there is no confirmation step, and Core needs no multi-document transaction on the registration path.
- [Define per-Event custom registration forms](../adr/05-define-custom-registration-forms.md) — five field types keyed by stable `fieldId`, definition on the Event and answers on the Registration, locked once the first Registration exists, and no file upload.
- [Define the Venue and Slot booking model](../adr/06-define-venue-slot-booking.md) — a Venue-Day document makes the half-open overlap predicate an atomic guard; cancellation is handled by idempotent, self-healing cleanup rather than a transaction, and Slots are always acquired before the old one is released.
- [Define QR check-in and its anti-fraud properties](../adr/07-define-qr-checkin-and-anti-fraud.md) — the door displays a 60-second rotating HMAC code proving presence while the signed-in session proves identity; attendance joins the Seat Ledger, manual overrides stay distinguishable, and the threat model is stated honestly.
- [Define the three roles and resource-level authorization](../adr/08-define-roles-and-resource-authorization.md) — Club Officer is a per-Club grant, ownership is enforced by scoping every query rather than checking after reading, and every rule in the matrix owes a negative test.
- [Set the Core boundary and Sprint split](13-set-core-boundary-and-sprints.md) — five deployable Sprints of 10–12 hours, Core Acceptance gated on concurrency tests and a negative authorization test per matrix row, predicted at 7.5/8 gate and 7 bonus points.
- [Draft the numbered implementation Issue queue](14-draft-implementation-issue-queue.md) — CH-020 through CH-031 exist and every Issue traces to a resolved decision; CH-020 is ready.
- [Lock the Core technical baseline](../adr/12-lock-core-technical-baseline.md) — server-side sessions, WebSocket with an in-process broadcast and no change streams or replica set, `MongoTemplate` only with no repository interfaces, Mongock owning every index, ECharts and Cypress. Operational reference: [`TECHNICAL-BASELINE.md`](implementation/TECHNICAL-BASELINE.md).
- [Prototype the student registration and check-in experience](prototypes/10-prototype-student-registration-and-checkin.md) — exposed that promotion is silent with notifications out of scope; fixed by recording `via: DIRECT | PROMOTED` on each enrolled entry rather than by adding a confirmation step or notification infrastructure.
- [Prototype the club officer console](prototypes/11-prototype-club-officer-console.md) — raising capacity needs an explicit warning that it admits waiting Students immediately, the editability rules only read as policy when shown as one table, and the unmet-demand table is what makes the dashboard worth opening twice.
- [Define the attendance dashboard](../adr/09-define-attendance-dashboard.md) — every denominator fixed, computed live by aggregation with a stated threshold that would change that, and a `promotedCount` added to the Seat Ledger so Waitlist conversion is measurable.

Settled during charting, before any ticket existed (recorded in Notes above, not as tickets): solo with the Team point abandoned; the CampusHub feature set retained with a new core; MongoDB single-database with the guardrail contract; three roles with resource-level authorization; Tailwind replacing MUI while TanStack Query is retained and Zustand is limited to client UI state.

## Destination reached

**This map is complete.** Every Core decision is resolved and the implementation queue exists: [CH-020 through CH-031](implementation/ISSUE-WORKFLOW.md#the-queue), twelve dependency-ordered specs across five Sprints, governed by [`ISSUE-WORKFLOW.md`](implementation/ISSUE-WORKFLOW.md). **CH-020 is ready**; everything else is blocked until its dependency is merged.

The two live planning documents are [the Sprint roadmap and Core Acceptance gate](13-set-core-boundary-and-sprints.md) and [`TECHNICAL-BASELINE.md`](implementation/TECHNICAL-BASELINE.md).

Building may begin. No further wayfinding session is needed unless implementation surfaces a decision nobody made — in which case it comes back here as a new ticket rather than being settled in a commit.

## Not yet specified

- **Deployment target and its inputs** — host, public hostname, secrets, and whether MongoDB is a container or a managed free tier. Deliberately open: these are environment inputs required only by CH-031 and they block nothing before it.
- **Future Work after Core** — the deferred increments are listed with their reasons in [the Core boundary document](13-set-core-boundary-and-sprints.md#future-work). They are not commitments, and the measured performance experiment is the only honest route by which Redis could ever enter this project.

## Out of scope

- **Team / Group collaboration artefacts.** No teammate exists; simulating one is excluded on integrity grounds, not scoping grounds.
- **Redis, Kafka, Elasticsearch, and any second datastore** — excluded from Core by the MongoDB guardrails.
- **Microservices** — the same proportionate-engineering position taken in BookInn ADR 0007.
- **Payments** for paid events, and any ticketing or refund flow.
- **Native mobile applications.** Check-in is a mobile browser page.
- **Maps and geospatial features** — Delivery Glance already owns that story.
- **Machine learning**, recommendation engines and predictive attendance.
- **Multi-campus or multi-tenant SaaS**, billing and subscriptions.
- **Email, SMS and push notifications.**
- **Self-service club registration and campus-wide account provisioning** beyond what the three roles need.

# CampusHub Core technical baseline

Status: current
Decision record: [ADR 12](../../adr/12-lock-core-technical-baseline.md)
Scope authority: [the map](../map.md)

## Purpose

This is the single implementation source for the Core stack, repository shape and module seams. The ADRs explain *why*; implementation Issues say *what to build next*. An Agent must not infer extra Core dependencies from the prototypes or from any ADR's Future Work sections.

## Locked Core stack

| Concern | Choice for Core |
|---|---|
| Backend | Java 25 LTS, Spring Boot 4.1 current patch, Maven Wrapper, Spring MVC, Spring Security, Bean Validation |
| Durable data | MongoDB 8 current patch, **single node — no replica set**, accessed through `MongoTemplate` |
| Schema evolution | Mongock change units, versioned and committed; they own every index. Automatic index creation from annotations is disabled |
| Sessions | Spring Security form login with opaque same-origin sessions, Spring Session MongoDB, secure cookies, CSRF enabled. **No JWT** |
| API docs | springdoc-openapi, served at `/swagger-ui` and guarded by a test that fails if the document stops generating |
| Frontend | Node 24 LTS, React 19.2 current patch, strict TypeScript, Vite 8.1, React Router, TanStack Query over **axios**, Zustand, Tailwind CSS |
| Charts | Apache ECharts via `echarts-for-react` |
| Realtime | Spring WebSocket (STOMP-free, raw `WebSocketHandler`) with an **in-process broadcast**. No MongoDB change streams |
| Verification | JUnit 5, AssertJ, Mockito, Testcontainers MongoDB, Vitest, Testing Library, **Cypress** |
| Quality gates | JaCoCo ≥ 90% backend, Vitest ≥ 90% frontend global, Checkstyle, SpotBugs, ESLint, all failing the build |
| Packaging | Docker Compose for local MongoDB; one multi-stage image containing the Boot app and the compiled React assets |
| Operations | Actuator health, request correlation, redacted logs. No monitoring platform in Core |

Versions are pinned in lock and build files by the scaffold Issue. A later patch update is allowed only in its own dependency PR with the full verification suite green; it does not reopen this decision.

**Deployment target is an environment input, not a Core decision.** Host, hostname, secrets and whether MongoDB is a container or a managed free tier are supplied at the release Issue. Nothing before that Issue may block on them.

## Repository shape

```text
campushub/
├── .github/workflows/ci.yml
├── server/
│   ├── .mvn/wrapper/…  mvnw  mvnw.cmd  pom.xml
│   └── src/
│       ├── main/java/com/campushub/…
│       ├── main/resources/application.yml
│       └── test/java/com/campushub/…
├── web/
│   ├── package.json  package-lock.json  tsconfig*.json  vite.config.ts
│   ├── cypress.config.ts  cypress/
│   └── src/…
├── compose.yaml
├── Dockerfile
├── .env.example
└── README.md
```

- Development uses Vite's same-origin `/api` proxy to Boot, so the session cookie behaves in development exactly as in production.
- Production builds the React assets first, places them in the Boot jar, and serves browser-history routes from the same origin.
- `server/target`, `web/node_modules`, `web/dist`, local env files and secrets are ignored. Lockfiles and the Maven Wrapper are committed.

## Modules and seams

The backend is one executable divided by **business** module, never by technical layer. There is no application-wide `controller/service/repository` tree.

**A module owns whole documents. Two modules never write the same document.** This is the rule that keeps the atomicity argument true: if the Seat Ledger could be written from two places, the guarantee that every race is resolved by one guarded write would depend on discipline instead of structure.

| Module | Owns | Interface exposed to peers |
|---|---|---|
| `system` | build and runtime proof | read-only status |
| `identityaccess` | accounts, roles, the current actor, Club grants | current actor, authorization checks |
| `club` | Club documents and officer grants | club lookup, grant queries and commands |
| `event` | **the whole Event document** — lifecycle, form definition, Seat Ledger, Waitlist, attendance | guarded commands and role-scoped queries |
| `registration` | Registration documents (form answers only) | submit and read answers, CSV projection |
| `venue` | Venue and Venue-Day documents | book, release, day view |
| `checkin` | rotating token derivation and verification | issue a display code, verify a scanned one |
| `dashboard` | read-only aggregations across collections | metric queries per role scope |
| `realtime` | WebSocket sessions and fan-out | publish a scoped refresh hint |

`event` is deliberately large because the Event document is. Registration, promotion, withdrawal and attendance all mutate the Seat Ledger, so they belong to one owner; the module is subdivided by package internally and still presents one interface.

`checkin` verifies tokens but does **not** write attendance — it hands a verified `(eventId, studentId)` to `event`, which performs the guarded write. That is the seam: presence proof and Seat Ledger ownership are different responsibilities.

Implementation classes, `MongoTemplate` access and web DTOs stay package-private where Java allows. Controllers call module interfaces; no module reaches into another's collection. A `shared` package may hold `Clock`, identifiers and error primitives — never generic business services or speculative abstractions.

Create a seam only where it hides real complexity or has a genuine second implementation. For Core:

- **`Clock`** — production time versus a fixed clock. Every Registration Window rule, check-in window and token window is tested against it.
- **`CheckInTokenCodec`** — HMAC derivation and verification, testable in isolation with a fixed clock and a known secret.
- **`AttendanceBroadcaster`** — a real WebSocket fan-out in production, a recording no-op in tests.

Redis, Kafka, change streams, replica sets, PostGIS, WebFlux, microservices and a generic event bus are not seams and not dependencies.

## Persistence rules

- **`MongoTemplate` is the only persistence API. There are no Spring Data repository interfaces.** Every operation that matters here is a guarded conditional update or an aggregation — `findAndModify` with a filter, an aggregation-pipeline update, an `$elemMatch` overlap guard — and derived repository methods cannot express any of them. Offering two routes to the database would invite the important operations to be written the wrong way. `spring-boot-starter-data-mongodb` remains the dependency that provides `MongoTemplate`.
- **Every contended write is one `findAndModify` with its guard in the filter.** No read-then-write, no application-side compare-and-set, no locks.
- **Mongock owns all indexes**, including the unique index on `(venueId, date)` and the unique index on `(eventId, studentId)` for Registrations. Index creation is never inferred from annotations at runtime.
- **`now` always comes from the injected `Clock`** and is passed into queries as a value. Server time is authoritative; client time is never trusted for anything.
- MongoDB runs as a **single node**. No Core operation uses a multi-document transaction, so no replica set is required. Introducing one would be a change to this document, not a local decision.

## Web and realtime rules

- Unsafe requests carry CSRF protection. Every internal route requires an authenticated session.
- **Authorization is enforced by scoping the query**, never by loading and then comparing. No module method offers a way to fetch a Club-owned resource without the caller's grants.
- Controllers return role-specific DTOs. The frontend never receives a broad domain object with sensitive fields hidden by CSS.
- **WebSocket carries refresh hints, never authoritative state.** Every message tells a client that something changed in a scope it is subscribed to; the client then re-reads an authorized snapshot over normal HTTP. Reconnection is therefore always safe and never loses correctness.
- The broadcast is **in-process**, which is correct for a single instance and wrong for several. This limitation is stated in the ADR rather than hidden, and horizontal scale would require a shared broker.

## Frontend rules

- **axios is the HTTP client**, wrapped by TanStack Query. It is a gate item in its own right — the rubric names React and Axios explicitly — and one shared instance is where the CSRF header and error normalisation live.
- **TanStack Query owns server state** — every fetch, cache and invalidation. **Zustand owns client-only UI state** — multi-step form drafts, door-screen settings, filter panels. Nothing lives in both, and server data is never copied into Zustand.
- Tailwind is the only styling system. No component library.
- Strict TypeScript with no `as` assertions in application code. The custom registration form is rendered from a discriminated union on field type.
- Chart components are thin. **All arithmetic lives in pure data-shaping functions** that are unit tested to the full coverage bar; the chart components themselves get smoke tests for series count and accessible labels.

## Core build contracts

The scaffold Issue must establish these; every later Issue keeps them green, and CI runs the same commands from a clean checkout:

```bash
./server/mvnw verify
npm --prefix web ci
npm --prefix web run check
docker compose up --build --wait
curl --fail --silent http://localhost:8080/actuator/health
curl --fail --silent http://localhost:8080/api/system
docker compose down
```

`npm run check` runs TypeScript checking, Vitest once with coverage, and a production build. `mvnw verify` runs Checkstyle, SpotBugs, the tests and the JaCoCo gate. Cypress runs as its own CI job against the composed stack, introduced by the Issue that first needs it.

## Explicitly absent from Core

- Redis, Kafka, Elasticsearch, a second datastore, and any cache.
- MongoDB replica sets, change streams and multi-document transactions.
- JWT, refresh tokens and stateless authentication.
- Microservices, Kubernetes, CQRS, event sourcing and a generic event bus.
- Email, SMS, web push, and any notification infrastructure.
- File upload and object storage.
- Offline check-in queueing.
- Event approval workflows, recurring Venue bookings, Slot buffers and cross-midnight Events.
- Pre-aggregated dashboard collections and scheduled ETL.

Each omission is recorded in the ADR that produced it, not silently forgotten.

# The hardening evidence

Status: current
Decision records: [ADR 08](../../adr/08-define-roles-and-resource-authorization.md), [ADR 12](../../adr/12-lock-core-technical-baseline.md), [ADR 17](../../adr/17-define-code-structure-and-its-enforcement.md)
Gate: [Core Acceptance](../13-set-core-boundary-and-sprints.md#core-acceptance)

## Purpose

[`EVIDENCE.md`](EVIDENCE.md) collects the project's two central negative claims — the contended writes are atomic, and a Club Officer's authority stops at their own Club. This document is the same idea for the three that arrived with [the hardening Issue](https://github.com/Jamiedz999/campushub/issues/19): **no Student identifier reaches a log line**, **no form answer reaches a DTO outside the owning Club**, and **the coverage gate is not being met by looking away**.

It is a separate file rather than more sections in `EVIDENCE.md` because that document is the concurrency and authorization argument and is referenced as such from two ADRs and the map. Same shape of claim, different claim.

A fourth of the same shape was added while checking [#11](https://github.com/Jamiedz999/campushub/issues/11) — **every REST endpoint has a Testcontainers integration test** — and is [the last section](#the-endpoint-coverage-sweep) here.

## What is not there

Each claim is stated the way it would be tested if it were false, with the control that stops the test passing for the wrong reason.

| Claim | Test | How it could fail silently without that test |
|---|---|---|
| Every unsafe route requires the CSRF token | `RoutingTableSweepIntegrationTest.everyUnsafeRouteRefusesARequestThatDoesNotCarryTheCsrfToken` | A route added after the review is written. The sweep enumerates Spring's own routing table rather than a list, so a new controller is covered the day it is written |
| …including the two Spring Security serves without a controller | the same test, via `FILTER_SERVED_UNSAFE_ROUTES` | `/api/auth/login` and `/api/auth/logout` are handled by the form-login and logout filters and appear in no `RequestMappingHandlerMapping`, so a sweep of the routing table alone can never see them |
| …and refusing everything is not how it passes | `RoutingTableSweepIntegrationTest.theSameUnsafeRoutesAreReachedOnceTheCsrfTokenIsCarried` | A misspelled path also answers 403 |
| No form answer reaches a DTO a University Admin can read | `RoutingTableSweepIntegrationTest.aStudentsFormAnswersAreAbsentFromEveryDtoAUniversityAdminCanReach` | A field added to a DTO that already had a legitimate reason to exist. This sweeps every mapped `GET`, not a chosen few |
| …and the sweep read real payloads rather than a wall of 404s | the same test asserts which routes answered `200`, because a University Admin is refused the Club-scoped ones by design and a sweep of refusals proves nothing | The sweep passing on empty problem-detail bodies — the single most likely way this test rots into decoration |
| …and the answer it looks for exists at all | `RoutingTableSweepIntegrationTest.theOwningClubsOfficerDoesReadTheAnswerTheSweepIsLookingFor` | A fixture that never recorded an answer passes the sweep perfectly |
| The session and CSRF cookies carry the attributes that were decided | `SecurityConfigIntegrationTest.theSessionCookieIsHttpOnlyAndSameSiteLax`, `…theCsrfCookieIsReadableByScript…`, `…neitherCookieIsMarkedSecureOverPlainHttp…` | A default changing under an upgrade, in either direction: attributes lost, or `Secure` hardcoded on and the cookie silently undeliverable everywhere TLS is not terminated |
| No Student identifier reaches a log line, over a full journey | `RedactedLoggingIntegrationTest.aFullJourneyLeavesNoStudentIdentifierInTheLogOutput` | The journey 404-ing its way through and leaving nothing to grep — so the same test asserts the Student ends up on the Roster, scanned |
| …because the masking is wired in, not because nothing tried | `RedactedLoggingIntegrationTest.aLineWrittenWithAnIdentifierInItComesOutMasked`, `…aStackTraceCarryingAnIdentifierComesOutMaskedToo` | The application logs almost nothing today, so a clean grep proves nothing on its own. These two log an identifier on purpose and watch it come out masked |
| …and every appender is redacting, not just the one that exists today | `RedactedLoggingIntegrationTest.everyAppenderTheApplicationLogsThroughRedactsAndCarriesTheCorrelationId` | A file or JSON appender added later, wired to a pattern without `%redact`, quietly publishing what the console appender masks |
| No display name and no form answer reaches a log line either | `RedactedLoggingIntegrationTest.neitherAStudentsDisplayNameNorTheirFormAnswersReachALogLine` | Neither can be masked by pattern, so both depend on nothing logging them — and something was. See below |
| Every request carries a correlation id to the response and onto its log lines | `CorrelationIdFilterTest`, `RedactedLoggingIntegrationTest.theIdOnTheResponseIsTheIdOnTheLogLines`, `…aRefusedRequestCarriesACorrelationIdBackJustLikeASuccessfulOne` | Redaction without correlation would leave the logs safe and useless — with identifiers masked, the id on the response header is the only handle a report can be traced by |
| The door screen stays legible from the back of a room | `doorScreenLegibility.test.ts` | A colour or a size edited to taste. The test reads the `door-` tokens out of `web/src/index.css` and holds every text pair to WCAG **AAA** and every size to a floor |
| …and the door screen actually uses them | `OfficerDoorPage.test.tsx.takesEveryColourAndSizeFromTheDoorPaletteRatherThanTailwindsOwn` | A `text-slate-500` appearing on that screen. It would pass the legibility test, which reads tokens and not markup, and pass axe, which has no contrast rule under jsdom |
| The rendered surfaces have no structural accessibility violations | `accessibilityViolations(...)` assertions in the `OfficerDoorPage`, `StudentCheckInPage`, `RegistrationFormFields`, `EventsBrowsePage`, `EventRegistrationPage`, `MyEventsPage` and `SignInPage` tests | The helper being reduced to returning an empty array. `testAccessibility.test.ts` feeds it markup that is definitely wrong and watches it complain |

### The one this actually caught

The display-name grep failed the first time it ran, and what it found is the reason the whole exercise was worth doing.

Spring writes the request and response body into a log line at `DEBUG` — `Read [...]` and `Writing [...]`, from `org.springframework.web.servlet.mvc.method.annotation`. For this application that means a Student's display name and their form answers: the CSV export the Officer downloads went into the log verbatim, name and answer together. Turning `DEBUG` on for `org.springframework.web` to chase a routing problem would have published exactly what [ADR 08](../../adr/08-define-roles-and-resource-authorization.md)'s privacy boundary exists to keep inside the owning Club.

Redaction cannot help with either: a name has no lexical shape to match on, and an answer is arbitrary text. So that package is pinned at `INFO` in `application.yml`, with the reason written beside it, and the test that found it now holds it there. It is a floor rather than a ceiling — naming the package explicitly still overrides it, which is the deliberate act it should be.

### What is deliberately not claimed

- **Display names and form answers are not masked, and cannot be.** The defence for them is that nothing logs them, which is why it is asserted over a real journey rather than assumed.
- **Only `GET` responses are swept for form answers.** The routes that project answers at all are the two `registration-answers` reads, and both refuse a University Admin outright — `EventRegistrationAccessIntegrationTest.neitherAStudentNorAUniversityAdminMayReadTheFormAnswers` in [`EVIDENCE.md`](EVIDENCE.md)'s matrix. The sweep is the belt to that braces, not the only strap.
- **The accessibility assertions cannot see contrast, focus order or anything positional**, because jsdom has no layout and no canvas. That is why contrast is held to a number separately, from the declared values rather than from a render, and why the door screen is separately bound to those values. Everything else remains a manual concern and is not claimed here.
- **`/v3/api-docs` and `/swagger-ui` are reachable without a session.** The filter chain requires one only under `/api` and `/ws`. The documents describe the API and carry no data, and [ADR 12](../../adr/12-lock-core-technical-baseline.md) puts Swagger in Core deliberately, so this is a named position rather than an oversight.

## The coverage exclusions

The gate is JaCoCo ≥ 90% line and branch on the backend and Vitest ≥ 90% on the frontend, and [it is never lowered](TECHNICAL-BASELINE.md). An exclusion lowers it quietly, so every one in the build is listed here, and both build files point back at this table rather than restating it.

| Where | Excluded | Why |
|---|---|---|
| `server/pom.xml` | `**/*Application.class` | A `main` that calls `SpringApplication.run`. Every test boots it; the only uncovered branch is the context failing to start, and a test for that would be testing Spring |
| `web/vite.config.ts` | Vitest's `coverageConfigDefaults.exclude` | Config files, `dist/`, `coverage/`, type declarations and the test files themselves. Kept rather than restated — dropping it would count `vite.config.ts` towards the gate |
| `web/vite.config.ts` | `src/main.tsx` | The entry point: mounts the app, no branching of its own |
| `web/vite.config.ts` | `**/__boundaryFixture.ts` | Deliberate import-boundary violations that exist so `featureBoundary.test.ts` can prove the ESLint rule has teeth. Ships in no bundle |
| `web/vite.config.ts` | `**/__doorSocketDouble.ts` | A stand-in for the browser's `WebSocket`, used only by tests. Counting a test double towards the gate would measure the tests testing themselves |

**Removed rather than justified:** `**/config/**` in the JaCoCo configuration. This codebase is divided by business module and has no `config` package anywhere — [ADR 17](../../adr/17-define-code-structure-and-its-enforcement.md) gives each module `domain/`, `persistence/`, `web/` and `internal/` and nothing else — so the exclusion covered no class and only made the gate look softer than it is.

The baseline also permits excluding DTO records with no behaviour. None is excluded: ours all carry a `from(...)` projection, which is exactly the code a role-specific DTO must be right about.

**No new exclusion was added by this work.** The axe helper is test-only and would have been the obvious candidate; it earns its coverage from `testAccessibility.test.ts` instead, which is the test that proves the helper reports violations at all.

## The endpoint coverage sweep

Core Acceptance asks for **Testcontainers integration tests covering every REST endpoint**. That is a claim about a set, and a set is the one thing a passing build cannot show you: a route with no test does not fail anything. It was true when it was last checked, on 2026-08-14, and it was not true before that check — which is the reason for writing the check down rather than the result.

The routes are enumerated from the source, never from a list kept by hand:

```bash
cd server && grep -rn -E '@(Get|Post|Put|Delete|Patch)Mapping' src/main/java --include='*.java'
```

**32 routes: 30 mapped by a controller, plus the two Spring Security serves from the filter chain.** Each is covered by a test that exercises what the route does over real HTTP against Testcontainers MongoDB:

| Routes | Covered by |
|---|---|
| `GET /api/system` | `system.web.SystemControllerIntegrationTest` |
| `GET /api/auth/me`, `POST /api/auth/login`, `POST /api/auth/logout` | `identityaccess.internal.SecurityConfigIntegrationTest` — the last two have no controller and appear in no routing table |
| `POST /api/clubs/{clubId}/events`, `GET /api/events/{eventId}`, `PATCH /api/events/{eventId}`, `POST …/publication`, `POST …/cancellation` | `event.web.EventOfficerAccessIntegrationTest` |
| `GET /api/events`, `GET`/`POST`/`DELETE /api/events/{eventId}/registration`, `PUT …/registration/answers`, `GET /api/events/mine`, `PUT …/registration-form`, `GET …/registration-answers`, `GET …/registration-answers/csv` | `event.web.EventRegistrationAccessIntegrationTest` |
| `GET …/door-code`, `POST`/`GET …/attendance`, `PUT …/attendance/{studentId}` | `checkin.web.CheckInAccessIntegrationTest` |
| `PUT`/`DELETE /api/events/{eventId}/slot`, `POST`/`GET /api/venues`, `PATCH /api/venues/{venueId}`, `GET /api/venues/{venueId}/days/{date}` | `venue.web.VenueAccessIntegrationTest` |
| `GET /api/dashboard` | `dashboard.web.DashboardAccessIntegrationTest` |
| `GET`/`POST /api/clubs/{clubId}/officers`, `DELETE …/officers/{accountId}` | `identityaccess.web.ClubOfficerAccessIntegrationTest` |

### What "covered" is not allowed to mean

`RoutingTableSweepIntegrationTest` reaches **every** mapped route over real HTTP, and it is deliberately not counted here. It asserts that a route refuses a request without the CSRF token, that the same route answers once the token is carried, and that no form answer appears in the body — never anything about what the route is for. Counting it would let this whole claim be satisfied by two tests that cannot tell a working endpoint from a broken one. It is a sweep for one property across all routes; this table is one behavioural test per route, and they are different obligations.

### What the sweep found

Two routes had no such test when it was run, and both looked covered from a distance:

- **`PUT /api/events/{eventId}/registration/answers`** — the retry path behind "your Seat is safe, but your answers were not saved". Tested only against a mocked repository (`RegistrationModuleImplTest`) and a mocked module (`RegistrationControllerTest`).
- **`GET /api/events`** — Event discovery. Tested at the repository, against real Mongo, by `EventRepositoryBrowseIntegrationTest`, and at the controller against a mocked module. Nothing joined the two ends, so parameter binding and the Published-only rule were proven separately from each other and never together.

Both now have one in `EventRegistrationAccessIntegrationTest`. The retry test was run once with the answer write removed from `RegistrationModuleImpl.retryAnswers` and failed on the read-back assertion, for the same reason [the guard-removal record](EVIDENCE.md#the-guard-removal-record) exists: a test that passes with its subject deleted was not testing it.

**The maintenance obligation:** a new route is a new row. The grep above regenerates the left column in one command, so the check is cheap; nothing in the build enforces it, which is stated here rather than left to be discovered.

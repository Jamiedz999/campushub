# The concurrency and authorization evidence

Status: current
Decision records: [ADR 04](../../adr/04-define-registration-capacity-and-waitlist.md), [ADR 06](../../adr/06-define-venue-slot-booking.md), [ADR 07](../../adr/07-define-qr-checkin-and-anti-fraud.md), [ADR 08](../../adr/08-define-roles-and-resource-authorization.md)
Gate: [Core Acceptance](../13-set-core-boundary-and-sprints.md#core-acceptance)

## Purpose

The project rests on two claims: **the contended writes are atomic**, and **a Club Officer's authority stops at their own Club**. Both are claims about what does *not* happen, so neither is visible in a feature and neither is proven by a passing happy path.

This document is where the evidence for both is collected and where the mapping from claim to test is written down. It is not a second copy of the tests — the tests are the evidence. It exists because the tests are deliberately scattered: each one lives beside the module it covers, so no directory listing shows the set, and without a written mapping "every matrix row has a negative test" would be an assertion nobody could check.

Three later claims have the same shape and are kept in [`HARDENING.md`](HARDENING.md) beside this one: no Student identifier reaches a log line, no form answer reaches a DTO outside the owning Club, and the coverage gate is not being met by looking away.

## The concurrency suite

Four claims, gathered by the JUnit tag `@Tag("concurrency")` rather than by a directory, because [the document-ownership rule](../../adr/17-define-code-structure-and-its-enforcement.md) wants each test beside the module that owns the write. All four run real parallel callers against Testcontainers MongoDB. None of them uses a mock, because a mock cannot fail the way a lost update fails.

```
cd server && ./mvnw verify -Pconcurrency
```

The `concurrency` profile is what makes the tag a suite. It filters both Surefire and Failsafe by the tag and turns off coverage and the linters, since a four-test run cannot meet a whole-project coverage gate — and a profile that lowered the gate to fit would be exactly the exclusion Core Acceptance forbids. **`./mvnw verify` with no profile remains the command that enforces the gates**, and it runs these tests too.

| Claim | Test | The guard it rests on |
|---|---|---|
| N parallel registrations against a Capacity of M enrol exactly M | `EventRepositorySeatLedgerIntegrationTest.nParallelRegistrationsAgainstACapacityOfMProduceExactlyMEnrolments` | `$expr: { $lt: [ { $size: "$enrolled" }, "$capacity" ] }` in `EventRepository.takeSeat` |
| Parallel withdrawals of one Seat promote exactly one Student | `EventRepositorySeatLedgerIntegrationTest.parallelWithdrawalsOfOneSeatPromoteExactlyOneStudent` | `enrolled.studentId: studentId` in `EventRepository.withdrawEnrolled` — the freed Seat is what the filter matches, so only one caller can free it |
| Parallel scans by one Student produce exactly one attendance record | `EventRepositoryAttendanceIntegrationTest.nParallelScansByOneStudentProduceExactlyOneAttendanceRecord` | `attendance.studentId: { $ne: studentId }` in `EventRepository.recordAttendance` |
| Parallel overlapping Slot requests have exactly one winner | `VenueModuleImplIntegrationTest.parallelOverlappingSlotRequestsHaveExactlyOneWinner` | the half-open `$not/$elemMatch` predicate on `bookings` in `VenueRepository.acquire` |

`VenueModuleImplIntegrationTest.parallelIdenticalRequestsForOneEventCreateOnlyOneBooking` carries the tag as well: it is the same guard meeting a double-clicked identical request rather than two different Events.

### The guard-removal record

A test that still passes with its guard deleted is not evidence of the guard. Each of the four was therefore run once with its guard removed from the production code, and each failed. Restated: the row below is what the suite reports when the atomicity claim is false.

Run on 2026-08-14, against `mongo:8` under Testcontainers, one guard removed at a time and the source restored from git after each run.

| Guard removed | What the test reported |
|---|---|
| Capacity `$expr` in `takeSeat` | 40 parallel registrations against a Capacity of 10 all enrolled — `expected: 10, but was: 40` |
| `enrolled.studentId` match in `withdrawEnrolled` | all 20 parallel withdrawals of the same Seat reported success, where exactly one may — `expected: 1, but was: 20`. That guard is what makes the freed Seat the thing being matched, so without it the promotion arithmetic runs once per caller rather than once per Seat |
| `attendance.studentId: $ne` in `recordAttendance` | 30 parallel scans by one Student wrote 30 attendance records — `expected: 1, but was: 30` |
| overlap predicate in `VenueRepository.acquire` | both overlapping requests won and double-booked the room — `[ACQUIRED, ACQUIRED]` where `[ACQUIRED, SLOT_TAKEN]` was expected |

The experiment is [`server/scripts/verify-guards-are-load-bearing.sh`](../../../server/scripts/verify-guards-are-load-bearing.sh) — it patches out one guard at a time, runs that claim's test, and restores the file from git after each. It is committed so the table above is reproducible rather than a story about something that happened once.

Every run in it is *expected* to fail, which makes a broken toolchain indistinguishable from a load-bearing guard if the exit code is all you read. So the script proves the suite green before it breaks anything, and reads `Tests run: N, Failures: >0` rather than the exit status — a compile break reports **inconclusive**, not success. An experiment that cannot fail to confirm its own hypothesis is not an experiment, and this one had that bug before it had this check.

It is deliberately **not** part of `mvnw verify`. The result only changes when someone edits a guard, and a mutation harness maintained in the build for four of them costs more than the fact is worth. **If one of these guards is edited, the script is run again and this table is updated** — that is the maintenance obligation, and it is stated here rather than left implied.

## The Seat Ledger freeze

The Roster is the Seat Ledger frozen at `startsAt`, and the door checks Students against it. A Ledger that could still change afterwards would make "frozen" a word rather than a rule, so the freeze is carried in every Seat Ledger write's filter as `startsAt: { $gt: now }` and is tested as a guard, on a fixed clock:

| Test | What it pins |
|---|---|
| `EventRepositorySeatLedgerIntegrationTest.everySeatLedgerWriteIsRefusedAtTheExactInstantTheEventStarts` | registration, Waitlist join, Waitlist leave, withdrawal and the Capacity raise all refused **at** `startsAt` — the instant an off-by-one lets through |
| `EventRepositorySeatLedgerIntegrationTest.everySeatLedgerWriteStaysRefusedAfterTheEventHasStarted` | the same five refused **after** it, including after `endsAt` — the interval a guard written as an equality would let through |

Promotion has no write of its own: it happens inside `withdrawEnrolled` and inside the Capacity raise, so refusing those two is what refuses the promotion. Both tests assert `promotedCount` is still zero afterwards, which is how a promotion that slipped through would show itself.

Check-in is the one Seat Ledger write that deliberately omits the freeze — it exists to happen after the Event has begun — and carries its own window instead, from fifteen minutes before `startsAt` until `endsAt`. That exception is [ADR 07](../../adr/07-define-qr-checkin-and-anti-fraud.md)'s, not an escape from this one.

## The permission matrix, row by row

Every row of [ADR 08's matrix](../../adr/08-define-roles-and-resource-authorization.md#the-permission-matrix) and the named test that refuses it. Every refusal is **`404` carrying `code: NOT_FOUND`**, never `403`: the resource is scoped out of the query rather than found and then denied, so a caller cannot learn from a refusal that the resource exists. The tests assert the `code`, not only the status, because a `404` from a routing mistake looks identical otherwise.

| Matrix row | Student ❌ | Club Officer of another Club ❌ | University Admin ❌ |
|---|---|---|---|
| Browse and view Published Events | — (permitted) | — (permitted) | — (permitted) |
| Register, withdraw, join Waitlist | — (permitted) | — (permitted) | — (permitted) |
| Check in to an Event they are enrolled in | — (permitted) | — (permitted) | — (permitted) |
| See attendee names or form answers | `aStudentCannotOpenTheDoorScreenOfAnEventTheyAreAttending`, `neitherAStudentNorAUniversityAdminMayReadTheFormAnswers` | `anOfficerOfAnotherClubCannotOpenTheDoorOrReadTheRoster`, `anOfficerCannotBuildOrReadRegistrationFormsForAnotherClub` | `aUniversityAdminCannotRunTheDoorReadTheRosterOrOverrideAttendance`, `neitherAStudentNorAUniversityAdminMayReadTheFormAnswers` |
| Create, edit, publish, cancel an Event | `aStudentWithNoGrantsCannotCreateAnEvent`, `aStudentWithNoGrantsCannotReadEditOrPublishADraft` | `anOfficerOfClubBReceives404NotFoundNeverForbiddenForClubAsDraft`, `anOfficerOfClubBCannotEditClubAsDraft`, `anOfficerOfClubBCannotPublishClubAsDraft`, `anOfficerOfAnotherClubCannotCancelIt` | `aUniversityAdminCannotCreateEditPublishOrReadAnEventTheyMayCancel` (cancel only — the positive is `aUniversityAdminCancelsAnyClubsEventThroughTheSameUnscopedRoute`) |
| Build the registration form, export answers | `aStudentWithNoGrantsCannotReadEditOrPublishADraft`, `neitherAStudentNorAUniversityAdminMayReadTheFormAnswers` | `anOfficerCannotBuildOrReadRegistrationFormsForAnotherClub` | `aUniversityAdminCannotCreateEditPublishOrReadAnEventTheyMayCancel`, `neitherAStudentNorAUniversityAdminMayReadTheFormAnswers` |
| Book or release a Venue Slot | `neitherAnOfficerOfAnotherClubNorAStudentCanBookOrReleaseAnEventsSlot` | `neitherAnOfficerOfAnotherClubNorAStudentCanBookOrReleaseAnEventsSlot` | `anAdminManagesVenueRecordsButCannotBookOrReleaseAnEventsSlot` — see the amendment note below |
| Run the door screen, override attendance | `aStudentCannotOpenTheDoorScreenOfAnEventTheyAreAttending` | `anOfficerOfAnotherClubCannotOpenTheDoorOrReadTheRoster`, `anOfficerOfAnotherClubCannotMarkAnyonePresent` | `aUniversityAdminCannotRunTheDoorReadTheRosterOrOverrideAttendance` |
| Create and manage Venues | `anAdminManagesVenuesWhileOfficersCanOnlyListThemAndStudentsSeeNothing` | `anAdminManagesVenuesWhileOfficersCanOnlyListThemAndStudentsSeeNothing` | — (permitted) |
| Grant Club Officer rights | `aStudentCannotGrantOfficerRights`, `aStudentWithNoGrantsCannotReachTheOfficerOnlyRoute` | `anOfficerCannotGrantOrRevokeOfficerRightsEvenInTheirOwnClub`, `anOfficerOfOneClubCannotSeeAnotherClubsOfficers` | — (permitted) |
| Read the cross-club dashboard | `aStudentWhoOfficersNothingHasNoDashboard` | `anOfficerAskingForAnotherClubsMetricsFindsNothing` | — (permitted) |

Where the tests live:

| Test class | Rows it holds |
|---|---|
| `event.web.EventOfficerAccessIntegrationTest` | Event authoring and cancellation |
| `event.web.EventRegistrationAccessIntegrationTest` | registration, the Draft boundary, forms and answers |
| `checkin.web.CheckInAccessIntegrationTest` | the door, the Roster and manual overrides |
| `venue.web.VenueAccessIntegrationTest` | Venue records and Event Slots |
| `identityaccess.web.ClubOfficerAccessIntegrationTest` | grants, revocation and the officer list |
| `dashboard.web.DashboardAccessIntegrationTest` | both dashboard views and the privacy boundary |

The first three rows carry no ownership rule — they are the routes every signed-in Student may use — so they owe no cross-club negative. What they do owe is the boundary of the permission itself, and each has one: `aStudentCannotSeeOrRegisterForAStillUnpublishedDraft` for the first two, and `aWaitlistedStudentIsToldWhyRatherThanBeingLetIn` for the third, since "an Event they are enrolled in" is the whole content of that row. `SecurityConfigIntegrationTest.unauthenticatedAccessToAProtectedRouteIsRefused` covers the case underneath all of them.

### Amendment found while writing this table

**The Venue Slot row of ADR 08's matrix said a University Admin may book or release a Slot. The code refuses them, and [ADR 06](../../adr/06-define-venue-slot-booking.md) is why**: Admins create and manage Venues, Club Officers book them. Two resolved decisions disagreed, the implementation followed the more specific one, and nobody noticed until the rows were listed against their tests. ADR 08 is amended; the behaviour is unchanged, and `anAdminManagesVenueRecordsButCannotBookOrReleaseAnEventsSlot` was already asserting the corrected rule.

This is the mapping earning its keep: the contradiction was invisible while the matrix and the tests were only ever read separately.

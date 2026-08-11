# CH-030 · Harden the Core with risk-based evidence

Sprint: 5
Area: cross-cutting
Blocked by: 028, 029
Decisions: [Core boundary and Sprints](../13-set-core-boundary-and-sprints.md)

## Change

Close the evidence gaps rather than add features. Every item exists because a specific claim would otherwise be unproven.

- **Concurrency suite gathered in one place**: capacity guard, single promotion per freed Seat, check-in idempotency, Venue overlap. These four are the project's central claim.
- **A negative authorization test for every row of the permission matrix**, asserting not-found rather than forbidden.
- Cypress in CI over three journeys: register → waitlist → promotion; officer publishes → books a venue → exports answers; door code → scan → dashboard reflects it.
- Coverage brought to and held at 90% on both sides, with any genuinely untestable exclusion named and justified rather than silently excluded.
- Session security review: CSRF on every unsafe route, secure cookie attributes, no sensitive field reaching a DTO.
- Accessibility pass on the student mobile surfaces and the door screen.
- Request correlation and redacted logging; no student identifier in a log line.

## Acceptance

Every Core Acceptance bullet except deployment and README passes from a clean checkout.

## Tests

This Issue is tests.

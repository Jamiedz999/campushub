# CH-022 · Add the Event document, lifecycle and browse

Sprint: 1
Area: event
Blocked by: 021
Decisions: [Event lifecycle](../../adr/03-define-event-lifecycle.md)

## Change

- `event` module owning the whole Event document: `status` of Draft, Published or Cancelled, the four timestamps, `capacity`, and an empty Seat Ledger.
- **Phase is computed on read** from status, timestamps and the Seat Ledger. No Phase field is stored and no job writes one.
- An injected `Clock` supplies `now`. Every window rule is expressed against it.
- Officer commands: create Draft, edit, publish, cancel. Editability follows the lifecycle rules — capacity raise-only, no un-publishing, immutability from `startsAt` and from `endsAt`.
- Student-facing browse with **search, filter, sort and paging** over Published Events. This is the rubric's beyond-CRUD gate item; it ships here rather than being bolted on.
- All officer queries are **scoped by the caller's Club grants** in the query itself.

## Acceptance

- Every Phase in the lifecycle table is reachable in tests using only a fixed clock.
- An officer of Club A receives not-found — not forbidden — for Club B's Draft.
- Cancelling freezes the Seat Ledger rather than clearing it.

## Tests

Fixed-clock unit tests for every Phase boundary, including the exact instants. Negative authorization tests for every officer command. Integration tests for search, filter, sort and paging.

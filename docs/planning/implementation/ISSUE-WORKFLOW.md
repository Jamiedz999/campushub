# Core implementation Issue workflow

Status: current

## What an Issue is

An Issue spec in [`../issues/`](../issues/) is the authoritative statement of one increment: what changes, what must be true when it is done, and what tests prove it. Specs are files in this repository and are reviewed like code.

Once a GitHub remote exists, each spec becomes one GitHub Issue carrying a one-paragraph summary and a permalink to the committed spec. **The spec never lives in the issue body.** Execution state — open, closed, labels — lives only on GitHub; the spec files carry `Sprint:` and `Area:`, which describe the work, not where it stands.

## Definition of Ready

An Issue may be started when all of these hold:

1. Every Issue in its `Blocked by:` line is **merged**, not merely finished.
2. Every decision it references exists and is resolved. **An Issue that needs a decision nobody made is not ready** — it goes back to the map as a new ticket rather than being resolved by whoever is coding at the time.
3. Its acceptance criteria are checkable by someone who did not write them.
4. Any environment input it names has been supplied.

**Only the first unblocked Issue is ready.** Later Issues stay blocked until their dependency is merged. This is deliberate: the queue exists to stop several half-finished vertical slices from coexisting, which is the failure mode a solo project falls into most easily.

## Definition of Done

- All build contracts in [`TECHNICAL-BASELINE.md`](TECHNICAL-BASELINE.md) pass from a clean checkout.
- Coverage gates hold. Coverage is never lowered to make an Issue pass; if a gate blocks, the tests are missing.
- The acceptance criteria are demonstrably met, not argued.
- Merged through a pull request with CI green. Branch protection requires it.

## Branch and commit convention

- Branch `ch-0NN-<slug>`, matching the Issue number.
- Commits prefixed `CH-0NN:`.
- One Issue per pull request. A PR that grows a second Issue's work is split.

## When an Issue turns out to be wrong

Implementation regularly discovers what planning could not. When it does:

- If the fix is a **decision**, stop and add a ticket to the map. Do not decide it in a commit message.
- If the fix is a **correction to a resolved decision**, amend that ADR and note the amendment in the map's Decisions-so-far. Several ADRs here already carry amendments discovered exactly this way — that is the system working, not a failure of it.
- If the fix is **new scope**, it is Future Work unless it blocks Core Acceptance.

## The queue

| Issue | Sprint | Blocked by |
|---|---:|---|
| [CH-020 Scaffold the full-stack walking skeleton](../issues/020-scaffold-full-stack-walking-skeleton.md) | 1 | — |
| [CH-021 Add sign-in, roles and Club grants](../issues/021-add-signin-roles-and-club-grants.md) | 1 | 020 |
| [CH-022 Add the Event document, lifecycle and browse](../issues/022-add-event-lifecycle-and-browse.md) | 1 | 021 |
| [CH-023 Add the Seat Ledger — registration and capacity](../issues/023-add-seat-ledger-registration-and-capacity.md) | 2 | 022 |
| [CH-024 Add the Waitlist, withdrawal and promotion](../issues/024-add-waitlist-withdrawal-and-promotion.md) | 2 | 023 |
| [CH-025 Add per-Event custom registration forms](../issues/025-add-custom-registration-forms.md) | 3 | 024 |
| [CH-026 Add Venues and Slot booking](../issues/026-add-venues-and-slot-booking.md) | 3 | 022 |
| [CH-027 Add QR check-in and attendance](../issues/027-add-qr-checkin-and-attendance.md) | 4 | 024, 026 |
| [CH-028 Add the live attendee count over WebSocket](../issues/028-add-live-attendee-count.md) | 4 | 027 |
| [CH-029 Build the attendance dashboard](../issues/029-build-attendance-dashboard.md) | 5 | 027 |
| [CH-030 Harden the Core with risk-based evidence](../issues/030-harden-core-with-risk-based-evidence.md) | 5 | 028, 029 |
| [CH-031 Package the portfolio release](../issues/031-package-portfolio-release.md) | 5 | 030 |

**CH-020 is ready. Everything else is blocked.**

CH-026 depends only on CH-022, so it is the one Issue that could be taken out of Sprint order if Venue work is more appealing than form work on a given week.

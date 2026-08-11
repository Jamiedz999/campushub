# Core implementation Issue workflow

Status: current

## What an Issue is

An Issue spec in [`../issues/`](../issues/) is the authoritative statement of one increment: what changes, what must be true when it is done, and what tests prove it. Specs are files in this repository and are reviewed like code.

Each spec has one GitHub Issue carrying a one-paragraph summary and a permalink to the committed spec. **The spec never lives in the issue body.** Execution state — open, closed, `ready`/`blocked`, `sprint-N` — lives only on GitHub; the spec files carry `Sprint:` and `Area:`, which describe the work, not where it stands.

Issue titles are written plainly, with no `CH-0NN` prefix. The local `0NN` numbering orders the spec files and nothing else; the permalink in each issue body is what ties the two together. Dependencies use **GitHub's native issue dependencies**, so the frontier is visible in the GitHub UI without opening this document.

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

- Branch `ch-0NN-<slug>`, matching the spec number.
- Commits prefixed `CH-0NN:`, matching the spec number.
- One Issue per pull request. A PR that grows a second Issue's work is split.

## When an Issue turns out to be wrong

Implementation regularly discovers what planning could not. When it does:

- If the fix is a **decision**, stop and add a ticket to the map. Do not decide it in a commit message.
- If the fix is a **correction to a resolved decision**, amend that ADR and note the amendment in the map's Decisions-so-far. Several ADRs here already carry amendments discovered exactly this way — that is the system working, not a failure of it.
- If the fix is **new scope**, it is Future Work unless it blocks Core Acceptance.

## The queue

| Issue | Spec | Sprint | Blocked by |
|---|---|---:|---|
| [#1 Scaffold the full-stack walking skeleton](https://github.com/Jamiedz999/campushub/issues/1) | [`020`](../issues/020-scaffold-full-stack-walking-skeleton.md) | 1 | — |
| [#2 Add sign-in, roles and Club grants](https://github.com/Jamiedz999/campushub/issues/2) | [`021`](../issues/021-add-signin-roles-and-club-grants.md) | 1 | #1 |
| [#3 Add the Event document, lifecycle and browse](https://github.com/Jamiedz999/campushub/issues/3) | [`022`](../issues/022-add-event-lifecycle-and-browse.md) | 1 | #2 |
| [#4 Add the Seat Ledger — registration and capacity](https://github.com/Jamiedz999/campushub/issues/4) | [`023`](../issues/023-add-seat-ledger-registration-and-capacity.md) | 2 | #3 |
| [#5 Add the Waitlist, withdrawal and promotion](https://github.com/Jamiedz999/campushub/issues/5) | [`024`](../issues/024-add-waitlist-withdrawal-and-promotion.md) | 2 | #4 |
| [#6 Add per-Event custom registration forms](https://github.com/Jamiedz999/campushub/issues/6) | [`025`](../issues/025-add-custom-registration-forms.md) | 3 | #5 |
| [#7 Add Venues and Slot booking](https://github.com/Jamiedz999/campushub/issues/7) | [`026`](../issues/026-add-venues-and-slot-booking.md) | 3 | #3 |
| [#8 Add QR check-in and attendance](https://github.com/Jamiedz999/campushub/issues/8) | [`027`](../issues/027-add-qr-checkin-and-attendance.md) | 4 | #5, #7 |
| [#9 Add the live attendee count over WebSocket](https://github.com/Jamiedz999/campushub/issues/9) | [`028`](../issues/028-add-live-attendee-count.md) | 4 | #8 |
| [#10 Build the attendance dashboard](https://github.com/Jamiedz999/campushub/issues/10) | [`029`](../issues/029-build-attendance-dashboard.md) | 5 | #8 |
| [#11 Harden the Core with risk-based evidence](https://github.com/Jamiedz999/campushub/issues/11) | [`030`](../issues/030-harden-core-with-risk-based-evidence.md) | 5 | #9, #10 |
| [#12 Package the portfolio release](https://github.com/Jamiedz999/campushub/issues/12) | [`031`](../issues/031-package-portfolio-release.md) | 5 | #11 |

**[#1](https://github.com/Jamiedz999/campushub/issues/1) is ready. Everything else is blocked.**

#7 depends only on #3, so it is the one Issue that could be taken out of Sprint order if Venue work is more appealing than form work on a given week.

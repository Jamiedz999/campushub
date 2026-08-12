# Core implementation Issue workflow

Status: current

## What an Issue is

**The GitHub Issue is the spec.** It states what changes, what must be true when it is done, and what tests prove it, in full. There is no spec file in this repository and no summary-plus-link pointer — an Issue is read where the work happens, and a reader should never have to click through to find out what to build.

Each Issue names the Sprint it belongs to, the modules it touches, and the decisions it implements as absolute links into `docs/adr/` and `docs/planning/`. Titles are written plainly, with no prefix. The `0NN` numbers that survive in the footers order the original drafting and carry no other meaning.

**What stays in this repository is the durable thinking**: the ADRs, the domain glossary, the technical baseline, the Sprint roadmap and the map. Those are reasoned positions with reasons attached, they are cross-referenced, and they belong under version control. Implementation Issues are perishable execution instructions, and they belong in the tracker.

The cost of that split, named rather than hidden: **an Issue amendment gets no `git diff`.** Amendment is normal here — three decisions were corrected during planning alone — so a material change to an Issue is recorded as a comment on it saying what changed and why. When the change originates in a decision, the reasoning lives in that ADR's amendment, which *is* under version control.

## Where execution state lives

**GitHub's native issue dependencies are the only record of what is blocked.** They are computed, they update themselves the moment a blocker closes, and GitHub renders them in the issue list without anyone opening this file.

There is deliberately **no `blocked` label**. A label saying what the dependency graph already says is a second copy of the same fact with nothing keeping it honest: close a blocker and the graph is right while the label is wrong, and from then on the two disagree with no way to tell which was meant.

**There is a `ready` label, and it is not that.** Zero open blockers does not mean startable — the previous increment may be broken, an environment input may not have arrived, or a decision the Issue leans on may turn out not to exist. That is a human judgement, nothing else in the system records it, and it cannot be derived from the dependency graph or from anything else. `ready` is where it is recorded.

Apply it to **exactly one Issue at a time**, and remove it when that Issue closes. A closed Issue still wearing `ready` is precisely the drift this file exists to prevent.

`sprint-N` records intent and cannot be derived either, so it stays.

## Definition of Ready

An Issue receives `ready` only when all of these hold:

1. Every blocker on its dependency graph is **merged and closed**, not merely finished.
2. Every decision it references exists and is resolved. **An Issue that needs a decision nobody made is not ready** — it goes back to the map as a new ticket rather than being resolved by whoever is coding at the time.
3. Its acceptance criteria are checkable by someone who did not write them.
4. Any environment input it names has been supplied.
5. The previously merged increment still works.

Children inherit their parent's Definition of Ready rather than carrying their own — the parent is what was judged startable, and the children are how it gets done.

**Unblocked is not the same as ready, and exactly one Issue is `ready` at a time.** Several Issues can be unblocked at once — the moment #3 merges, both #4 and #7 are — and labelling all of them would let work start on assumptions an earlier Issue was supposed to establish. Rationing the label is what stops several half-finished vertical slices coexisting, which is the failure mode a solo project falls into most easily.

## Definition of Done

- All build contracts in [`TECHNICAL-BASELINE.md`](TECHNICAL-BASELINE.md) pass from a clean checkout.
- Coverage gates hold. Coverage is never lowered to make an Issue pass; if a gate blocks, the tests are missing.
- The acceptance criteria are demonstrably met, not argued.
- Merged through a pull request with CI green. Branch protection requires it.

## Issue size, branches and pull requests

**An Issue is sized so that one agent session can finish it without compressing its context.** That is the unit, and it is smaller than a deployable increment — several of the original twelve Issues were two or three sessions' work, which meant an agent had to either lose context halfway or make decisions the Issue never stated.

So an oversized Issue is **split into child Issues**, and the children carry the work:

- **Every child must be verifiable by one command.** Not independently deployable — independently *verifiable*. When a child closes, `./server/mvnw verify` or `npm --prefix web run check` is green. This is the only thing the next session can trust when it starts cold on a branch someone else left.
- **`ready` stays on the parent, one at a time.** Children are worked in order under it and are never labelled.

**One increment per pull request.** The children of one parent share a branch and merge as one pull request, because they were one increment before they were split and merging them together is merging the parent. **Children of different parents never share a branch.**

That boundary is the whole rule, and the reason for it is the Definition of Ready's last clause. "The previously merged increment still works" is only checkable if something was merged. Stack three unrelated Issues on one branch and nothing is merged until the end, so the check cannot run, the increments cannot be bisected, and a session that breaks an earlier one is found at the worst moment — with three Issues of diff to search. The `ready` rationing has the same purpose and fails the same way: several unrelated Issues on one branch *is* the coexisting-half-finished-slices failure mode the label exists to prevent.

- Branch `ch-0NN-<slug>`, matching the parent's spec number.
- Commits prefixed `CH-0NN:` — children use the parent's number, and name the child in the message.
- A pull request that grows a second parent's work is split.

## When an Issue turns out to be wrong

Implementation regularly discovers what planning could not. When it does:

- If the fix is a **decision**, stop and add a ticket to the map. Do not decide it in a commit message.
- If the fix is a **correction to a resolved decision**, amend that ADR and note the amendment in the map's Decisions-so-far. Several ADRs here already carry amendments discovered exactly this way — that is the system working, not a failure of it.
- If the fix is **new scope**, it is Future Work unless it blocks Core Acceptance.

## The queue

Thirteen increments, two of them split into children. Children are indented and are GitHub sub-issues of the increment above them.

| Issue | Sprint | Blocked by |
|---|---:|---|
| [#1 Scaffold the full-stack walking skeleton](https://github.com/Jamiedz999/campushub/issues/1) | 1 | — |
| &nbsp;&nbsp;↳ [#13 Scaffold the Spring Boot server and its quality gates](https://github.com/Jamiedz999/campushub/issues/13) | 1 | — |
| &nbsp;&nbsp;↳ [#14 Scaffold the React application and its quality gates](https://github.com/Jamiedz999/campushub/issues/14) | 1 | #13 |
| &nbsp;&nbsp;↳ [#15 Compose the stack and wire CI](https://github.com/Jamiedz999/campushub/issues/15) | 1 | #14 |
| [#2 Add sign-in, roles and Club grants](https://github.com/Jamiedz999/campushub/issues/2) | 1 | #1 |
| [#3 Add the Event document, lifecycle and browse](https://github.com/Jamiedz999/campushub/issues/3) | 1 | #2 |
| [#4 Add the Seat Ledger — registration and capacity](https://github.com/Jamiedz999/campushub/issues/4) | 2 | #3 |
| [#5 Add the Waitlist, withdrawal and promotion](https://github.com/Jamiedz999/campushub/issues/5) | 2 | #4 |
| [#6 Add per-Event custom registration forms](https://github.com/Jamiedz999/campushub/issues/6) | 3 | #5 |
| [#7 Add Venues and Slot booking](https://github.com/Jamiedz999/campushub/issues/7) | 3 | #3 |
| [#16 Write the README, screenshots and architecture diagram](https://github.com/Jamiedz999/campushub/issues/16) | 3 | #6, #7 |
| [#8 Add QR check-in and attendance](https://github.com/Jamiedz999/campushub/issues/8) | 4 | #5, #7, #16 |
| [#9 Add the live attendee count over WebSocket](https://github.com/Jamiedz999/campushub/issues/9) | 4 | #8 |
| [#10 Build the attendance dashboard](https://github.com/Jamiedz999/campushub/issues/10) | 5 | #8 |
| [#11 Harden the Core with risk-based evidence](https://github.com/Jamiedz999/campushub/issues/11) | 5 | #9, #10 |
| &nbsp;&nbsp;↳ [#17 Gather the concurrency and authorization evidence](https://github.com/Jamiedz999/campushub/issues/17) | 5 | #9, #10 |
| &nbsp;&nbsp;↳ [#18 Add the Cypress journeys to CI](https://github.com/Jamiedz999/campushub/issues/18) | 5 | #17 |
| &nbsp;&nbsp;↳ [#19 Harden sessions, accessibility and observability](https://github.com/Jamiedz999/campushub/issues/19) | 5 | #18 |
| [#12 Package the portfolio release](https://github.com/Jamiedz999/campushub/issues/12) | 5 | #11 |

**[#1](https://github.com/Jamiedz999/campushub/issues/1) is `ready`**, and work inside it starts at #13. The table above is a map of intent and can fall behind; the dependency graph says what is blocked, and the `ready` label says what may actually be started.

**[#16](https://github.com/Jamiedz999/campushub/issues/16) blocks Sprint 4.** That is deliberate: it is the v0.1 milestone, and putting it in the graph is what stops "the repository is presentable at the end of Sprint 3" from being an intention nobody is held to.

#7 depends only on #3, so it is the one Issue that could be taken out of Sprint order if Venue work is more appealing than form work on a given week.

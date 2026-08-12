# Draft the numbered implementation Issue queue

Type: task
Status: resolved
Blocked by: 13

## Question

Write the destination artefact: a numbered, dependency-ordered set of implementation Issues that can be opened and worked without any further decision.

This is the terminal ticket. When it is done the map is done.

The work:

- One spec file per Issue in `docs/planning/issues/`, numbered from 020 in the Delivery Glance convention, each carrying its Sprint and Area, the change it makes, the acceptance criteria, the tests it must add, and the Issues it is blocked by.
- Only the first unblocked Issue is ready; every later Issue stays blocked until its dependency is merged.
- Every Issue must be traceable back to a resolved decision in `docs/adr/`; an Issue that needs a decision that does not exist is a signal that the map is not finished.
- `docs/planning/implementation/ISSUE-WORKFLOW.md`, defining the Definition of Ready and the handoff to GitHub Issues once the repository exists.
- Update the map: move the queue out of "Not yet specified" and record the destination as reached.

## Answer

The queue exists as **[GitHub Issues](https://github.com/Jamiedz999/campushub/issues)**, wired with native dependencies and governed by [`ISSUE-WORKFLOW.md`](implementation/ISSUE-WORKFLOW.md). Each Issue carries its full specification; this repository keeps the decisions they implement, not the instructions themselves.

Every Issue names the decisions it implements, and every decision it names is resolved — the traceability check the ticket asked for found no Issue depending on a decision nobody made. Where implementation discovers otherwise, the workflow's rule applies: a missing decision goes back to the map as a ticket rather than being settled in a commit.

**[#1](https://github.com/Jamiedz999/campushub/issues/1) is the only Issue with no open blockers.** Everything else waits on its dependency being merged.

### Amendment — the 2026-08-12 review

The queue was drafted as twelve Issues. A review before building found three problems with it, and it is now **thirteen increments across nineteen Issues**:

- **The traceability check passed and was not enough.** Every Issue traced to a resolved decision, and no check asked whether the Issues shared any conventions. They did not: identifiers, error shapes, paging and timezone were unstated everywhere, and twelve Issues were about to answer them differently. [ADR 15](../adr/15-define-http-api-and-time-contract.md) and [ADR 16](../adr/16-define-event-discovery.md) now hold them.
- **Two Issues were buckets.** #1 and #11 were each three agent sessions of unrelated work. Both are split into children that share a branch and merge as one increment; [`ISSUE-WORKFLOW.md`](implementation/ISSUE-WORKFLOW.md) holds the sizing rule.
- **The README moved forward.** It was in the release Issue, behind a deployment that has no host yet. It is now [#16](https://github.com/Jamiedz999/campushub/issues/16) at the end of Sprint 3, and it blocks Sprint 4 so that the v0.1 milestone is enforced rather than intended.

Four Issues also carried claims that were wrong rather than merely unstated — most sharply #5, whose acceptance criterion asserted an invariant that broke as soon as anyone left the Waitlist. Each is amended on the Issue and in the ADR behind it.

This ticket is the map's destination. Nothing remains to decide before building starts.

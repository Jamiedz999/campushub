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

The queue exists: **[CH-020 through CH-031](implementation/ISSUE-WORKFLOW.md#the-queue)**, twelve dependency-ordered specs in [`issues/`](issues/), governed by [`ISSUE-WORKFLOW.md`](implementation/ISSUE-WORKFLOW.md).

Every Issue names the decisions it implements, and every decision it names is resolved — the traceability check the ticket asked for found no Issue depending on a decision nobody made. Where implementation discovers otherwise, the workflow's rule applies: a missing decision goes back to the map as a ticket rather than being settled in a commit.

**CH-020 is ready.** Everything else is blocked until its dependency is merged.

This ticket is the map's destination. Nothing remains to decide before building starts.

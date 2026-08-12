# Define the Event lifecycle

Type: grilling
Status: resolved
Blocked by: none

## Question

What are the canonical Event states, which transitions are permitted, who triggers each one, and what does each state mean to a student?

## Answer

### Stored Status versus derived Phase

The obvious state set — Draft, Published, Registration Open, Registration Closed, In Progress, Completed, Cancelled — is mostly not a state machine. Registration Open, Registration Closed, In Progress and Completed are all **readings of the clock against timestamps the Event already carries**. Storing them means a scheduled job must write them, that job can lag or fail, and the stored value can then disagree with the timestamps beside it. A field that can contradict the data it was derived from is a bug waiting to be scheduled.

So the model splits in two.

**Status is stored.** It changes only when a person decides something:

```
Draft ──publish──▶ Published ──cancel──▶ Cancelled
  └──────────────── cancel ─────────────────┘
```

Three values, forward only. There is no un-publishing: once Students can see an Event and take Seats, reverting it to a draft has no honest meaning. `Cancelled` is terminal.

**Phase is derived**, computed on read from Status, four timestamps and the Seat Ledger. Nothing writes it and nothing can be stale:

| Phase | Condition | What the Student sees |
|---|---|---|
| Draft | Status is Draft | Not visible to Students at all |
| Scheduled | Published, `now < registrationOpensAt` | "Registration opens on 3 March" |
| Registration Open | Published, within the Registration Window, Seats free | "12 of 40 seats left" |
| Full | Published, within the Registration Window, no Seats free | "Event full — join the waitlist (4 waiting)" |
| Registration Closed | Published, `now ≥ registrationClosesAt`, `now < startsAt` | "Registration has closed" |
| In Progress | Published, `startsAt ≤ now < endsAt` | "Happening now" |
| Completed | Published, `now ≥ endsAt` | "This event has ended" |
| Cancelled | Status is Cancelled | "This event was cancelled" |

Eight phases from three stored values and four timestamps. **Full is a phase, not a state** — it is the Seat Ledger being full, and it resolves itself the moment somebody withdraws.

### The Registration Window, and the correction it forces on the Seat Ledger

An Event carries `registrationOpensAt`, `registrationClosesAt`, `startsAt` and `endsAt`. The **Registration Window** is the first pair.

**Closing registration early is not a transition.** A Club Officer who wants registration to stop now sets `registrationClosesAt` to now. No extra state, no extra transition, no way for a "closed" flag to disagree with a future close time sitting next to it.

The current time is supplied by the application from an injectable server-authoritative clock and passed into the query as a value. It is never read from the client, and a fixed clock makes every window rule deterministically testable.

**This corrects the Seat Ledger decision.** [The registration ADR](04-define-registration-capacity-and-waitlist.md) wrote its guard as `state: RegistrationOpen`, which this decision has just established is not a stored field. The guard is:

```
{ _id: eventId,
  status: Published,
  registrationOpensAt:  { $lte: now },
  registrationClosesAt: { $gt:  now },
  enrolled: { $ne: studentId },
  waitlist: { $ne: studentId },
  $expr: { $lt: [ { $size: "$enrolled" }, "$capacity" ] } }
```

Plain indexed comparisons, still one atomic write, still no scheduler. Everything else in that decision stands unchanged.

### Approval is not in Core

A University Admin does **not** approve Events. Approval would add a stored `PendingApproval` status, a rejection outcome that is either terminal or a loop back to Draft, and a notification to tell the Club Officer — and notifications are out of scope. It would also break the demo: a visitor clicking through the product would need two accounts before a single Event could exist.

The University Admin role keeps real authority without it — Venues are theirs to manage, and the cross-club dashboard is theirs to read — so resource-level authorization still has three genuinely different actors to distinguish. Approval is preserved as Future Work.

### Cancellation

- The owning Club's Officer, or a University Admin, may cancel a Published Event at any time before it is Completed. A Completed Event cannot be cancelled; it happened.
- **The Seat Ledger freezes and is not cleared.** Both arrays stay exactly as they were, for the same reason the Waitlist survives a finished Event: erasing who was affected destroys the only record that they were, and the dashboard needs it.
- No registration, no withdrawal, no promotion and no check-in occur on a Cancelled Event.
- Its Venue Slot is released. That is two documents changing, and it is the multi-document candidate the registration decision flagged; the Venue decision owns the answer.
- Cancellation is terminal.

### What may change, and when

- **Draft** — everything is editable. Nothing depends on it yet.
- **Published, before `startsAt`** — title, description, timestamps and Venue may be edited. Moving `startsAt` moves the Venue Slot, which the Venue decision owns.
- **Capacity may be raised, never lowered.** Lowering it would have to evict enrolled Students, and there is no fair rule for choosing them. **Raising it promotes from the Waitlist immediately**, as many as now fit, in the one atomic pipeline update the registration decision already defines — the same mechanism as a withdrawal, triggered differently.
- **Capacity is required and finite.** An unlimited Event would make the Seat Ledger vacuous and the Waitlist dead code, and the Waitlist is this project's flagship behaviour.
- **From `startsAt`** — the Seat Ledger is frozen as the Roster, and the timestamps become immutable.

**Amendment — Capacity freezes at `startsAt` too, and the freeze is a guard rather than a convention.** As first written, this section said timestamps become immutable from `startsAt` and said nothing about Capacity. That left Capacity raisable a minute after the Event began, which would promote Students from the Waitlist into a Roster this same section calls frozen — the two rules contradicted each other and the second one silently lost.

**Capacity is immutable from `startsAt`**, alongside the timestamps. And the freeze is not a rule the application remembers to honour: **`startsAt: { $gt: now }` is part of the filter on every Seat Ledger write** — taking a Seat, joining the Waitlist, withdrawing, promoting and raising Capacity alike. A freeze that lives only in prose is enforced by whoever writes the next caller; a freeze that lives in the guard cannot be forgotten, which is the same argument [the authorization decision](08-define-roles-and-resource-authorization.md) makes for scoping queries.

Check-in is the one write that is deliberately outside this rule — it exists to happen after `startsAt`, and it carries its own window and its own Roster check.
- **From `endsAt`** — the Event is fully immutable.
- The registration form locks once the first Registration exists. That rule belongs to the forms decision; it is named here only so the immutability picture is complete.

### A Venue is optional

An Event may have no Venue — it may be online, or the space may not be settled yet. Keeping the link optional means the Event lifecycle does not depend on the Venue decision to be implementable.

# Define registration, capacity and Waitlist auto-promotion

Type: grilling
Status: resolved
Blocked by: none

## Question

How does a student take a seat, how does the Waitlist behave, and how is the whole thing kept correct under concurrency without a lock?

## Answer

### The governing idea

**Only the Seat has to be atomic. Everything else is allowed to be eventually consistent.**

Every decision below follows from that sentence, and it is the sentence this project's concurrency story is built on: BookInn contained the race with a pessimistic lock, Delivery Glance contained it with an atomic conditional update, and CampusHub contains it by **modelling the contended resource so that the race fits inside one document**.

### The Seat Ledger lives on the Event document

The Event document owns `capacity`, an `enrolled` array, and an ordered `waitlist` array of student identifiers.

Each `enrolled` entry is `{ studentId, via, at }`, where `via` is `DIRECT` or `PROMOTED`. Guards become `"enrolled.studentId": { $ne: studentId }` and are otherwise unchanged. The extra two fields were forced by [the student prototype](../planning/prototypes/10-prototype-student-registration-and-checkin.md): with notifications out of scope, a promoted Student had no way to learn they were in, and an officer had no way to see which registrations came off the queue. Recording the route in is the cheapest thing that fixes both, and it costs no extra write. Together these are the **Seat Ledger**. Every seat and queue mutation — taking a seat, joining the queue, withdrawing, promoting — is **one `findOneAndUpdate` against that one document**.

Registration documents live in their own collection and hold **only the custom form answers** (see the forms decision). They are written after a Seat is won. If that write fails, the student holds a Seat with missing answers: detectable, repairable, and irrelevant to who got the Seat.

Rejected alternatives:

- **A counter on the Event plus a separate Registration collection.** Taking a Seat becomes two writes — increment, then insert — and a failed second write requires compensation that can itself fail. This reintroduces exactly the distributed-write problem the whole design exists to avoid, and it would make the interview claim false.
- **Embedding full Registrations, form answers included, in the Event document.** One write for everything, but the Event document grows with answer payloads and every read of an Event drags all registration data unless projected everywhere. The split above gets the atomicity without the bloat.

Capacities here are tens to low hundreds of students, so two arrays of identifiers stay in the tens of kilobytes — far from the 16 MB document limit. **If a future Event type needs thousands of seats, this decision must be revisited**; it is correct for the scale, not for all scales.

### Taking a Seat

One guarded update carries the status check, the Registration Window check, the duplicate check and the capacity check together:

```
findOneAndUpdate(
  { _id: eventId,
    status: Published,
    registrationOpensAt:  { $lte: now },
    registrationClosesAt: { $gt:  now },
    enrolled: { $ne: studentId },
    waitlist: { $ne: studentId },
    $expr: { $lt: [ { $size: "$enrolled" }, "$capacity" ] } },
  { $push: { enrolled: studentId } } )
```

`now` comes from the application's injectable server-authoritative clock, never from the client. The [Event lifecycle decision](03-define-event-lifecycle.md) establishes why registration openness is a window comparison rather than a stored state.

A losing writer gets `null` and nothing has changed. When the guard fails only because the Event is full, the same shape with `$push: { waitlist: studentId }` and no capacity guard puts the student on the Waitlist instead.

**Classifying the failure.** `null` means "full", "already enrolled", "already waitlisted" or "registration not open", and the student must be told which. **Attempt first, then read the Event once to classify the failure.** Correctness lives entirely in the atomic write; the follow-up read only produces a message, so if the state moves between the two, the worst outcome is a slightly stale explanation and never a wrong seat. This is a deliberate example of knowing which reads are allowed to be stale.

### Withdrawal and Promotion are a single atomic operation

Withdrawal removes the student from `enrolled` **and** moves the head of `waitlist` into `enrolled` **in one update**, expressed as an aggregation-pipeline update so the new `enrolled` value can be computed from the document's own fields: `$filter` removes the withdrawing student while preserving order, and `$slice` plus `$concatArrays` moves the queue head across. Exact operators are an implementation concern; the binding decision is **one operation, not two**.

The same update increments a **`promotedCount`** on the Event. That field was added by [the dashboard decision](09-define-attendance-dashboard.md), which needs it to compute Waitlist conversion — a promoted Student leaves `waitlist` and is otherwise unrecoverable. One integer, written in the operation that already exists.

This is the payoff of the Seat Ledger. The classic failure of a scheduled-sweep design — two overlapping sweeps promoting two students into one freed Seat — **cannot occur**, because there is no window between freeing and filling.

Consequences:

- **Promotion is immediate**, never scheduled. A periodic reconciliation sweep is Future Work, as a safety net rather than a mechanism.
- **A promoted student is enrolled outright — there is no confirmation step.** Confirmation would require holds, expiry timestamps, a timeout-release job, re-promotion after release, and a rule about whether a held Seat counts as taken. That is the single most expensive piece of complexity available in this feature, and it buys protection against a problem free campus events do not have. Preserved as Future Work; `enrolled` therefore has exactly one meaning.
- Withdrawal by a **waitlisted** student is a plain `$pull` from `waitlist`, and promotes nobody.

### Rules

- **Withdrawal is permitted until the Event starts.** The later withdrawal stays open, the more the Waitlist actually turns over — and Waitlist turnover is the feature this project is built to show.
- **A withdrawn student may register again, and returns to the tail of the queue.** Restoring a former position would mean storing position history, which is state that exists only to serve an edge case.
- **After registration closes, promotion continues; new registrations stop.** Closing means "no new entrants", not "stop filling empty Seats" — filling them is the entire point of holding a queue.
- **When the Event starts, the Seat Ledger freezes.** From that moment `enrolled` is the definitive roster the door checks against, which is the input the QR check-in decision consumes.
- **The Waitlist is never cleared after the Event ends.** It is the evidence of demand and feeds the dashboard's Waitlist-conversion metric.
- **Idempotency.** The `$ne` guards make a repeated or double-clicked registration a no-op that reports "already registered" rather than an error, and make double promotion impossible.

### The multi-document transaction self-check

The map's guardrails require an honest answer to where transactions are genuinely needed, and treat "nearly everywhere" as proof the modelling is wrong.

**On the registration path, Core needs none.** Taking a Seat, joining the queue, withdrawing and promoting are each one write to one document. Writing the form answers is a second write, but it is declared eventually consistent by design rather than made atomic by machinery.

One genuine candidate exists outside this path: cancelling an Event must both close the Event and release its Venue slot — two documents. That belongs to the Venue decision, and the expected answer there is idempotent cleanup rather than a transaction.

Two consequences follow:

- **Core runs MongoDB without requiring transactions**, which also keeps the Testcontainers setup on a plain single node.
- **If the real-time attendee count is implemented with change streams**, a replica set becomes necessary for that reason instead — a dependency for the check-in and technical-baseline decisions to settle, not this one.

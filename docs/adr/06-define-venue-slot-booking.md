# Define the Venue and Slot booking model

Type: grilling
Status: resolved
Blocked by: 04

## Question

How is a Venue booked for a time slot such that two Events can never collide, using the same document-atomicity approach settled for capacity?

## Answer

### The Venue-Day document

A **Venue-Day** document holds one Venue's bookings for one calendar date: `{ venueId, date, bookings: [ { eventId, startMinute, endMinute } ] }`, with a unique index on `(venueId, date)`.

Every booking for a given Venue on a given day therefore lives in **one document**, which is what makes collision detection a single atomic write:

```
findOneAndUpdate(
  { venueId, date,
    bookings: { $not: { $elemMatch: {
        startMinute: { $lt: newEnd },
        endMinute:   { $gt: newStart } } } } },
  { $push: { bookings: { eventId, startMinute, endMinute } } },
  { upsert: true } )
```

The `$not`/`$elemMatch` guard **is** the interval-overlap predicate, evaluated by the server as part of the write. There is no read-then-insert window, so there is nothing to lock. A losing writer gets `null` and the Venue is unchanged.

The `upsert` creates the day's document on first use. Two concurrent first bookings race to insert; the unique index on `(venueId, date)` means one wins and the other fails with a duplicate-key error, which the application retries once against the now-existing document.

Rejected: **a separate Booking collection**. Collision detection becomes query-then-insert with a race between the two steps, closable only with a transaction — precisely the machinery this project is built to do without.

### Interval semantics — the part inherited from BookInn

Slots are **half-open**: `[startMinute, endMinute)`. Two intervals overlap if and only if `aStart < bEnd && aEnd > bStart`. Consecutive bookings — 10:00–11:00 and 11:00–12:00 — therefore do **not** collide.

This rule, and that predicate, are what carries over from BookInn. **The implementation does not**: BookInn resolved the same problem with JPA entities, `SELECT … FOR UPDATE` and a relational transaction, none of which exists here. What was reused is the knowledge of which boundary convention avoids the off-by-one arguments, paid for once already.

Times are stored as **minutes from midnight** alongside the date, which keeps a day's arithmetic inside one document and away from timezone handling.

**An Event may not span midnight.** A crossing booking would touch two Venue-Day documents and reintroduce the multi-document write this model exists to avoid. The constraint is honest for campus events and is stated as a validation rule, not worked around.

**Amendment — the projection is lossy for one hour on two days a year, and those Slots are refused.** This section said minutes from midnight keeps a day's arithmetic "away from timezone handling". That is true of the arithmetic and false of the projection: turning an instant into `(date, minuteOfDay)` requires a timezone, and [the API and time contract](15-define-http-api-and-time-contract.md) settles it as `Europe/Dublin`, which observes daylight saving.

On the autumn transition the local hour `[01:00, 02:00)` occurs **twice**, so two bookings an hour apart in real time project onto identical `(date, minuteOfDay)` pairs and the overlap guard cannot tell them apart. On the spring transition that same local hour does **not exist**, and a naive conversion moves a time silently forward by an hour.

**A Slot may not intersect `[01:00, 02:00)` local on a daylight-saving transition date**, refused in validation with `SLOT_IN_DST_TRANSITION` and covered by fixed-clock tests on both dates. Rejected: storing each booking's UTC offset, which puts timezone arithmetic back inside the `$elemMatch` guard this model exists to keep simple. Both constraints — no crossing midnight, no crossing a transition — are the same price paid for the same atomicity, and naming the second is what stops the first from looking like the only one.

### Ordering rule: acquire before releasing

Rescheduling an Event means taking a new Slot and releasing the old one — two writes, possibly on two documents.

**Always take the new Slot first, then release the old.** If the second write fails, the Event holds two Slots: wasteful, visible, repairable, and never double-booked. Releasing first and failing to acquire would lose the room to somebody else with no way back. This ordering is a general rule for this codebase, not a one-off.

### Cancellation, and the multi-document candidate

Cancelling an Event must close the Event and release its Slot. This is the one genuine two-document case the registration decision flagged, and the answer is **idempotent cleanup, not a transaction**.

- The Event is cancelled first, because that is the user-visible truth.
- The Slot release is a `$pull` by `eventId`: idempotent, safe to repeat, harmless if it already happened.
- If the release never lands, an **orphan booking** holds a room for an Event that is no longer happening. Because every booking carries its `eventId`, an orphan is always detectable by joining against the Event's Status.
- **Orphans self-heal where they matter.** When a booking attempt collides, the colliding bookings' Events are checked; any belonging to a Cancelled Event is released and the attempt retried once. The Venue-Day view does the same when rendered.
- A periodic reconciliation sweep is Future Work — a safety net, not the mechanism.

This is the honest answer the map's guardrails asked for: the one place Core genuinely wants a transaction is handled by making the second step idempotent and self-correcting instead.

### Rules

- **University Admins create and manage Venues.** Club Officers book them. This is one of the three responsibilities that make the University Admin role substantive without an approval workflow.
- **Booking is first-come-first-served with no approval step.** An approval queue would put a human in front of the atomic write and hide the behaviour this feature exists to demonstrate.
- **Slots are arbitrary start and end times**, not fixed periods. Campus events do not fit a lecture timetable.
- **No buffer or turnaround time** between bookings in Core. Future Work.
- **No recurring bookings** in Core. Future Work.
- A Venue is optional for an Event, so this decision never blocks the Event lifecycle.
- Releasing a Slot for an Event that has already started is not permitted; the room was used.

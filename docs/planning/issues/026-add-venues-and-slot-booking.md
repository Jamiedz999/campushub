# CH-026 · Add Venues and Slot booking

Sprint: 3
Area: venue
Blocked by: 022
Decisions: [Venue and Slot booking](../../adr/06-define-venue-slot-booking.md)

## Change

- `venue` module: Venue documents managed by University Admins, and Venue-Day documents `{ venueId, date, bookings[] }` with a Mongock-created unique index on `(venueId, date)`.
- Booking is **one guarded upsert** whose filter is the half-open overlap predicate expressed as `$not`/`$elemMatch`. A duplicate-key error from the concurrent first insert is retried once.
- Times stored as minutes from midnight. Events crossing midnight are rejected by validation.
- Release is an idempotent `$pull` by `eventId`.
- Cancelling an Event cancels first, then releases. Orphaned bookings — those whose Event is Cancelled — are detected and released when a booking attempt collides with one, and when the day view renders.
- Rescheduling **acquires the new Slot before releasing the old**.
- Officer surfaces: the Venue-day timeline, booking, and a clear refusal when the write loses.

## Acceptance

- **Two Events can never hold overlapping Slots in one Venue**, under concurrent booking.
- Back-to-back bookings at a shared boundary both succeed.
- A failed reschedule leaves the club holding two Slots and never zero.

## Tests

Concurrency test: parallel bookings of overlapping ranges yield exactly one winner. Boundary tests for the half-open rule at the shared instant. An orphan self-healing test. A cross-midnight rejection test.

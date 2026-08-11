# Define QR check-in and its anti-fraud properties

Type: grilling
Status: resolved
Blocked by: 04

## Question

How is attendance captured, and what specifically stops a student who is not present from checking in?

## Answer

### Direction: the door displays, the Student scans

A Club Officer opens a **door screen** that displays a QR code refreshing every 60 seconds. Students scan it with their own phone, already signed in.

The rejected alternative — each Student presents a personal code that a staff member scans — needs a staffed scanning device and produces a queue, and its code is a **static secret tied to a person**, so a screenshot forwarded to a friend checks that friend in from anywhere.

Reversing the direction splits the two things that must be proven, and gives each to whichever party can actually prove it:

- **The rotating code proves presence.** It is worthless a minute later, so possessing it means having seen a screen that is in the room.
- **The authenticated session proves identity.** The code says nothing about who scanned it; the signed-in Student does.

Neither half alone admits anyone.

### The rotating token

The displayed code carries the Event identifier, a **window index** — the current time divided by 60 seconds — and an HMAC over both, computed with a server-side secret. The server recomputes the HMAC on receipt and rejects anything that does not match.

**The server accepts the current window and the one before it.** A code scanned at the moment it rotates, or on a phone whose clock is slightly off, still works; the effective lifetime is between 60 and 120 seconds. Widening this window trades presence assurance for tolerance, and two windows is the smallest value that survives ordinary scan latency.

Nothing about the token is stored. It is verified by recomputation, so there is no token table, no cleanup, and no state to get out of step.

### What this actually defends against — and what it does not

**It defeats** forwarding a screenshot to someone off campus, publishing a code in a group chat, and checking in before or after the event from elsewhere. All of these die with the window.

**It does not defeat** a Student who is in the room relaying each fresh code to a friend in real time. Nothing short of per-Student challenges or location attestation would, and both are out of scope.

This is the honest boundary: the design removes casual proxy attendance, not determined collusion. Stating that plainly is worth more than a claim the mechanism cannot support.

### Attendance is part of the Seat Ledger

Check-in appends to an `attendance` array on the Event document — `{ studentId, at, method }` — which makes it one atomic guarded write, consistent with every other seat operation:

```
findOneAndUpdate(
  { _id: eventId,
    status: Published,
    enrolled: studentId,
    "attendance.studentId": { $ne: studentId } },
  { $push: { attendance: { studentId, at: now, method: SCANNED } } } )
```

- **`enrolled: studentId` is the Roster check.** Only Students frozen into the Roster when the Event started may check in; someone still on the Waitlist cannot, however early they arrive.
- **The `$ne` guard makes check-in idempotent.** A second scan by the same Student is a no-op reported as "already checked in", not an error and not a duplicate row.
- **The token is not single-use.** Everyone in the room scans the same code in the same window — that is the design, not a weakness. Replay protection comes from the idempotency guard, not from consuming the token.

### The check-in window

Open from **15 minutes before `startsAt` until `endsAt`**. Arriving early is normal; arriving after the event has ended is not attendance.

**Lateness is derived, not stored.** A record whose `at` is later than `startsAt` plus a grace period is displayed as late. Storing a `late` flag would be storing a clock reading, the same mistake the Event lifecycle decision already rejected.

### Manual override

A Club Officer may mark an enrolled Student present when a phone fails, a scan will not read, or the screen is down.

**The record stores `method: MANUAL` and is always distinguishable from a scanned one.** An override that looks identical to a scan destroys the meaning of every scan — the attendance data would no longer support any claim about who was actually there. The dashboard shows the split.

### Weak network and offline

**Core has no offline queueing.** A scan that cannot reach the server fails visibly and is retried; the door screen keeps working because it only renders a code. Offline capture would need a client-side queue, replay-on-reconnect and conflict handling, and would let a device hold attendance the server cannot see. Future Work.

### The live attendee count

The door screen shows attendance climbing as people arrive. The business requirement is narrow: **one-way, server to client, for one Event, visible to the Club Officer running the door, and a few seconds of latency is fine.**

The transport — Server-Sent Events, WebSocket, or plain polling — and whether it is driven by MongoDB change streams are technical choices, deferred to [the technical baseline](12-lock-core-technical-baseline.md). Two constraints bind that decision:

- The channel carries **refresh hints, never authoritative state**; every client reconnect re-reads an authorized snapshot.
- Change streams require a MongoDB replica set, which the registration decision established Core does not otherwise need. Choosing them means accepting that cost for this feature alone.

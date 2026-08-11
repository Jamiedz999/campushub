# CH-028 · Add the live attendee count over WebSocket

Sprint: 4
Area: realtime
Blocked by: 027
Decisions: [technical baseline](../implementation/TECHNICAL-BASELINE.md)

## Change

- `realtime` module: a raw `WebSocketHandler`, per-Event subscription scopes, and an `AttendanceBroadcaster` seam with a real fan-out in production and a recording no-op in tests.
- The request that writes attendance publishes a **refresh hint** — never a count, never state.
- The door screen subscribes, re-reads an authorized snapshot on every hint and on every reconnect, and degrades to periodic re-read if the socket is unavailable.
- Subscription is authorized: only an officer of the owning Club may subscribe to an Event's door scope.

## Acceptance

- A missed message or a dropped connection never leaves the screen wrong, because reconnect re-reads.
- No authoritative state travels over the socket.
- An officer of another Club cannot subscribe.

## Tests

A test asserting the hint payload carries no counts. A reconnect test proving convergence after missed messages. A negative subscription-authorization test.

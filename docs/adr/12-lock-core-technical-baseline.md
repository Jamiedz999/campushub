# Lock the Core technical baseline

Type: grilling
Status: resolved
Blocked by: 02, 08, 10, 11

## Question

What exactly is built on, and where are the module seams?

## Answer

The operational reference is [`TECHNICAL-BASELINE.md`](../planning/implementation/TECHNICAL-BASELINE.md), which is the single source for versions, repository shape, module boundaries and build contracts. This record holds the reasoning behind the choices that were genuinely contested.

### Server-side sessions, not JWT

Chosen by the user over the JWT access/refresh pair BookInn uses.

A same-origin monolith is the case sessions were designed for, and JWT here would be the textbook over-application of a stateless mechanism. Two consequences settle it beyond taste: the authorization decision requires the caller's **Club grants on every request**, and putting a growing grant set inside a token bloats it and makes every grant change wait for expiry; and granting or revoking officer rights must **take effect immediately**, which a stateless token cannot do without a revocation list — a session store by another name.

The cost is accepted knowingly: Delivery Glance also uses sessions, so this adds no new authentication keyword. BookInn already covers JWT at the portfolio level, and "I chose sessions here and JWT there, for these reasons" is a stronger answer than either alone.

### WebSocket with an in-process broadcast, not SSE and not change streams

Chosen by the user, and it is the one place in this project where a keyword influenced a technical decision. That is recorded plainly rather than dressed up.

The requirement from the check-in decision is one-way, single-event, seconds-tolerant — which SSE serves exactly, and which Delivery Glance already demonstrates. WebSocket is bidirectional and this use is not, so it is chosen for portfolio breadth, and the ADR says so.

What was **not** conceded is the cost that usually rides along. MongoDB change streams would have been the "proper" source of realtime events, and they require a replica set — which [the registration decision](04-define-registration-capacity-and-waitlist.md) established Core otherwise does not need, and which would complicate every Testcontainers run for one feature. The broadcast is therefore **in-process**: the same request that writes attendance publishes the hint.

The honest limitation: this is correct for one instance and silently wrong for several, because a second instance's clients would never hear the event. Core is one instance. Horizontal scale would need a shared broker, and that is Future Work, not a hidden assumption.

The channel carries **refresh hints, never authoritative state**, so a client that misses a message or reconnects re-reads a snapshot and is correct again.

### `MongoTemplate` only, with no repository interfaces

Every operation that carries weight in this system is a guarded conditional update or an aggregation: `findAndModify` with a capacity guard, an aggregation-pipeline update that moves the Waitlist head, an `$elemMatch` overlap guard on a Venue-Day. **Derived repository methods cannot express any of them.**

Offering both a repository layer and `MongoTemplate` would mean two routes to the database, and the important operations are exactly the ones that would drift to the wrong one. One API, used consistently.

`spring-boot-starter-data-mongodb` is still the dependency, so Spring Data MongoDB remains an accurate claim. This also closes out the trade the map recorded: giving up the Spring Data JPA gate item on this project was a conscious choice, and BookInn covers JPA for the portfolio.

### Mongock for schema evolution

A schemaless store still has a schema; it is just enforced by the application instead of the server. Versioned change units give the same discipline Flyway gives BookInn — committed, ordered, reviewable — and they own every index, including the two uniqueness constraints the concurrency design depends on.

Runtime index creation from annotations is disabled. An index that appears because a class was annotated is an index nobody reviewed, and here indexes are load-bearing: `(venueId, date)` is what makes the Venue-Day upsert race safe.

"How do you do migrations without a schema?" is a question this project will be asked, and having a real answer is worth one dependency.

### ECharts, and Cypress

**ECharts** over BookInn's Recharts, for library breadth and because the dashboard's grouped comparisons are what it is good at. The dashboard decision already put every metric definition in pure functions, so the charting library sits above the tested layer and the coverage gate is unaffected by the switch.

**Cypress** over Playwright, chosen by the user to match the rubric's named keyword exactly. Playwright is faster in CI and already used on Delivery Glance; the trade is a slower pipeline for a precise keyword match and one more tool covered across the portfolio.

### A module owns whole documents

The structural rule that keeps the concurrency claim true: two modules never write the same document. If the Seat Ledger could be mutated from more than one place, "every race is resolved by a single guarded write" would depend on everyone remembering, which is the failure mode the authorization decision already rejected in a different guise.

This is why `event` is a large module — it owns the Event document, and registration, promotion, withdrawal and attendance all live in it — and why `checkin` verifies tokens but hands the verified pair to `event` rather than writing attendance itself.

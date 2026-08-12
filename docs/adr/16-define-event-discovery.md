# Define Event discovery — search, filter, sort and paging

Type: grilling
Status: resolved
Blocked by: 03, 15

## Question

How does a Student find an Event, and which of those operations can actually use an index?

## Answer

### Why this needed a decision after all

[The Core boundary document](../planning/13-set-core-boundary-and-sprints.md) called this "the one Core feature no ADR needed because nothing about it is contested". That was wrong, and the reason it was wrong is instructive: it was not contested because nobody had looked at it. It is also the rubric's beyond-CRUD gate item, which makes it the least specified load-bearing thing in the plan.

Looking at it surfaces a real conflict with a decision already made.

### The conflict: Phase is derived, and derived values cannot be indexed

[The Event lifecycle decision](03-define-event-lifecycle.md) stores three Statuses and derives eight Phases, on the grounds that a stored Phase can contradict the timestamps beside it. That decision is correct and stands.

But the filter a Student most wants is "Events I can still register for" — which is a **Phase**, and a Phase exists only at read time. So discovery has to be split by what the database can and cannot do with an index:

| Filter | Expressed as | Indexable |
|---|---|---|
| Open for registration | `status: Published` and `registrationOpensAt ≤ now < registrationClosesAt` | **Yes** — plain comparisons on indexed fields |
| Upcoming / date range | `startsAt` range | **Yes** |
| By Club | `clubId` | **Yes** |
| Not yet started | `startsAt > now` | **Yes** |
| **Has a free Seat** | `$expr: { $lt: [ { $size: "$enrolled" }, "$capacity" ] }` | **No** — computed per document |

The first four are Phases in the user's language and indexed comparisons in the query. **The Phase concept survives contact with the query planner everywhere except capacity**, because capacity is the one part of a Phase that depends on an array's length rather than on the clock.

**All five predicates go in one `$match` stage.** MongoDB uses the index for the indexable conjuncts and evaluates the `$expr` on what survives. Keeping them in one stage is what makes `total` correct — splitting the `$expr` into a later stage would produce a page count that disagrees with the page.

Rejected: **denormalising `seatsLeft` onto the Event.** It would be indexable, and `$inc` in the same guarded write would keep it consistent. It is rejected because it is a second copy of a fact the Seat Ledger already holds, and the whole registration design rests on the Seat Ledger being the single authority for how many Seats are taken. A second counter is one refactor away from being the one the capacity guard reads.

**The threshold that would change this**, recorded so the position is falsifiable in the same way [the dashboard's](09-define-attendance-dashboard.md) is: a browse query exceeding roughly 200 ms, or Event counts in the tens of thousands. At hundreds of Events, an indexed match producing a page-sized candidate set costs nothing measurable.

### Search

**A MongoDB text index over `title` and `description`, created by Mongock.**

Rejected: **regex matching**, which cannot use an index for an unanchored pattern, drags case-sensitivity and escaping into every query, and degrades linearly with the collection. Rejected: **Atlas Search**, which is a second service and is unavailable on the single node Core runs on.

The text index is the only index in the system that Mongock creates for a read path rather than for a correctness guarantee; that is noted where it is defined, so nobody later assumes it is load-bearing for concurrency.

### Sort

Three keys, and one deliberate omission:

- **`startsAt` ascending** — the default, and what "what's on" means.
- **`startsAt` descending** — for browsing what has passed.
- **`createdAt` descending** — newly published Events. Free, since `ObjectId` carries its creation time.

**Sorting by seats remaining is not offered.** Filtering by it is per-document and cheap; sorting by it requires computing the value for every matching Event and ordering the whole set in memory before a page can be cut. That is the one operation in discovery that does not degrade gracefully, and dropping it costs a Student nothing that the "has a free Seat" filter does not already give them.

**When a search term is present, the default sort is relevance** (`textScore`); otherwise it is `startsAt` ascending. An explicit sort parameter overrides either. Sorting by `startsAt` while searching silently discards relevance, so the default has to depend on the query rather than being fixed.

### What is visible at all

- **Draft Events never appear in discovery**, to anyone, including the owning Club's Officers. An Officer reaches their Drafts through the Club console, which is a scoped query and a different surface.
- **Cancelled Events do not appear in discovery** either — nobody browses for something that is not happening. A Student who holds a Seat on a Cancelled Event still sees it in their own event list, showing the Cancelled Phase, because [the Seat Ledger freezes rather than clearing](03-define-event-lifecycle.md) and erasing it from their view would erase the only signal that it was called off.
- Discovery therefore matches `status: Published` unconditionally, and that predicate is not a user-facing filter.

### Paging

The envelope, defaults and cap come from [the API contract](15-define-http-api-and-time-contract.md): `{ items, page, size, total }`, `size` defaulting to 20 and capped at 100.

**Deep paging is not defended against.** Skip-based paging degrades at high offsets, and a keyset cursor is real machinery for a collection of hundreds. The cap on `size` is the only limit Core carries.

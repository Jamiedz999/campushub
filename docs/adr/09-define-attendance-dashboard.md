# Define the attendance dashboard

Type: grilling
Status: resolved
Blocked by: 07

## Question

What does the dashboard show, how are its numbers computed, and who sees which view?

## Answer

### The metrics, with their denominators fixed

A metric whose denominator is ambiguous is a metric that collapses under one interview question, so each is defined exactly:

| Metric | Definition |
|---|---|
| **Fill rate** | `enrolled / capacity` — how much of the room was claimed |
| **Attendance rate** | `attended / enrolled` — **against enrolled, not capacity** |
| **No-show rate** | `1 − attendance rate` |
| **Waitlist conversion** | `promotedCount / everQueuedCount` — the share of everyone who ever queued that got in |
| **Unmet demand** | `waitlist length` at Event end — people who wanted in and never got in |
| **Manual override share** | attendance records with `method: MANUAL` over all attendance records |
| **Club activity** | Events run, total enrolled and total attended per Club, per month |

Attendance rate is deliberately measured against **enrolled**, because it answers "did the people who committed turn up?" Measuring against capacity would conflate a half-empty event with a no-show problem, which are different failures with different causes.

### Which Events the numbers are computed over

**Amendment — the denominators were fixed and the population was not.** Every metric above names what it divides by, and none of them says which Events are in the set being divided. That is the same ambiguity one level up, and it collapses under the same interview question: a Cancelled Event has a Seat Ledger full of people who never had the chance to attend, so counting it drags every attendance rate down for a reason that has nothing to do with attendance.

**The dashboard reports on Events where `status: Published` and `now ≥ endsAt`. Nothing else is counted.**

- **Draft Events are excluded** — they were never offered to anyone, and "Events run" counting things nobody could see would make club activity a measure of drafting.
- **Cancelled Events are excluded from every metric**, including club activity. The Seat Ledger froze rather than clearing, so the demand they represent is still recorded and still recoverable; it is simply not attendance data, because no attendance was possible.
- **Events still running are excluded**, since their attendance is mid-flight. The door screen is where an in-progress Event is watched, and [the live count belongs to the door](07-define-qr-checkin-and-anti-fraud.md), not here.

The exclusions are shown in the UI as a count — "3 Cancelled Events not shown" — rather than left to be inferred from a total that does not add up. A number that quietly omits rows is a number nobody can check.

**Manual override share** exists because the check-in decision made scanned and manual records distinguishable. A club whose attendance is 80% manual has not demonstrated attendance, and the dashboard should say so.

### Two fields this decision adds to the Seat Ledger

Waitlist conversion cannot be computed from the Seat Ledger as the registration decision left it: a promoted Student moves out of `waitlist` and into `enrolled`, so by the time anyone asks, the numerator is gone.

**The Event document carries a `promotedCount`, incremented in the same atomic pipeline update that performs the promotion.** One integer, no extra write, no extra document — and it makes the flagship feature measurable instead of merely working. This amends [the registration decision](04-define-registration-capacity-and-waitlist.md), which is annotated accordingly.

**Amendment — the denominator needed a second field.** Conversion was first written as `promotedCount / (promotedCount + waitlist length)`, on the assumption that everyone who ever queued is either still queued or was promoted. A Student who leaves the Waitlist is neither: the `$pull` removes them from `waitlist` and nothing counted them. The metric therefore overstated conversion by exactly the number of people who gave up — the group whose departure is the strongest evidence the queue was too slow.

**The Event carries an `everQueuedCount`, incremented in the guarded write that appends to `waitlist`**, and conversion is `promotedCount / everQueuedCount`. Unmet demand is unaffected: it is the queue's length at the end, and someone who left is not unmet demand, they are a lost registrant. The two numbers now measure different things on purpose.

### Computed live, and the conditions that would change that

Every number is computed on read by a **MongoDB aggregation pipeline**. There is no pre-aggregated collection and no scheduled ETL in Core.

This is correct at this scale — hundreds of Events, tens of thousands of attendance entries — where an indexed aggregation answers in milliseconds. Pre-aggregating now would be building a cache for a problem that does not exist, and the map's guardrails forbid exactly that.

**What would force the change**, recorded so the position is falsifiable rather than merely asserted: a dashboard query exceeding roughly one second, or Event counts in the tens of thousands. That threshold is also the honest opening for a future measured performance experiment — profile first, then decide between indexes, pre-aggregation and caching. That experiment is Future Work and is explicitly not a reason to add Redis to Core.

### The two views

- A **Club Officer** sees their own Club: its Events, each Event's metrics, and the Club's trend over time.
- A **University Admin** sees every Club, plus a cross-club comparison — which societies run the most events, which fill, which are chronically over-subscribed. No individual form answers appear in either view; the authorization decision holds that boundary.

Time range is a control on both views. **No drill-down in Core** beyond opening a single Event's own page.

### Charts

Chart types are settled here; the library is not.

- Attendance rate and fill rate over time — line.
- Enrolled against attended per Event — grouped bar.
- Waitlist conversion — a single figure with its two components, not a pie.
- Club comparison — horizontal bar, sorted.

The charting library is deferred to [the technical baseline](12-lock-core-technical-baseline.md) as a technical choice.

### The live attendee count is not here

It belongs to the door screen, which the check-in decision owns. The dashboard reports on Events that have finished or are running; it is not a real-time surface, and giving it one would duplicate the door.

### How this survives the 90% frontend coverage gate

Charts are awkward to test, and the gate is not negotiable, so the split is decided now rather than argued later:

- **The data-shaping functions** — turning an API response into series, computing rates, bucketing by month — are pure functions with no rendering, and are unit tested to the full bar. This is where every one of the definitions above actually lives, so this is where the tests matter.
- **The chart components** are smoke tested: they render, with the expected number of series and the expected accessible labels.

Testing the arithmetic thoroughly and the drawing lightly is the honest allocation, and it keeps the gate meaningful instead of turning it into snapshot padding.

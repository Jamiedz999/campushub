# Define per-Event custom registration forms

Type: grilling
Status: resolved
Blocked by: 03

## Question

What can a club ask its registrants, how is that captured, and how are the answers validated, stored and read back?

## Answer

This feature is MongoDB's justification for existing in this project, so it is specified as a first-class capability rather than a text box. Per the map's guardrails it cannot be cut for scope.

**Why the document model earns its place here.** Every Event defines its own fields, so the shape of an answer set is data, not schema. A relational store would have to choose between an entity-attribute-value table — three joins to read one form, no type safety, unindexable answers — or a JSON column, which is a document store wearing a relational coat. Here the form definition and the answers are simply documents that differ in shape, which is what the database is for. This paragraph is the ADR's whole argument; if the feature is ever removed, the database choice must be revisited with it.

### Where the two halves live

- **The form definition lives on the Event document.** It is part of what the Event *is*, it is small, and it is read whenever the Event is read — including by the registration write that must validate against it.
- **The answers live on the Registration document**, in its own collection, keyed by `(eventId, studentId)` with a unique index. Registrations hold answers and nothing else; Seats are the Seat Ledger's business.

**Amendment — Registrations carry an answer fence, not a Seat (implementation Issue #6, 2026-08-14).** "Answers and nothing else" failed when a Student withdrew, registered again, and an old answer request arrived last: the unique `(eventId, studentId)` document had no way to tell which period of being Enrolled those answers belonged to. A Registration now copies the current Seat Ledger entry's `enrollmentVersion` beside its answers. That value grants no Seat and the Seat Ledger remains the sole authority; it only makes the eventual write and every read prove that the answers belong to the exact current enrollment.

Writes are fenced by the monotonic version assigned inside the atomic Seat Ledger write: the same or a newer `enrollmentVersion` may update the unique Registration, while an older one is rejected atomically. Missing/null versions from documents created before this amendment form the legacy incarnation and sort below every assigned version. This preserves old answers while both sides are legacy, lets the next enrollment replace them, and prevents a delayed legacy retry from overwriting current answers. [The Seat Ledger decision](04-define-registration-capacity-and-waitlist.md) records how the version is generated with the Seat and therefore follows successful write order rather than request-start order.

### Field types

Five, and no more: **short text, long text, single choice, multiple choice, number**.

**File upload is excluded.** It would require object storage, size limits, content-type checking, malware scanning and a deletion policy — a whole subsystem behind one field type. Future Work.

Each field carries a stable **`fieldId`**, a label, an optional help text, a `required` flag, and type-specific constraints: maximum length for text, minimum and maximum for number, and the option list for the choice types.

**Answers are keyed by `fieldId`, never by label.** Labels are display strings that a Club Officer may fix a typo in; keying answers by them would make the data's meaning depend on its presentation.

### Validation

The server validates every submitted answer set against the Event's own form definition, inside the same request that takes the Seat. Required fields must be present, text must be within length, numbers within range, and choice answers must be drawn from the defined options — an answer naming an option that does not exist is rejected rather than stored.

The client validates from the same definition for feedback only. **The server's check is the one that counts**, and it is not skippable by a crafted request.

**Amendment — what happens when the answers write fails after the Seat is won.** [The registration decision](04-define-registration-capacity-and-waitlist.md) accepts that the answers are a second, non-atomic write, and calls the resulting state "detectable, repairable, and irrelevant to who got the Seat". It never said how it is detected or who repairs it, which left an accepted inconsistency with no user-visible handling — the worst kind, because it looks decided.

**The Seat stands.** It is the authoritative outcome and the Student holds it; failing the whole request to keep the two writes in step would throw away the only part that was contended. The response reports the registration as succeeded and flags the answers as unsaved, the Student's own event list shows the Registration as incomplete with a retry that writes only the answers, and the officer's answers table distinguishes an unsaved Registration from one whose optional fields are genuinely empty.

Validation therefore runs **before** the Seat write, not after: a form that would be rejected should never consume a Seat. What can still fail afterwards is the write itself, not the content.

This is repair by making the state visible to the one person motivated to fix it, rather than by a reconciliation sweep — which remains Future Work, and which would be a safety net over a mechanism that already tells someone.

### The form locks once the first Registration exists

While an Event has no Registrations, its form is freely editable. From the first Registration onward, **the form definition is immutable**.

The alternative is versioned definitions: each Registration remembering which version it answered, and every reader resolving answers against the right one. That is real machinery for a case that barely arises — a Club Officer who realises the form is wrong before anyone has signed up can still fix it, and one who realises afterwards has a handful of registrants to ask directly.

This is why answers stay interpretable without a version field: the definition they were written against cannot have changed.

### Reading answers back

- The owning **Club Officer** sees a table of registrants against their answers, and per-option counts for the choice fields.
- **CSV export** of the same table. It is the one operation a club genuinely needs outside the product, and it is a few lines over an already-shaped table.
- **Nobody else sees individual answers** — not other Students, not University Admins. The authorization decision holds the full privacy boundary.

### Rendering a form the frontend did not know about

The form definition is a **discriminated union on field type**; the renderer switches on that tag and TypeScript narrows each branch to the props that field type actually has. Answers are a map from `fieldId` to a union of answer values, narrowed the same way.

Strict TypeScript is preserved without a single `as`: what is unknown at compile time is *which* fields exist, not what a field of a given type looks like.

### An Event may have no form

Most events just need a name on a list. An empty form definition is valid and makes registration a single click, with no empty form step in the way.

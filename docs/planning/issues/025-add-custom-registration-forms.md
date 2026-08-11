# CH-025 · Add per-Event custom registration forms

Sprint: 3
Area: event, registration
Blocked by: 024
Decisions: [custom registration forms](../../adr/05-define-custom-registration-forms.md)

## Change

- Form definition on the Event document: an ordered list of fields, each with a stable `fieldId`, label, help text, `required`, and type-specific constraints. Five types: short text, long text, single choice, multiple choice, number.
- `registration` module owning Registration documents keyed `(eventId, studentId)` with a unique index, holding answers keyed by `fieldId`.
- Server-side validation against the Event's own definition, inside the request that takes the Seat. An answer naming an undefined option is rejected.
- The definition locks once the first Registration exists.
- Officer form builder; answers table with per-option counts; CSV export.
- Frontend renderer driven by a **discriminated union on field type**, with no `as` assertions.
- Losing the race preserves the Student's answers client-side and carries them into the waitlist join.

## Acceptance

- An Event with no form registers in one action with no empty step.
- Editing a locked form is refused with a clear reason.
- Answers remain interpretable with no version field, because the definition cannot have changed.

## Tests

Validation tests per field type including the undefined-option case. A lock test. A round-trip test from builder to CSV. Frontend tests rendering a form the test itself defines.

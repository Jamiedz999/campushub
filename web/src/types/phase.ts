// Mirrors com.campushub.event.domain.Phase — see docs/adr/03-define-event-lifecycle.md. Shared across
// features (rather than duplicated in each feature's types.ts) because a feature may not import from
// another feature — see docs/planning/implementation/TECHNICAL-BASELINE.md.
export type Phase =
  | "DRAFT"
  | "SCHEDULED"
  | "REGISTRATION_OPEN"
  | "FULL"
  | "REGISTRATION_CLOSED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

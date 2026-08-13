// Mirrors com.campushub.event.web.EventRegistrationView — see
// docs/adr/04-define-registration-capacity-and-waitlist.md and
// docs/adr/15-define-http-api-and-time-contract.md.
//
// Phase is duplicated from features/events/types.ts rather than imported: a feature may not import from
// another feature, enforced by ESLint import/no-restricted-paths — see
// docs/planning/implementation/TECHNICAL-BASELINE.md.
export type Phase =
  | "DRAFT"
  | "SCHEDULED"
  | "REGISTRATION_OPEN"
  | "FULL"
  | "REGISTRATION_CLOSED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

export interface EventRegistrationView {
  id: string;
  clubId: string;
  title: string;
  description: string;
  phase: Phase;
  registrationOpensAt: string;
  registrationClosesAt: string;
  startsAt: string;
  endsAt: string;
  capacity: number;
  enrolledCount: number;
  waitlistCount: number;
  /** Whether the signed-in Student themselves already holds a Seat — never who else does. */
  enrolled: boolean;
}

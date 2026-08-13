// Mirrors com.campushub.event.web.EventRegistrationView — see
// docs/adr/04-define-registration-capacity-and-waitlist.md and
// docs/adr/15-define-http-api-and-time-contract.md. Phase lives in ../../types/phase, shared with
// features/events rather than duplicated — a feature may not import from another feature, but shared
// code may move down into types/lib — see docs/planning/implementation/TECHNICAL-BASELINE.md.
import type { Phase } from "../../types/phase";

export type EnrollmentVia = "DIRECT" | "PROMOTED";

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
  /** Why this Student holds their Seat; null when they do not hold one. */
  enrollmentVia: EnrollmentVia | null;
  /** One-based position for this Student only; null when they are not waiting. */
  waitlistPosition: number | null;
}

// Mirrors com.campushub.event.domain.Phase and the DTOs in com.campushub.event.web — see
// docs/adr/03-define-event-lifecycle.md and docs/adr/16-define-event-discovery.md.
export type Phase =
  | "DRAFT"
  | "SCHEDULED"
  | "REGISTRATION_OPEN"
  | "FULL"
  | "REGISTRATION_CLOSED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

export type EventSort = "STARTS_AT_ASC" | "STARTS_AT_DESC" | "CREATED_AT_DESC" | "RELEVANCE";

export interface EventBrowseItem {
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
}

export interface EventBrowseFilters {
  q?: string;
  openForRegistration?: boolean;
  hasFreeSeat?: boolean;
  startsAtFrom?: string;
  startsAtTo?: string;
  sort?: EventSort;
  page: number;
  size: number;
}

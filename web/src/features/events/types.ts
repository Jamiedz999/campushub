// Mirrors the DTOs in com.campushub.event.web — see docs/adr/03-define-event-lifecycle.md and
// docs/adr/16-define-event-discovery.md. Phase itself lives in ../../types/phase — shared with
// features/registration, which renders the same Phase for a single Event.
export type { Phase } from "../../types/phase";
import type { Phase } from "../../types/phase";
import type { RegistrationForm } from "../../types/registrationForm";

export type EventSort = "STARTS_AT_ASC" | "STARTS_AT_DESC" | "CREATED_AT_DESC" | "RELEVANCE";
export type EventStatus = "DRAFT" | "PUBLISHED" | "CANCELLED";

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

export interface EventOfficerView extends EventBrowseItem {
  status: EventStatus;
  promotedCount: number;
  everQueuedCount: number;
  waitlistConversion: number;
  registrationForm: RegistrationForm;
  registrationFormLocked: boolean;
}

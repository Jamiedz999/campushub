// Mirrors the DTOs in com.campushub.checkin.web and the attendance DTOs in com.campushub.event.web —
// see docs/adr/07-define-qr-checkin-and-anti-fraud.md.

export type AttendanceMethod = "SCANNED" | "MANUAL";

export interface DoorCode {
  eventId: string;
  title: string;
  /** The rotating code itself. Worthless a minute later, which is what makes it prove presence. */
  token: string;
  rotatesAt: string;
  checkInOpensAt: string;
  checkInClosesAt: string;
  checkInOpen: boolean;
}

export interface CheckInResult {
  eventId: string;
  eventTitle: string;
  at: string;
  method: AttendanceMethod;
}

/**
 * One holder of a Seat and their attendance, if any. "Roster" and "enrolled" are the glossary's words
 * for this — see CONTEXT.md, which lists "attendee" under Avoid, because the Roster says who may
 * attend, not who did. `at` is null until they are marked present, by either route.
 */
export interface RosterEntry {
  studentId: string;
  displayName: string;
  at: string | null;
  method: AttendanceMethod | null;
}

export interface AttendanceRoster {
  eventId: string;
  title: string;
  items: RosterEntry[];
}

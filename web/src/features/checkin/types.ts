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
  capacity: number;
  enrolledCount: number;
  attendedCount: number;
}

export interface CheckInResult {
  eventId: string;
  eventTitle: string;
  at: string;
  method: AttendanceMethod;
}

export interface Attendee {
  studentId: string;
  displayName: string;
  attendedAt: string | null;
  method: AttendanceMethod | null;
}

export interface AttendanceRoster {
  eventId: string;
  title: string;
  capacity: number;
  enrolledCount: number;
  attendedCount: number;
  items: Attendee[];
}

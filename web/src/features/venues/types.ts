export interface VenueSummary {
  id: string;
  name: string;
}

export interface VenuePage {
  items: VenueSummary[];
  page: number;
  size: number;
  total: number;
}

export interface VenueDayBooking {
  eventId: string;
  startMinute: number;
  endMinute: number;
}

export interface VenueDay {
  venue: VenueSummary;
  date: string;
  bookings: VenueDayBooking[];
}

// This feature owns the small Event projection its booking screen consumes. Keeping it here avoids
// coupling one frontend feature to another (ADR 17), while the HTTP endpoint stays the same.
export interface VenueBookingEvent {
  id: string;
  title: string;
  startsAt: string;
  endsAt: string;
  venueId: string | null;
}

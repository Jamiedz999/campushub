// Mirrors DashboardResponse in com.campushub.dashboard.web — see
// docs/adr/09-define-attendance-dashboard.md.
//
// Every field is a count. No rate crosses the wire, because a rate without its denominator beside it
// cannot be checked, and because every division in this feature is a pure function in metrics.ts that
// the coverage gate can hold. The names are the glossary's: enrolled, not attendees; Waitlist length
// at the end is unmetDemand, which is not the same number as everQueued.

/** Numerators and denominators over the whole reported population. */
export interface MetricTotals {
  eventsRun: number;
  capacity: number;
  enrolled: number;
  attended: number;
  promoted: number;
  everQueued: number;
  unmetDemand: number;
  manualAttendance: number;
}

/** One calendar month, `YYYY-MM` in the campus timezone, bucketed by when the Event ended. */
export interface MonthTotals {
  month: string;
  eventsRun: number;
  capacity: number;
  enrolled: number;
  attended: number;
}

export interface ClubTotals {
  clubId: string;
  clubName: string;
  eventsRun: number;
  capacity: number;
  enrolled: number;
  attended: number;
  unmetDemand: number;
}

export interface EventTotals {
  eventId: string;
  title: string;
  clubId: string;
  clubName: string;
  endsAt: string;
  capacity: number;
  enrolled: number;
  attended: number;
  unmetDemand: number;
}

/** What the range covers and the population deliberately leaves out, counted rather than dropped. */
export interface ExcludedEvents {
  draft: number;
  cancelled: number;
  inProgress: number;
}

/** Which of the ADR's two views this payload is. */
export type DashboardScope = "CLUB" | "ALL_CLUBS";

export interface Dashboard {
  scope: DashboardScope;
  from: string;
  to: string;
  totals: MetricTotals;
  months: MonthTotals[];
  clubs: ClubTotals[];
  events: EventTotals[];
  excluded: ExcludedEvents;
}

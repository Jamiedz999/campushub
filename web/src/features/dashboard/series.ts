import { attendanceRate, fillRate } from "./metrics";
import type { ClubTotals, EventTotals, ExcludedEvents, MonthTotals } from "./types";

/**
 * Turning the API's counts into the series each chart draws. Pure, and unit tested to the full bar —
 * the chart components below these are smoke tested only, so anything worth getting wrong has to live
 * here. See docs/adr/09-define-attendance-dashboard.md.
 */

/** How many Events fit on a grouped bar chart before the labels stop being readable. */
export const MAX_EVENT_BARS = 20;

const MONTH_NAMES = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];

/** `2026-03` becomes `Mar 2026`. Anything that is not a bucket key is passed through untouched. */
export function monthLabel(bucket: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(bucket);
  const name = match === null ? undefined : MONTH_NAMES[Number(match[2]) - 1];
  return match === null || name === undefined ? bucket : `${name} ${match[1]}`;
}

/** A series value as a tooltip reads it: a whole percent, or a dash where the month had no answer. */
export function percentTooltip(value: unknown): string {
  return typeof value === "number" ? `${value}%` : "—";
}

export interface RatesOverTime {
  months: string[];
  /** Whole percents, with null where a month had nothing to divide by — a gap, not a crash to zero. */
  fillRate: (number | null)[];
  attendanceRate: (number | null)[];
}

export function ratesOverTime(months: MonthTotals[]): RatesOverTime {
  return {
    months: months.map((month) => monthLabel(month.month)),
    fillRate: months.map((month) => percent(fillRate(asTotals(month)))),
    attendanceRate: months.map((month) => percent(attendanceRate(asTotals(month)))),
  };
}

export interface EnrolledAgainstAttended {
  titles: string[];
  enrolled: number[];
  attended: number[];
  /** How many Events were left off the chart, so the page can say so instead of quietly dropping them. */
  trimmed: number;
}

/**
 * The grouped bar: what each Event enrolled against what it actually got through the door.
 *
 * The API deliberately sends every Event in the range, because the range is what bounds the list.
 * Deciding how many bars fit on a screen is a display decision, so it is made here — and reported,
 * because a chart that silently shows a fifth of the data is the same defect as a total that silently
 * omits rows.
 */
export function enrolledAgainstAttended(
  events: EventTotals[],
  limit: number = MAX_EVENT_BARS,
): EnrolledAgainstAttended {
  const shown = events.slice(0, limit);
  return {
    titles: shown.map((event) => event.title),
    enrolled: shown.map((event) => event.enrolled),
    attended: shown.map((event) => event.attended),
    trimmed: events.length - shown.length,
  };
}

export interface ClubComparison {
  clubNames: string[];
  eventsRun: number[];
  enrolled: number[];
  attended: number[];
}

/**
 * The cross-club comparison, sorted least-active first because a horizontal bar chart's first category
 * sits at the bottom. Ties break by name, so the order never depends on what the database returned.
 */
export function clubComparison(clubs: ClubTotals[]): ClubComparison {
  const sorted = [...clubs].sort(
    (left, right) =>
      left.eventsRun - right.eventsRun || left.clubName.localeCompare(right.clubName),
  );
  return {
    clubNames: sorted.map((club) => club.clubName),
    eventsRun: sorted.map((club) => club.eventsRun),
    enrolled: sorted.map((club) => club.enrolled),
    attended: sorted.map((club) => club.attended),
  };
}

/**
 * The Events people wanted into and never got into, worst first. A table rather than a chart: the
 * question it answers is "which Event should have been bigger", and that is a list of names.
 */
export function unmetDemandRows(events: EventTotals[]): EventTotals[] {
  return events
    .filter((event) => event.unmetDemand > 0)
    .sort(
      (left, right) =>
        right.unmetDemand - left.unmetDemand || right.endsAt.localeCompare(left.endsAt),
    );
}

/**
 * "3 Cancelled Events not shown" — one sentence per reason, and nothing at all when nothing was left
 * out. The exclusions are stated rather than left to be inferred from a total that does not add up.
 */
export function excludedNotices(excluded: ExcludedEvents): string[] {
  return [
    notice(excluded.draft, "Draft Event", "Draft Events"),
    notice(excluded.cancelled, "Cancelled Event", "Cancelled Events"),
    notice(excluded.inProgress, "Event still running", "Events still running"),
  ].filter((sentence): sentence is string => sentence !== null);
}

function notice(count: number, singular: string, plural: string): string | null {
  return count === 0 ? null : `${count} ${count === 1 ? singular : plural} not shown`;
}

function percent(value: number | null): number | null {
  return value === null ? null : Math.round(value * 100);
}

// A month's counts are a population of their own, so the same rate functions apply to them unchanged.
function asTotals(month: MonthTotals) {
  return {
    eventsRun: month.eventsRun,
    capacity: month.capacity,
    enrolled: month.enrolled,
    attended: month.attended,
    promoted: 0,
    everQueued: 0,
    unmetDemand: 0,
    manualAttendance: 0,
  };
}

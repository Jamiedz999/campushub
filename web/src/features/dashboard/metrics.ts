import type { MetricTotals } from "./types";

/**
 * Every metric in docs/adr/09-define-attendance-dashboard.md, each dividing by exactly the denominator
 * that ADR names.
 *
 * This file is where the definitions actually live. The API sends counts, the charts draw whatever they
 * are handed, and the arithmetic between the two is here — pure, and unit tested to the full bar, which
 * is the split the ADR chose so the coverage gate would stay meaningful instead of turning into
 * snapshot padding.
 */

/**
 * A share, or null when nothing was there to divide. Null rather than zero on purpose: "0% turned up"
 * and "no Event ran" are different statements, and only one of them is true of an empty population.
 */
export function rate(numerator: number, denominator: number): number | null {
  return denominator === 0 ? null : numerator / denominator;
}

/**
 * The three counts every fill/attendance question is asked of. Narrower than {@link MetricTotals} on
 * purpose: a month's activity and one Event's row both answer these, and neither has a promotedCount.
 */
export interface Turnout {
  capacity: number;
  enrolled: number;
  attended: number;
}

/** How much of the room was claimed. */
export function fillRate(turnout: Turnout): number | null {
  return rate(turnout.enrolled, turnout.capacity);
}

/**
 * Did the people who committed turn up? Measured against enrolled, never against capacity — measuring
 * against capacity would make a half-empty Event look like a no-show problem.
 */
export function attendanceRate(turnout: Turnout): number | null {
  return rate(turnout.attended, turnout.enrolled);
}

export function noShowRate(turnout: Turnout): number | null {
  const attended = attendanceRate(turnout);
  return attended === null ? null : 1 - attended;
}

/**
 * The share of everyone who ever queued that got in. The denominator is everQueued rather than
 * promoted plus the Waitlist's length, because a Student who joined the queue and then left it is in
 * neither — and their leaving is the strongest evidence the queue was too slow.
 */
export function waitlistConversion(totals: MetricTotals): number | null {
  return rate(totals.promoted, totals.everQueued);
}

/** A Club whose attendance is mostly overrides has not demonstrated attendance. */
export function manualOverrideShare(totals: MetricTotals): number | null {
  return rate(totals.manualAttendance, totals.attended);
}

/**
 * The Students who joined the Waitlist and left it before the Event — everyQueued minus the ones who
 * got in and the ones still queued at the end. They are why conversion divides by everQueued, and the
 * figure prints them rather than leaving the denominator unexplained.
 */
export function waitlistAbandoned(totals: MetricTotals): number {
  return totals.everQueued - totals.promoted - totals.unmetDemand;
}

/** A whole percent, or a dash where there is no answer. */
export function formatRate(value: number | null): string {
  return value === null ? "—" : `${Math.round(value * 100)}%`;
}

export function formatCount(value: number): string {
  return value.toLocaleString("en-IE");
}

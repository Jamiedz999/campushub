import { campusMonthStart } from "../../lib/campusTimeZone";

/**
 * The time-range control. Both views have one, and both express it as whole campus calendar months, so
 * that the window and the month buckets it fills always begin at the same instant.
 */
export type RangeChoice = "3m" | "6m" | "12m" | "24m";

export const RANGE_OPTIONS: { value: RangeChoice; label: string }[] = [
  { value: "3m", label: "Last 3 months" },
  { value: "6m", label: "Last 6 months" },
  { value: "12m", label: "Last 12 months" },
  { value: "24m", label: "Last 2 years" },
];

const MONTHS_BACK: Record<RangeChoice, number> = { "3m": 2, "6m": 5, "12m": 11, "24m": 23 };

/**
 * Where the window starts. Every choice sends a `from`, including the widest: an option that sent none
 * would fall back to the server's own twelve-month default and be a second control producing the same
 * window as "Last 12 months" — two choices, one result, which is worse than having one fewer choice.
 */
export function rangeStart(choice: RangeChoice, now: Date): string {
  return campusMonthStart(now, MONTHS_BACK[choice]);
}

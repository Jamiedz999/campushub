import { campusMonthStart } from "../../lib/campusTimeZone";

/**
 * The time-range control. Both views have one, and both express it as whole campus calendar months, so
 * that the window and the month buckets it fills always begin at the same instant.
 */
export type RangeChoice = "3m" | "6m" | "12m" | "all";

export const RANGE_OPTIONS: { value: RangeChoice; label: string }[] = [
  { value: "3m", label: "Last 3 months" },
  { value: "6m", label: "Last 6 months" },
  { value: "12m", label: "Last 12 months" },
  { value: "all", label: "Everything" },
];

const MONTHS_BACK: Record<Exclude<RangeChoice, "all">, number> = { "3m": 2, "6m": 5, "12m": 11 };

/**
 * Where the window starts, or undefined for the whole history — in which case no `from` is sent at all
 * and the server applies its own default, rather than the client guessing at a date it knows less about.
 */
export function rangeStart(choice: RangeChoice, now: Date): string | undefined {
  return choice === "all" ? undefined : campusMonthStart(now, MONTHS_BACK[choice]);
}

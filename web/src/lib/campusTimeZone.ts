// The one campus timezone calendar values are computed in — see
// docs/adr/15-define-http-api-and-time-contract.md: one configured constant, injected wherever a
// calendar value is derived, never hardcoded at a call site and never the viewer's browser timezone.
export const CAMPUS_TIME_ZONE = "Europe/Dublin";

/**
 * An instant as a campus wall-clock time. It lives here rather than in one feature because two
 * features now render one — the venue timeline and the door — and a feature may not import from
 * another feature (ADR 17): shared code moves down into `lib`.
 */
export function formatCampusTime(instant: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: CAMPUS_TIME_ZONE,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(new Date(instant));
}

const CAMPUS_PARTS = new Intl.DateTimeFormat("en-GB", {
  timeZone: CAMPUS_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

/**
 * The instant at which a campus calendar month began, `monthsBack` months before the one `instant`
 * falls in, as an ISO string.
 *
 * The server buckets club activity by campus calendar month, so a range that began at UTC midnight
 * would cut an hour off the first bucket for half the year — an Event ending at 00:30 Dublin on the
 * first would be charted in that month and excluded from the window that filled it. Deriving the
 * boundary in the same timezone the buckets use is what keeps the two agreeing.
 *
 * Midnight on the first of a month is never inside Dublin's daylight-saving gap, which falls at 01:00
 * on a Sunday, so one offset lookup is enough — see docs/adr/15-define-http-api-and-time-contract.md.
 */
export function campusMonthStart(instant: Date, monthsBack: number): string {
  const { year, month } = campusYearMonth(instant);
  const localMidnight = Date.UTC(year, month - 1 - monthsBack, 1);
  return new Date(localMidnight - campusOffsetMs(new Date(localMidnight))).toISOString();
}

function campusYearMonth(instant: Date): { year: number; month: number } {
  const part = campusPartReader(instant);
  return { year: part("year"), month: part("month") };
}

/** How far ahead of UTC the campus clock reads at {@code instant}. */
function campusOffsetMs(instant: Date): number {
  const part = campusPartReader(instant);
  // The campus wall clock read as though it were UTC: the gap between that and the real instant is
  // exactly the offset. 24 is what an h23 formatter returns for local midnight in some engines, and
  // Date.UTC rolls it over into the next day correctly either way.
  const asIfUtc = Date.UTC(
    part("year"),
    part("month") - 1,
    part("day"),
    part("hour"),
    part("minute"),
    part("second"),
  );
  return asIfUtc - instant.getTime();
}

/** One formatter pass, read back by part name. Every part asked for below is one the formatter emits. */
function campusPartReader(instant: Date): (type: string) => number {
  const parts = new Map<string, number>(
    CAMPUS_PARTS.formatToParts(instant).map((part) => [part.type, Number(part.value)]),
  );
  return (type) => parts.get(type) ?? 0;
}

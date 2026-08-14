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

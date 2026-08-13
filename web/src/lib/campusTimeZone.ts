// The one campus timezone calendar values are computed in — see
// docs/adr/15-define-http-api-and-time-contract.md: one configured constant, injected wherever a
// calendar value is derived, never hardcoded at a call site and never the viewer's browser timezone.
export const CAMPUS_TIME_ZONE = "Europe/Dublin";

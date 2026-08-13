// The Student-facing message for each Seat Ledger `code` — see
// docs/adr/15-define-http-api-and-time-contract.md: the frontend switches on `code`, never on `detail`
// or on the HTTP status alone, because several distinct refusals share one status.
export function describeRegistrationError(code: string): string {
  switch (code) {
    case "EVENT_FULL":
      return "This Event is full.";
    case "ALREADY_ENROLLED":
      return "You are already registered for this Event.";
    case "ALREADY_WAITLISTED":
      return "You are already on the Waitlist for this Event.";
    case "REGISTRATION_NOT_OPEN":
      return "Registration is not open yet.";
    case "REGISTRATION_CLOSED":
      return "Registration has closed.";
    case "EVENT_STARTED":
      return "This Event has already started.";
    case "EVENT_CANCELLED":
      return "This Event was cancelled.";
    case "NOT_FOUND":
      return "This Event could not be found.";
    default:
      return "Something went wrong. Please try again.";
  }
}

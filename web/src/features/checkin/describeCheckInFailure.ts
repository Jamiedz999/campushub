/**
 * The wording for every way the door can refuse, chosen by the stable `code` — see
 * docs/adr/15-define-http-api-and-time-contract.md.
 *
 * Five of the door's six states are failures, and the door is the highest-stress surface in the
 * product, with a queue of people behind the Student. Two of these words are load-bearing:
 *
 * - **An expired code is not an error.** It is the most common outcome and the proof the mechanism
 *   works, so it reads as a normal retry with the scan offered again.
 * - **The no-signal state names the manual override**, because Core has no offline queue and asking a
 *   human is genuinely the only way through.
 */
export interface CheckInFailure {
  /** Short label for the state — the pill at the top of the phone screen. */
  headline: string;
  message: string;
  /**
   * What actually gets the Student through this state. RESCAN means a fresh code off the screen —
   * re-sending the one they have would be refused again. RETRY means the same code is still good and
   * only the request failed. NONE means no button helps and a human is the way through.
   */
  action: "RESCAN" | "RETRY" | "NONE";
}

export function describeCheckInFailure(code: string): CheckInFailure {
  switch (code) {
    case "TOKEN_EXPIRED":
      return {
        headline: "Code expired",
        message: "That code has rotated. Scan the screen again — it changes every minute.",
        action: "RESCAN",
      };
    case "TOKEN_INVALID":
      return {
        headline: "Code not recognised",
        message: "That code did not come from this event's door. Scan the screen in the room again.",
        action: "RESCAN",
      };
    case "ALREADY_CHECKED_IN":
      return {
        headline: "Already checked in",
        message: "You are already checked in. There is nothing else to do.",
        action: "NONE",
      };
    case "NOT_ON_ROSTER":
      return {
        headline: "You're on the waitlist",
        message:
          "You were still waiting when this event started, so there is no seat to check into. " +
          "Speak to the organiser.",
        action: "NONE",
      };
    case "CHECK_IN_WINDOW_CLOSED":
      return {
        headline: "Check-in is closed",
        message: "Check-in opens 15 minutes before the start and closes when the event ends.",
        action: "NONE",
      };
    case "NOT_FOUND":
      return {
        headline: "Event not found",
        message: "This event could not be found.",
        action: "NONE",
      };
    case "NETWORK_ERROR":
      return {
        headline: "Couldn't reach CampusHub",
        message: "Check-in needs a connection. Try again, or ask the organiser to mark you present.",
        action: "RETRY",
      };
    default:
      return {
        headline: "Something went wrong",
        message: "Try again, or ask the organiser to mark you present.",
        action: "RETRY",
      };
  }
}

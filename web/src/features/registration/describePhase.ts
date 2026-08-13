import { CAMPUS_TIME_ZONE } from "../../lib/campusTimeZone";
import type { EventRegistrationView } from "./types";

/** The Student-facing message for each Phase, scoped to what this Issue can actually do: joining the
 * Waitlist is Issue #5's territory, so FULL says only that the Event is full. See
 * docs/adr/03-define-event-lifecycle.md. */
export function describePhase(view: EventRegistrationView): string {
  switch (view.phase) {
    case "DRAFT":
      return "Not visible to Students at all";
    case "SCHEDULED": {
      const opensDate = new Date(view.registrationOpensAt).toLocaleDateString(undefined, {
        timeZone: CAMPUS_TIME_ZONE,
      });
      return `Registration opens on ${opensDate}`;
    }
    case "REGISTRATION_OPEN":
      return `${view.capacity - view.enrolledCount} of ${view.capacity} seats left`;
    case "FULL":
      return "This Event is full";
    case "REGISTRATION_CLOSED":
      return "Registration has closed";
    case "IN_PROGRESS":
      return "Happening now";
    case "COMPLETED":
      return "This event has ended";
    case "CANCELLED":
      return "This event was cancelled";
  }
}

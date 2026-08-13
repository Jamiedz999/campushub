import { CAMPUS_TIME_ZONE } from "../../lib/campusTimeZone";
import type { EventBrowseItem } from "./types";

/** The Student-facing message for each Phase — the "What the Student sees" column of the Phase table in
 * docs/adr/03-define-event-lifecycle.md. */
export function describePhase(item: EventBrowseItem): string {
  switch (item.phase) {
    case "DRAFT":
      return "Not visible to Students at all";
    case "SCHEDULED": {
      const opensDate = new Date(item.registrationOpensAt).toLocaleDateString(undefined, {
        timeZone: CAMPUS_TIME_ZONE,
      });
      return `Registration opens on ${opensDate}`;
    }
    case "REGISTRATION_OPEN":
      return `${item.capacity - item.enrolledCount} of ${item.capacity} seats left`;
    case "FULL":
      return `Event full — join the waitlist (${item.waitlistCount} waiting)`;
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

import type { RosterEntry } from "./types";

/**
 * How the door is doing: how many of the Seats in the room are accounted for, and by which route.
 *
 * Derived from the Roster rather than read from the server, for the same reason Phase is derived
 * rather than stored — two numbers that can disagree with the list beside them are two numbers that
 * eventually will. It also keeps this Issue clear of the live attendee count, which is
 * [its own Issue](https://github.com/Jamiedz999/campushub/issues/9).
 */
export interface RosterProgress {
  enrolled: number;
  attended: number;
  scanned: number;
  manual: number;
}

export function rosterProgress(items: RosterEntry[]): RosterProgress {
  const scanned = items.filter((entry) => entry.method === "SCANNED").length;
  const manual = items.filter((entry) => entry.method === "MANUAL").length;
  return { enrolled: items.length, attended: scanned + manual, scanned, manual };
}

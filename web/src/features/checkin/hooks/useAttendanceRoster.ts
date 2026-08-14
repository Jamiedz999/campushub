import { useQuery } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { getAttendanceRoster } from "../api/checkin";
import type { AttendanceRoster } from "../types";

/**
 * The door's Roster, and the numbers the screen derives from it.
 *
 * Deliberately not polled. Pushing attendance out as it happens is
 * [its own Issue](https://github.com/Jamiedz999/campushub/issues/9), and a timer here would be that
 * deferred decision made by accident — one the WebSocket work would then have to unpick. This re-reads
 * when the Officer's own override changes something, which is the only change this screen causes.
 */
export function useAttendanceRoster(eventId: string) {
  return useQuery<AttendanceRoster, ApiError>({
    queryKey: ["checkin", "attendance", eventId],
    queryFn: () => getAttendanceRoster(eventId),
  });
}

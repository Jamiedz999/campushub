import { useQuery, useQueryClient, type UseQueryResult } from "@tanstack/react-query";
import { useCallback } from "react";
import type { ApiError } from "../../../lib/apiError";
import { getAttendanceRoster } from "../api/checkin";
import type { AttendanceRoster } from "../types";
import { useDoorScopeSocket } from "./useDoorScopeSocket";

/**
 * How often the screen re-reads while the socket is down. Slow enough to be free at the scale of one
 * door, quick enough that an Officer watching people walk in never wonders whether it has stopped.
 */
export const DEGRADED_REFRESH_MS = 10_000;

function attendanceRosterKey(eventId: string) {
  return ["checkin", "attendance", eventId];
}

/**
 * The door's Roster, kept current by the socket and by re-reading — never by anything the socket said.
 *
 * Every number on the screen is derived from this one authorized snapshot, so the three ways it can
 * refresh — a hint, a reconnect, or the fallback timer — all end in the same place. Missing a hint
 * costs latency and nothing else, which is the whole reason the channel is allowed to be lossy.
 */
export function useAttendanceRoster(eventId: string): {
  roster: UseQueryResult<AttendanceRoster, ApiError>;
  live: boolean;
} {
  const queryClient = useQueryClient();

  const reRead = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: attendanceRosterKey(eventId) });
  }, [queryClient, eventId]);

  const live = useDoorScopeSocket(eventId, reRead);

  const roster = useQuery<AttendanceRoster, ApiError>({
    queryKey: attendanceRosterKey(eventId),
    queryFn: () => getAttendanceRoster(eventId),
    // The timer exists only for the case where the socket does not: a proxy that strips upgrades, a
    // browser that refuses one, a server without the route. The door screen degrades to being a few
    // seconds behind rather than to being wrong, and stops paying for the timer once the socket is up.
    refetchInterval: live ? false : DEGRADED_REFRESH_MS,
    // A door screen is projected and then left alone, so the tab it lives in is regularly not the
    // focused one. TanStack pauses interval refetches in the background by default, which would stop
    // exactly the fallback that exists for the case where nothing else is arriving.
    refetchIntervalInBackground: true,
  });

  return { roster, live };
}

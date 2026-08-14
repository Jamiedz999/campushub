import { useQuery } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { getAttendanceRoster } from "../api/checkin";
import type { AttendanceRoster } from "../types";
import { DOOR_CODE_REFRESH_MS } from "./useDoorCode";

/** The door's roster, refreshed on the same cadence as the code so both halves of the screen agree. */
export function useAttendanceRoster(eventId: string) {
  return useQuery<AttendanceRoster, ApiError>({
    queryKey: ["checkin", "attendance", eventId],
    queryFn: () => getAttendanceRoster(eventId),
    refetchInterval: DOOR_CODE_REFRESH_MS,
  });
}

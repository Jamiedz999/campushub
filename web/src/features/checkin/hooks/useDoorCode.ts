import { useQuery } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { getDoorCode } from "../api/checkin";
import type { DoorCode } from "../types";

/**
 * The displayed code, re-read well inside its own lifetime. A code stays valid for its window and the
 * one after it — 60 to 120 seconds — so refreshing every 15 seconds means the screen is never showing
 * a code the server would refuse, and the attendance count on the same response stays close to live
 * until the WebSocket push arrives.
 */
export const DOOR_CODE_REFRESH_MS = 15_000;

export function useDoorCode(eventId: string) {
  return useQuery<DoorCode, ApiError>({
    queryKey: ["checkin", "door-code", eventId],
    queryFn: () => getDoorCode(eventId),
    refetchInterval: DOOR_CODE_REFRESH_MS,
  });
}

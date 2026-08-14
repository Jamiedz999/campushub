import { useMutation } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { checkIn } from "../api/checkin";
import type { CheckInResult } from "../types";

/**
 * One scan. Deliberately not retried: a code the server refused will still be refused a moment later,
 * and the Student's way through is a fresh code off the screen, not a silent retry of a stale one.
 */
export function useCheckIn(eventId: string) {
  return useMutation<CheckInResult, ApiError, string>({
    mutationFn: (token) => checkIn(eventId, token),
    retry: false,
  });
}

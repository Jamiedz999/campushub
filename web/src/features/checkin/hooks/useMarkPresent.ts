import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { markPresent } from "../api/checkin";

/** The Officer's override. On success the roster and the count are re-read, never patched locally. */
export function useMarkPresent(eventId: string) {
  const queryClient = useQueryClient();
  return useMutation<void, ApiError, string>({
    mutationFn: (studentId) => markPresent(eventId, studentId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["checkin", "attendance", eventId] }),
        queryClient.invalidateQueries({ queryKey: ["checkin", "door-code", eventId] }),
      ]);
    },
  });
}

import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { withdrawFromEvent } from "../api/withdrawFromEvent";
import type { EventRegistrationView } from "../types";

/** Gives up a Seat or leaves the Waitlist, then refreshes every view whose counts changed. */
export function useWithdrawFromEvent(eventId: string) {
  const queryClient = useQueryClient();
  return useMutation<EventRegistrationView, ApiError>({
    mutationFn: () => withdrawFromEvent(eventId),
    onSuccess: (view) => {
      queryClient.setQueryData(["events", "registration", eventId], view);
      queryClient.invalidateQueries({ queryKey: ["events", "mine"] });
      queryClient.invalidateQueries({ queryKey: ["events", "browse"] });
    },
  });
}

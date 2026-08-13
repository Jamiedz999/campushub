import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { registerForEvent } from "../api/registerForEvent";
import type { EventRegistrationView } from "../types";

/** Taking a Seat. On success, the fresh view (seats left, enrolled) replaces the cached one in place. */
export function useRegisterForEvent(eventId: string) {
  const queryClient = useQueryClient();
  return useMutation<EventRegistrationView, ApiError>({
    mutationFn: () => registerForEvent(eventId),
    onSuccess: (view) => {
      queryClient.setQueryData(["events", "registration", eventId], view);
      queryClient.invalidateQueries({ queryKey: ["events", "mine"] });
      queryClient.invalidateQueries({ queryKey: ["events", "browse"] });
    },
  });
}

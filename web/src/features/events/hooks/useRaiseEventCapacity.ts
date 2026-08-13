import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { raiseEventCapacity } from "../api/raiseEventCapacity";
import type { EventOfficerView } from "../types";

export function useRaiseEventCapacity(eventId: string) {
  const queryClient = useQueryClient();
  return useMutation<EventOfficerView, ApiError, number>({
    mutationFn: (capacity) => raiseEventCapacity(eventId, capacity),
    onSuccess: (view) => {
      queryClient.setQueryData(["events", "officer", eventId], view);
      queryClient.invalidateQueries({ queryKey: ["events", "browse"] });
      queryClient.invalidateQueries({ queryKey: ["events", "registration", eventId] });
      queryClient.invalidateQueries({ queryKey: ["events", "mine"] });
    },
  });
}

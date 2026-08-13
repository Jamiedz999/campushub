import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import type { RegistrationAnswers } from "../../../types/registrationForm";
import { retryRegistrationAnswers } from "../api/retryRegistrationAnswers";
import type { EventRegistrationView } from "../types";

export function useRetryRegistrationAnswers(eventId: string) {
  const queryClient = useQueryClient();
  return useMutation<EventRegistrationView, ApiError, RegistrationAnswers>({
    mutationFn: (answers) => retryRegistrationAnswers(eventId, answers),
    onSuccess: (view) => {
      queryClient.setQueryData(["events", "registration", eventId], view);
      queryClient.invalidateQueries({ queryKey: ["events", "mine"] });
    },
  });
}

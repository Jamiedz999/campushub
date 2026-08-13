import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import type { RegistrationForm } from "../../../types/registrationForm";
import { updateRegistrationForm } from "../api/updateRegistrationForm";
import type { EventOfficerView } from "../types";

export function useUpdateRegistrationForm(eventId: string) {
  const queryClient = useQueryClient();
  return useMutation<EventOfficerView, ApiError, RegistrationForm>({
    mutationFn: (form) => updateRegistrationForm(eventId, form),
    onSuccess: (view) => queryClient.setQueryData(["events", "officer", eventId], view),
    onError: (error) => {
      if (error.code === "FORM_LOCKED") {
        void queryClient.invalidateQueries({ queryKey: ["events", "officer", eventId] });
      }
    },
  });
}

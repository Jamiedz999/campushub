import { useQuery } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { getEventRegistration } from "../api/getEventRegistration";
import type { EventRegistrationView } from "../types";

export function useEventRegistration(eventId: string) {
  return useQuery<EventRegistrationView, ApiError>({
    queryKey: ["events", "registration", eventId],
    queryFn: () => getEventRegistration(eventId),
  });
}

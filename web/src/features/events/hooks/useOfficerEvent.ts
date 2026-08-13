import { useQuery } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { getOfficerEvent } from "../api/getOfficerEvent";
import type { EventOfficerView } from "../types";

export function useOfficerEvent(eventId: string) {
  return useQuery<EventOfficerView, ApiError>({
    queryKey: ["events", "officer", eventId],
    queryFn: () => getOfficerEvent(eventId),
  });
}

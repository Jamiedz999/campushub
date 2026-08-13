import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import type { PageResponse } from "../../../types/pageResponse";
import { listMyEvents } from "../api/listMyEvents";
import type { MyEventsFilters } from "../api/listMyEvents";
import type { EventRegistrationView } from "../types";

/** Keeps the previous page's results on screen while a new page loads. */
export function useMyEvents(filters: MyEventsFilters) {
  return useQuery<PageResponse<EventRegistrationView>, ApiError>({
    queryKey: ["events", "mine", filters],
    queryFn: () => listMyEvents(filters),
    placeholderData: keepPreviousData,
  });
}

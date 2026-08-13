import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import type { PageResponse } from "../../../types/pageResponse";
import { browseEvents } from "../api/browseEvents";
import type { EventBrowseFilters, EventBrowseItem } from "../types";

/** Keeps the previous page's results on screen while a new page or filter loads. */
export function useEventsBrowse(filters: EventBrowseFilters) {
  return useQuery<PageResponse<EventBrowseItem>, ApiError>({
    queryKey: ["events", "browse", filters],
    queryFn: () => browseEvents(filters),
    placeholderData: keepPreviousData,
  });
}

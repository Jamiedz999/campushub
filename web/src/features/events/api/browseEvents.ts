import { httpClient } from "../../../lib/httpClient";
import type { PageResponse } from "../../../types/pageResponse";
import type { EventBrowseFilters, EventBrowseItem } from "../types";

/** GET /api/events — Published Events only. See docs/adr/16-define-event-discovery.md. */
export async function browseEvents(filters: EventBrowseFilters): Promise<PageResponse<EventBrowseItem>> {
  const params: Record<string, string | number | boolean> = {
    page: filters.page,
    size: filters.size,
  };
  if (filters.q) {
    params.q = filters.q;
  }
  if (filters.openForRegistration !== undefined) {
    params.openForRegistration = filters.openForRegistration;
  }
  if (filters.hasFreeSeat !== undefined) {
    params.hasFreeSeat = filters.hasFreeSeat;
  }
  if (filters.startsAtFrom) {
    params.startsAtFrom = filters.startsAtFrom;
  }
  if (filters.startsAtTo) {
    params.startsAtTo = filters.startsAtTo;
  }
  if (filters.sort) {
    params.sort = filters.sort;
  }

  const response = await httpClient.get<PageResponse<EventBrowseItem>>("/events", { params });
  return response.data;
}

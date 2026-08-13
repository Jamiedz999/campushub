import { httpClient } from "../../../lib/httpClient";
import type { PageResponse } from "../../../types/pageResponse";
import type { EventRegistrationView } from "../types";

export interface MyEventsFilters {
  page: number;
  size: number;
}

/** GET /api/events/mine — every Event, whatever its Status, where the Student holds a Seat. */
export async function listMyEvents(filters: MyEventsFilters): Promise<PageResponse<EventRegistrationView>> {
  const response = await httpClient.get<PageResponse<EventRegistrationView>>("/events/mine", {
    params: { page: filters.page, size: filters.size },
  });
  return response.data;
}

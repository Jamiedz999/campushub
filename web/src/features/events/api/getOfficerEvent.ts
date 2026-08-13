import { httpClient } from "../../../lib/httpClient";
import type { EventOfficerView } from "../types";

/** GET the Club Officer's scoped Event view. */
export async function getOfficerEvent(eventId: string): Promise<EventOfficerView> {
  const response = await httpClient.get<EventOfficerView>(`/events/${eventId}`);
  return response.data;
}

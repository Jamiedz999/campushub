import { httpClient } from "../../../lib/httpClient";
import type { EventOfficerView } from "../types";

/** PATCH the capacity; the server promotes everyone who now fits in the same atomic write. */
export async function raiseEventCapacity(eventId: string, capacity: number): Promise<EventOfficerView> {
  const response = await httpClient.patch<EventOfficerView>(`/events/${eventId}`, { capacity });
  return response.data;
}

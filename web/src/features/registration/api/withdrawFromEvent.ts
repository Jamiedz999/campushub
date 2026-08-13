import { httpClient } from "../../../lib/httpClient";
import type { EventRegistrationView } from "../types";

/** DELETE /api/events/{eventId}/registration — give up a Seat or leave the Waitlist. */
export async function withdrawFromEvent(eventId: string): Promise<EventRegistrationView> {
  const response = await httpClient.delete<EventRegistrationView>(`/events/${eventId}/registration`);
  return response.data;
}

import { httpClient } from "../../../lib/httpClient";
import type { EventRegistrationView } from "../types";

/** GET /api/events/{eventId}/registration — the Student's own view of one Event. */
export async function getEventRegistration(eventId: string): Promise<EventRegistrationView> {
  const response = await httpClient.get<EventRegistrationView>(`/events/${eventId}/registration`);
  return response.data;
}

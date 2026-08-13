import { httpClient } from "../../../lib/httpClient";
import type { EventRegistrationView } from "../types";
import type { RegistrationAnswers } from "../../../types/registrationForm";

/** POST /api/events/{eventId}/registration — taking a Seat. See docs/adr/04-define-registration-capacity-and-waitlist.md. */
export async function registerForEvent(
  eventId: string,
  answers: RegistrationAnswers,
): Promise<EventRegistrationView> {
  const response = await httpClient.post<EventRegistrationView>(`/events/${eventId}/registration`, { answers });
  return response.data;
}

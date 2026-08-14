import { httpClient } from "../../../lib/httpClient";
import type { RegistrationAnswers } from "../../../types/registrationForm";
import type { EventRegistrationView } from "../types";

/** Retries the separate answer write; the Student must already hold the Seat. */
export async function retryRegistrationAnswers(
  eventId: string,
  answers: RegistrationAnswers,
): Promise<EventRegistrationView> {
  const response = await httpClient.put<EventRegistrationView>(`/events/${eventId}/registration/answers`, {
    answers,
  });
  return response.data;
}

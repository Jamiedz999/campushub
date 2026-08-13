import { httpClient } from "../../../lib/httpClient";
import type { RegistrationForm } from "../../../types/registrationForm";
import type { EventOfficerView } from "../types";

export async function updateRegistrationForm(
  eventId: string,
  form: RegistrationForm,
): Promise<EventOfficerView> {
  const response = await httpClient.put<EventOfficerView>(`/events/${eventId}/registration-form`, {
    fields: form.fields,
  });
  return response.data;
}

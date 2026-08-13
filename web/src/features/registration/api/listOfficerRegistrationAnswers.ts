import { httpClient } from "../../../lib/httpClient";
import type { OfficerAnswersView } from "../types";

export interface OfficerAnswersFilters {
  page: number;
  size: number;
}

export async function listOfficerRegistrationAnswers(
  eventId: string,
  filters: OfficerAnswersFilters,
): Promise<OfficerAnswersView> {
  const response = await httpClient.get<OfficerAnswersView>(`/events/${eventId}/registration-answers`, {
    params: { page: filters.page, size: filters.size },
  });
  return response.data;
}

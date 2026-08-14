import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { listOfficerRegistrationAnswers } from "../api/listOfficerRegistrationAnswers";
import type { OfficerAnswersFilters } from "../api/listOfficerRegistrationAnswers";
import type { OfficerAnswersView } from "../types";

export function useOfficerRegistrationAnswers(eventId: string, filters: OfficerAnswersFilters) {
  return useQuery<OfficerAnswersView, ApiError>({
    queryKey: ["events", "registration-answers", eventId, filters],
    queryFn: () => listOfficerRegistrationAnswers(eventId, filters),
    placeholderData: keepPreviousData,
  });
}

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { ApiError } from "../../../lib/apiError";
import { getDashboard, type DashboardQuery } from "../api/dashboard";
import type { Dashboard } from "../types";

/**
 * The dashboard for the caller's own scope. Keeps the previous window's numbers on screen while a new
 * one loads, so changing the time range redraws rather than emptying the page.
 */
export function useDashboard(query: DashboardQuery) {
  return useQuery<Dashboard, ApiError>({
    queryKey: ["dashboard", query],
    queryFn: () => getDashboard(query),
    placeholderData: keepPreviousData,
  });
}

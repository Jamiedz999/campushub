import { httpClient } from "../../../lib/httpClient";
import type { Dashboard } from "../types";

/** What the caller may narrow the read to. The scope itself is the server's decision, not this one's. */
export interface DashboardQuery {
  clubId?: string;
  from?: string;
  to?: string;
}

/**
 * The whole dashboard in one read. 404 unless the caller officers a Club or is a University Admin —
 * and 404, not 403, when a Club Officer names a Club they hold no grant in, because the query would
 * have been scoped to it and found nothing. See docs/adr/08-define-roles-and-resource-authorization.md.
 */
export async function getDashboard(query: DashboardQuery = {}): Promise<Dashboard> {
  const response = await httpClient.get<Dashboard>("/dashboard", { params: query });
  return response.data;
}

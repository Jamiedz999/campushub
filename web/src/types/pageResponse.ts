// The one collection envelope every paged endpoint in the API uses — see
// docs/adr/15-define-http-api-and-time-contract.md.
export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

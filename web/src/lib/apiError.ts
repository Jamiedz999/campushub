import axios from "axios";

export const NETWORK_ERROR_CODE = "NETWORK_ERROR";

interface ApiErrorShape {
  code: string;
  status: number;
  title: string;
  detail: string;
  fieldErrors?: Record<string, string>;
}

/**
 * The typed error every httpClient call rejects with. `code` is the contract —
 * see docs/adr/15-define-http-api-and-time-contract.md. Callers switch on
 * `code`, never on `status` or `detail`.
 */
export class ApiError extends Error implements ApiErrorShape {
  readonly code: string;
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly fieldErrors: Record<string, string>;

  constructor(shape: ApiErrorShape) {
    super(shape.detail);
    this.name = "ApiError";
    this.code = shape.code;
    this.status = shape.status;
    this.title = shape.title;
    this.detail = shape.detail;
    this.fieldErrors = shape.fieldErrors ?? {};
  }
}

interface ProblemDetailBody {
  code: string;
  title?: unknown;
  detail?: unknown;
  status?: unknown;
  fieldErrors?: unknown;
}

function stringMap(value: unknown): Record<string, string> {
  const result: Record<string, string> = {};
  if (typeof value !== "object" || value === null) {
    return result;
  }
  for (const [key, entry] of Object.entries(value)) {
    if (typeof entry === "string") {
      result[key] = entry;
    }
  }
  return result;
}

function isProblemDetailBody(data: unknown): data is ProblemDetailBody {
  if (typeof data !== "object" || data === null) {
    return false;
  }
  if (!("code" in data)) {
    return false;
  }
  return typeof data.code === "string";
}

/** Turns any rejection from httpClient into an ApiError carrying a stable `code`. */
export function normalizeApiError(error: unknown): ApiError {
  if (axios.isAxiosError<unknown>(error)) {
    const data = error.response?.data;
    if (isProblemDetailBody(data)) {
      return new ApiError({
        code: data.code,
        status: typeof data.status === "number" ? data.status : (error.response?.status ?? 0),
        title: typeof data.title === "string" ? data.title : "Request Failed",
        detail: typeof data.detail === "string" ? data.detail : error.message,
        fieldErrors: stringMap(data.fieldErrors),
      });
    }
  }

  return new ApiError({
    code: NETWORK_ERROR_CODE,
    status: 0,
    title: "Network Error",
    detail: error instanceof Error ? error.message : "An unknown error occurred.",
  });
}

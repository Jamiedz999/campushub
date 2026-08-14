import axios from "axios";

export const NETWORK_ERROR_CODE = "NETWORK_ERROR";

interface ApiErrorShape {
  code: string;
  status: number;
  title: string;
  detail: string;
  fieldErrors?: Record<string, string>;
  extensions?: Record<string, unknown>;
}

const PROBLEM_MEMBERS = new Set(["type", "title", "status", "detail", "instance", "code", "fieldErrors"]);

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
  /**
   * Any other member the problem document carried — a refusal that has a fact
   * the client needs, such as when an already-checked-in Student first checked
   * in. `code` stays the contract; these are read only after switching on it.
   */
  readonly extensions: Record<string, unknown>;

  constructor(shape: ApiErrorShape) {
    super(shape.detail);
    this.name = "ApiError";
    this.code = shape.code;
    this.status = shape.status;
    this.title = shape.title;
    this.detail = shape.detail;
    this.fieldErrors = shape.fieldErrors ?? {};
    this.extensions = shape.extensions ?? {};
  }

  /** One extension member, when it is a string. Nothing else is trusted from the wire. */
  stringExtension(name: string): string | null {
    const value = this.extensions[name];
    return typeof value === "string" ? value : null;
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

function extraMembers(data: ProblemDetailBody): Record<string, unknown> {
  const extensions: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(data)) {
    if (!PROBLEM_MEMBERS.has(key)) {
      extensions[key] = value;
    }
  }
  return extensions;
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
        extensions: extraMembers(data),
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

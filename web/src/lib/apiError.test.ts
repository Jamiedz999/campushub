import { AxiosError, AxiosHeaders } from "axios";
import { describe, expect, it } from "vitest";
import { ApiError, NETWORK_ERROR_CODE, normalizeApiError } from "./apiError";

describe("normalizeApiError", () => {
  it("turns an RFC 9457 problem+json body into a typed ApiError carrying code", () => {
    const requestConfig = { headers: new AxiosHeaders() };
    const axiosError = new AxiosError("Request failed with status code 409", "ERR_BAD_REQUEST", requestConfig, undefined, {
      status: 409,
      statusText: "Conflict",
      headers: new AxiosHeaders(),
      config: requestConfig,
      data: {
        type: "about:blank",
        title: "Conflict",
        status: 409,
        detail: "The event is full.",
        instance: "/api/events/abc123/registration",
        code: "EVENT_FULL",
        fieldErrors: { teamName: "Required." },
      },
    });

    const result = normalizeApiError(axiosError);

    expect(result).toBeInstanceOf(ApiError);
    expect(result.code).toBe("EVENT_FULL");
    expect(result.status).toBe(409);
    expect(result.title).toBe("Conflict");
    expect(result.detail).toBe("The event is full.");
    expect(result.fieldErrors).toEqual({ teamName: "Required." });
  });

  it("falls back to the network error code when the axios error carries no problem+json body", () => {
    const axiosError = new AxiosError("Network Error", "ERR_NETWORK", { headers: new AxiosHeaders() });

    const result = normalizeApiError(axiosError);

    expect(result.code).toBe(NETWORK_ERROR_CODE);
    expect(result.detail).toBe("Network Error");
  });

  it("falls back to the network error code for a plain, non-axios error", () => {
    const result = normalizeApiError(new Error("boom"));

    expect(result.code).toBe(NETWORK_ERROR_CODE);
    expect(result.detail).toBe("boom");
  });

  it("falls back to the network error code for a thrown non-error value", () => {
    const result = normalizeApiError("not an error at all");

    expect(result.code).toBe(NETWORK_ERROR_CODE);
    expect(result.detail).toBe("An unknown error occurred.");
  });

  it("falls back to the response status and generic wording when the problem body omits them", () => {
    const requestConfig = { headers: new AxiosHeaders() };
    const axiosError = new AxiosError("Request failed with status code 502", "ERR_BAD_RESPONSE", requestConfig, undefined, {
      status: 502,
      statusText: "Bad Gateway",
      headers: new AxiosHeaders(),
      config: requestConfig,
      data: { code: "UPSTREAM_UNAVAILABLE" },
    });

    const result = normalizeApiError(axiosError);

    expect(result.code).toBe("UPSTREAM_UNAVAILABLE");
    expect(result.status).toBe(502);
    expect(result.title).toBe("Request Failed");
    expect(result.detail).toBe("Request failed with status code 502");
  });

  it("keeps every other problem member, so a refusal can carry the fact the client needs", () => {
    const requestConfig = { headers: new AxiosHeaders() };
    const axiosError = new AxiosError("Request failed with status code 409", "ERR_BAD_REQUEST", requestConfig, undefined, {
      status: 409,
      statusText: "Conflict",
      headers: new AxiosHeaders(),
      config: requestConfig,
      data: {
        type: "about:blank",
        title: "Conflict",
        status: 409,
        detail: "You are already checked in.",
        instance: "/api/events/abc123/attendance",
        code: "ALREADY_CHECKED_IN",
        at: "2026-03-20T18:04:00Z",
        method: "SCANNED",
      },
    });

    const result = normalizeApiError(axiosError);

    expect(result.extensions).toEqual({ at: "2026-03-20T18:04:00Z", method: "SCANNED" });
    expect(result.stringExtension("at")).toBe("2026-03-20T18:04:00Z");
  });

  it("trusts an extension member only when it is a string", () => {
    const error = new ApiError({
      code: "ALREADY_CHECKED_IN",
      status: 409,
      title: "Conflict",
      detail: "",
      extensions: { at: 1774000020 },
    });

    expect(error.stringExtension("at")).toBeNull();
    expect(error.stringExtension("absent")).toBeNull();
  });

  it("carries no extensions when the problem document had none", () => {
    expect(new ApiError({ code: "NOT_FOUND", status: 404, title: "", detail: "" }).extensions).toEqual({});
  });

  it("falls back to the network error code when the response body is an object without a code", () => {
    const requestConfig = { headers: new AxiosHeaders() };
    const axiosError = new AxiosError("Request failed with status code 500", "ERR_BAD_RESPONSE", requestConfig, undefined, {
      status: 500,
      statusText: "Internal Server Error",
      headers: new AxiosHeaders(),
      config: requestConfig,
      data: { message: "something unrelated to the API contract" },
    });

    const result = normalizeApiError(axiosError);

    expect(result.code).toBe(NETWORK_ERROR_CODE);
  });
});

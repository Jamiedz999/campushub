import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "./apiError";
import { httpClient } from "./httpClient";

function clearCookies() {
  for (const entry of document.cookie.split(";")) {
    const name = entry.split("=")[0]?.trim();
    if (name !== undefined && name !== "") {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    }
  }
}

describe("httpClient", () => {
  let capturedConfig: InternalAxiosRequestConfig | undefined;

  beforeEach(() => {
    clearCookies();
    document.cookie = "XSRF-TOKEN=test-token-value";
    capturedConfig = undefined;
    httpClient.defaults.adapter = async (config) => {
      capturedConfig = config;
      return {
        data: {},
        status: 200,
        statusText: "OK",
        headers: new AxiosHeaders(),
        config,
      };
    };
  });

  afterEach(() => {
    clearCookies();
  });

  it("uses the same-origin /api base", () => {
    expect(httpClient.defaults.baseURL).toBe("/api");
  });

  it("does not attach the CSRF header to a safe GET request", async () => {
    await httpClient.get("/system");

    expect(capturedConfig?.headers.get("X-XSRF-TOKEN")).toBeFalsy();
  });

  it.each(["post", "put", "patch", "delete"])("attaches the CSRF header from the cookie to a %s request", async (method) => {
    await httpClient.request({ url: "/something", method });

    expect(capturedConfig?.headers.get("X-XSRF-TOKEN")).toBe("test-token-value");
  });

  it("does not attach the CSRF header when no cookie is set", async () => {
    clearCookies();

    await httpClient.post("/something", {});

    expect(capturedConfig?.headers.get("X-XSRF-TOKEN")).toBeFalsy();
  });

  it("normalises a failed request into an ApiError, end to end", async () => {
    const requestConfig = { headers: new AxiosHeaders() };
    httpClient.defaults.adapter = async () => {
      throw new AxiosError("Request failed with status code 500", "ERR_BAD_RESPONSE", requestConfig, undefined, {
        status: 500,
        statusText: "Internal Server Error",
        headers: new AxiosHeaders(),
        config: requestConfig,
        data: { type: "about:blank", title: "Internal Server Error", status: 500, detail: "boom", code: "INTERNAL_ERROR" },
      });
    };

    await expect(httpClient.get("/system")).rejects.toBeInstanceOf(ApiError);
  });
});

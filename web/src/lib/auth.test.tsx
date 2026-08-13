import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./apiError";
import { useCurrentActor, useLogin, useLogout, type CurrentActor } from "./auth";
import { httpClient } from "./httpClient";

const ACTOR: CurrentActor = {
  accountId: "account-1",
  email: "officer@demo.campushub",
  displayName: "Demo Officer",
  systemRole: "STUDENT",
  officerClubIds: ["club-a"],
};

function wrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("useCurrentActor", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns the signed-in actor", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue({ data: ACTOR });

    const { result } = renderHook(() => useCurrentActor(), { wrapper: wrapper() });

    await waitFor(() => expect(result.current.status).toBe("success"));
    expect(result.current.data).toEqual(ACTOR);
  });

  it("surfaces an unauthenticated ApiError without retrying", async () => {
    const getSpy = vi
        .spyOn(httpClient, "get")
        .mockRejectedValue(
            new ApiError({ code: "UNAUTHENTICATED", status: 401, title: "Unauthenticated", detail: "no session" }),
        );

    const { result } = renderHook(() => useCurrentActor(), { wrapper: wrapper() });

    await waitFor(() => expect(result.current.status).toBe("error"));
    expect(result.current.error?.code).toBe("UNAUTHENTICATED");
    expect(getSpy).toHaveBeenCalledTimes(1);
  });
});

describe("useLogin", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("posts form-encoded credentials and invalidates the current actor query", async () => {
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValue({ data: undefined });
    vi.spyOn(httpClient, "get").mockResolvedValue({ data: ACTOR });

    const { result } = renderHook(() => useLogin(), { wrapper: wrapper() });
    result.current.mutate({ email: "officer@demo.campushub", password: "123456" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(postSpy).toHaveBeenCalledTimes(1);
    const [path, body] = postSpy.mock.calls[0] ?? [];
    expect(path).toBe("/auth/login");
    expect(body).toBeInstanceOf(URLSearchParams);
    expect((body as URLSearchParams).get("email")).toBe("officer@demo.campushub");
    expect((body as URLSearchParams).get("password")).toBe("123456");
  });
});

describe("useLogout", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("posts to logout and invalidates the current actor query", async () => {
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValue({ data: undefined });
    vi.spyOn(httpClient, "get").mockRejectedValue(
        new ApiError({ code: "UNAUTHENTICATED", status: 401, title: "Unauthenticated", detail: "no session" }),
    );

    const { result } = renderHook(() => useLogout(), { wrapper: wrapper() });
    result.current.mutate();

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(postSpy).toHaveBeenCalledWith("/auth/logout");
  });
});

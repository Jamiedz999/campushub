import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../lib/apiError";
import { httpClient } from "../lib/httpClient";
import { SystemStatusPage } from "./SystemStatusPage";

function renderWithFreshClient() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <SystemStatusPage />
    </QueryClientProvider>,
  );
}

describe("SystemStatusPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders a loading state while the request is in flight", () => {
    vi.spyOn(httpClient, "get").mockReturnValue(new Promise(() => {}));

    renderWithFreshClient();

    expect(screen.getByRole("status")).toHaveTextContent(/loading/i);
  });

  it("renders the system status once the request resolves", async () => {
    const requestConfig = { headers: new AxiosHeaders() };
    vi.spyOn(httpClient, "get").mockResolvedValue({
      data: {
        status: "UP",
        version: "0.0.1-SNAPSHOT",
        buildTime: "2026-08-13T10:00:00Z",
        serverTime: "2026-08-13T10:05:00Z",
      },
      status: 200,
      statusText: "OK",
      headers: new AxiosHeaders(),
      config: requestConfig,
    });

    renderWithFreshClient();

    await waitFor(() => {
      expect(screen.getByText("UP")).toBeInTheDocument();
    });
    expect(screen.getByText("0.0.1-SNAPSHOT")).toBeInTheDocument();
    expect(screen.getByText("2026-08-13T10:05:00Z")).toBeInTheDocument();
  });

  it("renders the error state from the error's code, not its status or message text", async () => {
    vi.spyOn(httpClient, "get").mockRejectedValue(
      new ApiError({ code: "INTERNAL_ERROR", status: 500, title: "Internal Server Error", detail: "boom" }),
    );

    renderWithFreshClient();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("INTERNAL_ERROR");
    });
  });
});

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosHeaders } from "axios";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../lib/apiError";
import { httpClient } from "../../../lib/httpClient";
import type { EventOfficerView } from "../types";
import { OfficerCapacityPage } from "./OfficerCapacityPage";

function axiosResponse<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

function view(overrides: Partial<EventOfficerView>): EventOfficerView {
  return {
    id: "event-1",
    clubId: "club-1",
    title: "Robotics Night",
    description: "Build a small robot",
    status: "PUBLISHED",
    venueId: null,
    phase: "FULL",
    registrationOpensAt: "2026-03-01T00:00:00Z",
    registrationClosesAt: "2026-03-10T00:00:00Z",
    startsAt: "2026-03-20T00:00:00Z",
    endsAt: "2026-03-20T02:00:00Z",
    capacity: 40,
    enrolledCount: 40,
    waitlistCount: 4,
    promotedCount: 0,
    everQueuedCount: 4,
    waitlistConversion: 0,
    registrationForm: { fields: [] },
    registrationFormLocked: false,
    ...overrides,
  };
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/officer/events/event-1/capacity"]}>
        <Routes>
          <Route path="/officer/events/:eventId/capacity" element={<OfficerCapacityPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("OfficerCapacityPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("warns how many waiting Students a capacity raise will admit before submission", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({})));

    renderPage();
    const input = await screen.findByLabelText("New capacity");
    await user.clear(input);
    await user.type(input, "50");

    expect(screen.getByText("This will admit 4 waiting Students immediately.")).toBeInTheDocument();
  });

  it("bases the warning on the new free places, not only the capacity difference", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({ enrolledCount: 38 })));

    renderPage();
    const input = await screen.findByLabelText("New capacity");
    await user.clear(input);
    await user.type(input, "42");

    expect(screen.getByText("This will admit 4 waiting Students immediately.")).toBeInTheDocument();
  });

  it("raises capacity through the existing Event PATCH and renders the fresh capacity", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({})));
    const patchSpy = vi.spyOn(httpClient, "patch").mockResolvedValue(
      axiosResponse(view({ capacity: 42, enrolledCount: 42, waitlistCount: 2, promotedCount: 2 })),
    );

    renderPage();
    const input = await screen.findByLabelText("New capacity");
    await user.clear(input);
    await user.type(input, "42");
    await user.click(screen.getByRole("button", { name: "Raise capacity" }));

    await waitFor(() => expect(patchSpy).toHaveBeenCalledWith("/events/event-1", { capacity: 42 }));
    expect(await screen.findByText(/Current capacity: 42/)).toBeInTheDocument();
  });

  it("shows the scoped API error when the Officer cannot load the Event", async () => {
    vi.spyOn(httpClient, "get").mockRejectedValue(
      new ApiError({ code: "NOT_FOUND", status: 404, title: "Not Found", detail: "missing" }),
    );

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("NOT_FOUND");
  });
});

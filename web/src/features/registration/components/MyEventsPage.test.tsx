import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosHeaders } from "axios";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../lib/apiError";
import { httpClient } from "../../../lib/httpClient";
import type { EventRegistrationView } from "../types";
import { MyEventsPage } from "./MyEventsPage";

function axiosResponse<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

function view(overrides: Partial<EventRegistrationView>): EventRegistrationView {
  return {
    id: "event-1",
    clubId: "club-1",
    title: "Robotics Night",
    description: "Build a small robot",
    phase: "REGISTRATION_OPEN",
    registrationOpensAt: "2026-03-01T00:00:00Z",
    registrationClosesAt: "2026-03-10T00:00:00Z",
    startsAt: "2026-03-20T00:00:00Z",
    endsAt: "2026-03-20T02:00:00Z",
    capacity: 40,
    enrolledCount: 28,
    waitlistCount: 0,
    enrolled: true,
    enrollmentVia: "DIRECT",
    waitlistPosition: null,
    registrationForm: { fields: [] },
    answersSaved: true,
    answers: {},
    ...overrides,
  };
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <MyEventsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("MyEventsPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders a loading state while the request is in flight", () => {
    vi.spyOn(httpClient, "get").mockReturnValue(new Promise(() => {}));

    renderPage();

    expect(screen.getByRole("status")).toHaveTextContent(/loading/i);
  });

  it("renders the Student's enrolled events once the request resolves", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse({ items: [view({})], page: 0, size: 20, total: 1 }),
    );

    renderPage();

    await waitFor(() => expect(screen.getByText("Robotics Night")).toBeInTheDocument());
    expect(screen.getByText("12 of 40 seats left")).toBeInTheDocument();
  });

  it("carries the promotion signal in the Students own Event list", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse({ items: [view({ enrollmentVia: "PROMOTED" })], page: 0, size: 20, total: 1 }),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("You were on the Waitlist — you’re in.")).toBeInTheDocument();
    });
  });

  it("flags a safe Seat whose answers still need retrying", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse({ items: [view({ answersSaved: false })], page: 0, size: 20, total: 1 }),
    );

    renderPage();

    expect(await screen.findByText("Answers still need saving.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Retry answers" })).toHaveAttribute("href", "/events/event-1");
  });

  it("renders an empty state when the Student has not registered for anything", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse({ items: [], page: 0, size: 20, total: 0 }));

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("You haven’t registered for any events yet.")).toBeInTheDocument();
    });
  });

  it("renders the error state from the error's code", async () => {
    vi.spyOn(httpClient, "get").mockRejectedValue(
      new ApiError({ code: "INTERNAL_ERROR", status: 500, title: "Internal Server Error", detail: "boom" }),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("INTERNAL_ERROR");
    });
  });

  it("advances to the next page and back", async () => {
    const user = userEvent.setup();
    const getSpy = vi
      .spyOn(httpClient, "get")
      .mockResolvedValue(axiosResponse({ items: [view({})], page: 0, size: 20, total: 40 }));

    renderPage();
    await waitFor(() => expect(screen.getByText("Page 1 of 2")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(2));
    expect(getSpy.mock.calls[1]?.[1]).toMatchObject({ params: { page: 1 } });

    await user.click(screen.getByRole("button", { name: "Previous" }));
    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(3));
    expect(getSpy.mock.calls[2]?.[1]).toMatchObject({ params: { page: 0 } });
  });

  it("disables Previous on the first page", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse({ items: [view({})], page: 0, size: 20, total: 1 }),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    });
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });
});

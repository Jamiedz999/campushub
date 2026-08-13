import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosHeaders } from "axios";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../lib/apiError";
import type { CurrentActor } from "../../../lib/auth";
import { httpClient } from "../../../lib/httpClient";
import type { EventBrowseItem } from "../types";
import { EventsBrowsePage } from "./EventsBrowsePage";

function axiosResponse<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

function item(overrides: Partial<EventBrowseItem>): EventBrowseItem {
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
    ...overrides,
  };
}

const STUDENT: CurrentActor = {
  accountId: "student-1",
  email: "student@example.edu",
  displayName: "Student One",
  systemRole: "STUDENT",
  officerClubIds: [],
};

function renderPage(actor: CurrentActor = STUDENT) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryDefaults(["auth", "me"], { staleTime: Number.POSITIVE_INFINITY });
  client.setQueryData(["auth", "me"], actor);
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <EventsBrowsePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("EventsBrowsePage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders a loading state while the request is in flight", () => {
    vi.spyOn(httpClient, "get").mockReturnValue(new Promise(() => {}));

    renderPage();

    expect(screen.getByRole("status")).toHaveTextContent(/loading/i);
  });

  it("renders events once the request resolves, including the Phase message", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse({ items: [item({})], page: 0, size: 20, total: 1 }),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Robotics Night")).toBeInTheDocument();
    });
    expect(screen.getByText("12 of 40 seats left")).toBeInTheDocument();
    expect(screen.getByText("Page 1 of 1")).toBeInTheDocument();
  });

  it("links an Officer to capacity management only for their own Clubs Event", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse({
        items: [item({}), item({ id: "event-2", clubId: "club-2", title: "Chess Night" })],
        page: 0,
        size: 20,
        total: 2,
      }),
    );

    renderPage({ ...STUDENT, officerClubIds: ["club-1"] });

    const link = await screen.findByRole("link", { name: "Manage capacity" });
    expect(link).toHaveAttribute("href", "/officer/events/event-1/capacity");
    expect(screen.getAllByRole("link", { name: "Manage capacity" })).toHaveLength(1);
  });

  it("renders an empty state when no events match", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse({ items: [], page: 0, size: 20, total: 0 }));

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("No events match your filters.")).toBeInTheDocument();
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

  it("re-queries with the search term when the search form is submitted", async () => {
    const user = userEvent.setup();
    const getSpy = vi
      .spyOn(httpClient, "get")
      .mockResolvedValue(axiosResponse({ items: [], page: 0, size: 20, total: 0 }));

    renderPage();
    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(1));

    await user.type(screen.getByLabelText("Search events"), "robot");
    await user.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(2));
    const lastCall = getSpy.mock.calls[getSpy.mock.calls.length - 1];
    expect(lastCall?.[1]).toMatchObject({ params: { q: "robot", page: 0 } });
  });

  it("re-queries with hasFreeSeat when that filter is toggled", async () => {
    const user = userEvent.setup();
    const getSpy = vi
      .spyOn(httpClient, "get")
      .mockResolvedValue(axiosResponse({ items: [], page: 0, size: 20, total: 0 }));

    renderPage();
    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(1));

    await user.click(screen.getByLabelText("Has a free seat"));

    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(2));
    const lastCall = getSpy.mock.calls[getSpy.mock.calls.length - 1];
    expect(lastCall?.[1]).toMatchObject({ params: { hasFreeSeat: true, page: 0 } });
  });

  it("advances to the next page and back", async () => {
    const user = userEvent.setup();
    const getSpy = vi
      .spyOn(httpClient, "get")
      .mockResolvedValue(axiosResponse({ items: [item({})], page: 0, size: 20, total: 40 }));

    renderPage();
    await waitFor(() => expect(screen.getByText("Page 1 of 2")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(2));
    expect(getSpy.mock.calls[1]?.[1]).toMatchObject({ params: { page: 1 } });

    await user.click(screen.getByRole("button", { name: "Previous" }));
    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(3));
    expect(getSpy.mock.calls[2]?.[1]).toMatchObject({ params: { page: 0 } });
  });

  it("re-queries with openForRegistration when that filter is toggled", async () => {
    const user = userEvent.setup();
    const getSpy = vi
      .spyOn(httpClient, "get")
      .mockResolvedValue(axiosResponse({ items: [], page: 0, size: 20, total: 0 }));

    renderPage();
    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(1));

    await user.click(screen.getByLabelText("Open for registration"));

    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(2));
    expect(getSpy.mock.calls[1]?.[1]).toMatchObject({ params: { openForRegistration: true, page: 0 } });

    await user.click(screen.getByLabelText("Open for registration"));

    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(3));
    expect(getSpy.mock.calls[2]?.[1]?.params).not.toHaveProperty("openForRegistration");
  });

  it("re-queries with the chosen sort when the sort select changes", async () => {
    const user = userEvent.setup();
    const getSpy = vi
      .spyOn(httpClient, "get")
      .mockResolvedValue(axiosResponse({ items: [], page: 0, size: 20, total: 0 }));

    renderPage();
    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(1));

    await user.selectOptions(screen.getByLabelText("Sort"), "Starting latest");

    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(2));
    expect(getSpy.mock.calls[1]?.[1]).toMatchObject({ params: { sort: "STARTS_AT_DESC", page: 0 } });
  });

  it("disables Previous on the first page", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse({ items: [item({})], page: 0, size: 20, total: 1 }),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    });
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });
});

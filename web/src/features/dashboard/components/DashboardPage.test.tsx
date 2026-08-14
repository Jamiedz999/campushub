import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosHeaders } from "axios";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../lib/apiError";
import { httpClient } from "../../../lib/httpClient";
import type { Dashboard } from "../types";
import { DashboardPage } from "./DashboardPage";

// The same hand-computed fixture the pure functions and the server-side pipelines are checked against:
// three finished Events, 180 Seats, 142 enrolled, 109 attended of which 15 were manual overrides.
const OFFICER_VIEW: Dashboard = {
  scope: "CLUB",
  from: "2025-09-01T00:00:00Z",
  to: "2026-08-14T10:15:00Z",
  totals: {
    eventsRun: 3,
    capacity: 180,
    enrolled: 142,
    attended: 109,
    promoted: 10,
    everQueued: 22,
    unmetDemand: 14,
    manualAttendance: 15,
  },
  months: [
    { month: "2026-03", eventsRun: 1, capacity: 100, enrolled: 80, attended: 60 },
    { month: "2026-04", eventsRun: 2, capacity: 80, enrolled: 62, attended: 49 },
  ],
  clubs: [
    {
      clubId: "club-a",
      clubName: "Robotics Society",
      eventsRun: 3,
      capacity: 180,
      enrolled: 142,
      attended: 109,
      unmetDemand: 14,
    },
  ],
  events: [
    {
      eventId: "event-1",
      title: "Hack night",
      clubId: "club-a",
      clubName: "Robotics Society",
      endsAt: "2026-04-15T22:00:00Z",
      capacity: 50,
      enrolled: 50,
      attended: 40,
      unmetDemand: 9,
    },
  ],
  excluded: { draft: 1, cancelled: 3, inProgress: 0 },
};

const ADMIN_VIEW: Dashboard = {
  ...OFFICER_VIEW,
  scope: "ALL_CLUBS",
  clubs: [
    ...OFFICER_VIEW.clubs,
    {
      clubId: "club-b",
      clubName: "Choir",
      eventsRun: 1,
      capacity: 30,
      enrolled: 12,
      attended: 9,
      unmetDemand: 0,
    },
  ],
};

function axiosResponse<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

function mockRead(view: Dashboard) {
  return vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view));
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("DashboardPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("puts each headline metric against the denominator its definition names", async () => {
    mockRead(OFFICER_VIEW);

    renderPage();

    const headline = await screen.findByRole("region", { name: "Headline metrics" });
    // 142/180 fill, 109/142 attendance, the rest no-shows, 15/109 overrides.
    expect(within(headline).getByText("79%")).toBeVisible();
    expect(within(headline).getByText("77%")).toBeVisible();
    expect(within(headline).getByText("23%")).toBeVisible();
    expect(within(headline).getByText("14%")).toBeVisible();
    expect(within(headline).getByText("109 of 142 enrolled")).toBeVisible();
  });

  it("states what the range left out rather than leaving it to be inferred", async () => {
    mockRead(OFFICER_VIEW);

    renderPage();

    const notice = await screen.findByRole("note");
    expect(notice).toHaveTextContent("1 Draft Event not shown");
    expect(notice).toHaveTextContent("3 Cancelled Events not shown");
  });

  it("says how many Events the numbers were counted over", async () => {
    mockRead(OFFICER_VIEW);

    renderPage();

    expect(await screen.findByText(/Counted over 3 finished Events/)).toBeVisible();
  });

  it("shows a Club Officer their own Clubs and no cross-club comparison", async () => {
    mockRead(OFFICER_VIEW);

    renderPage();

    expect(await screen.findByRole("heading", { name: "Attendance for your Clubs" })).toBeVisible();
    expect(screen.queryByRole("region", { name: "Club comparison" })).not.toBeInTheDocument();
  });

  it("gives a University Admin the cross-club comparison", async () => {
    mockRead(ADMIN_VIEW);

    renderPage();

    expect(await screen.findByRole("heading", { name: "Attendance across every Club" })).toBeVisible();
    expect(screen.getByRole("region", { name: "Club comparison" })).toBeVisible();
  });

  it("re-reads with a new window when the time range changes", async () => {
    const getSpy = mockRead(OFFICER_VIEW);

    renderPage();
    await screen.findByRole("region", { name: "Headline metrics" });
    await userEvent.selectOptions(screen.getByLabelText(/Time range/), "3m");

    const windows = getSpy.mock.calls.map((call) => call[1]?.params);
    expect(windows).toHaveLength(2);
    expect(windows[0]).not.toEqual(windows[1]);
  });

  it("asks for no window at all when the whole history is chosen", async () => {
    const getSpy = mockRead(OFFICER_VIEW);

    renderPage();
    await screen.findByRole("region", { name: "Headline metrics" });
    await userEvent.selectOptions(screen.getByLabelText(/Time range/), "all");

    expect(getSpy.mock.lastCall?.[1]?.params).toEqual({ from: undefined });
  });

  it("tells an account with no Clubs that the dashboard is scoped to Clubs it officers", async () => {
    vi.spyOn(httpClient, "get").mockRejectedValue(
      new ApiError({ code: "NOT_FOUND", status: 404, title: "Not Found", detail: "No dashboard." }),
    );

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(/scoped to the Clubs you are an Officer of/);
  });

  it("reports any other failure by its stable code", async () => {
    vi.spyOn(httpClient, "get").mockRejectedValue(
      new ApiError({ code: "NETWORK_ERROR", status: 0, title: "Network", detail: "offline" }),
    );

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("NETWORK_ERROR");
  });

  it("says it is loading before the first read lands", () => {
    vi.spyOn(httpClient, "get").mockReturnValue(new Promise(() => {}));

    renderPage();

    expect(screen.getByRole("status")).toHaveTextContent("Loading the dashboard…");
  });
});

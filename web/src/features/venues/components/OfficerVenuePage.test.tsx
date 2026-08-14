import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosHeaders } from "axios";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../lib/apiError";
import { httpClient } from "../../../lib/httpClient";
import { OfficerVenuePage } from "./OfficerVenuePage";

function axiosResponse<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

const EVENT = {
  id: "event-1",
  title: "Robotics Night",
  startsAt: "2026-03-20T10:00:00Z",
  endsAt: "2026-03-20T11:00:00Z",
  venueId: null,
};

const VENUES = {
  items: [
    { id: "venue-1", name: "Sports Hall" },
    { id: "venue-2", name: "Science Theatre" },
  ],
  page: 0,
  size: 100,
  total: 2,
};

function mockReads() {
  return vi.spyOn(httpClient, "get").mockImplementation((url: string) => {
    if (url === "/events/event-1") {
      return Promise.resolve(axiosResponse(EVENT));
    }
    if (url === "/venues") {
      return Promise.resolve(axiosResponse(VENUES));
    }
    if (url === "/venues/venue-1/days/2026-03-20") {
      return Promise.resolve(
        axiosResponse({
          venue: VENUES.items[0],
          date: "2026-03-20",
          bookings: [
            { eventId: "event-early", startMinute: 540, endMinute: 600 },
            { eventId: "event-late", startMinute: 720, endMinute: 780 },
          ],
        }),
      );
    }
    if (url === "/venues/venue-2/days/2026-03-20") {
      return Promise.resolve(
        axiosResponse({ venue: VENUES.items[1], date: "2026-03-20", bookings: [] }),
      );
    }
    return Promise.reject(new Error(`Unexpected GET ${url}`));
  });
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/officer/events/event-1/venue"]}>
        <Routes>
          <Route path="/officer/events/:eventId/venue" element={<OfficerVenuePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function renderWithoutEventId() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/officer/venue"]}>
        <Routes>
          <Route path="/officer/venue" element={<OfficerVenuePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("OfficerVenuePage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows the selected Venue-day as an ordered timeline", async () => {
    mockReads();

    renderPage();

    expect(await screen.findByRole("heading", { name: "Book a venue · Robotics Night" })).toBeInTheDocument();
    const timeline = await screen.findByRole("list", { name: "Sports Hall timeline" });
    expect(timeline).toHaveTextContent("09:00–10:00");
    expect(timeline).toHaveTextContent("12:00–13:00");
    expect(screen.getByText(/Event time: 10:00–11:00/)).toBeInTheDocument();
  });

  it("books the Event's current time into the selected Venue", async () => {
    const user = userEvent.setup();
    mockReads();
    const putSpy = vi.spyOn(httpClient, "put").mockResolvedValue(axiosResponse(undefined));

    renderPage();
    await screen.findByRole("list", { name: "Sports Hall timeline" });
    await user.click(screen.getByRole("button", { name: "Book this venue" }));

    await waitFor(() =>
      expect(putSpy).toHaveBeenCalledWith("/events/event-1/slot", {
        venueId: "venue-1",
        startsAt: EVENT.startsAt,
        endsAt: EVENT.endsAt,
      }),
    );
    expect(await screen.findByRole("status", { name: "booking result" })).toHaveTextContent(
      "Venue booked.",
    );
  });

  it("makes a lost booking race clear and removes the refusal when another Venue is chosen", async () => {
    const user = userEvent.setup();
    mockReads();
    vi.spyOn(httpClient, "put").mockRejectedValue(
      new ApiError({
        code: "SLOT_TAKEN",
        status: 409,
        title: "Conflict",
        detail: "overlap",
      }),
    );

    renderPage();
    await screen.findByRole("list", { name: "Sports Hall timeline" });
    await user.click(screen.getByRole("button", { name: "Book this venue" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "That slot was taken moments ago. Your Event has not changed. Choose another Venue.",
    );

    await user.selectOptions(screen.getByLabelText("Venue"), "Science Theatre");

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(await screen.findByRole("list", { name: "Science Theatre timeline" })).toHaveTextContent(
      "No bookings yet.",
    );
  });

  it("releases an existing booking idempotently", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockImplementation((url: string) => {
      if (url === "/events/event-1") {
        return Promise.resolve(axiosResponse({ ...EVENT, venueId: "venue-1" }));
      }
      if (url === "/venues") {
        return Promise.resolve(axiosResponse(VENUES));
      }
      return Promise.resolve(
        axiosResponse({ venue: VENUES.items[0], date: "2026-03-20", bookings: [] }),
      );
    });
    const deleteSpy = vi.spyOn(httpClient, "delete").mockResolvedValue(axiosResponse(undefined));

    renderPage();
    await user.click(await screen.findByRole("button", { name: "Release venue" }));

    await waitFor(() => expect(deleteSpy).toHaveBeenCalledWith("/events/event-1/slot"));
    expect(await screen.findByRole("status", { name: "booking result" })).toHaveTextContent(
      "Venue released.",
    );
  });

  it("explains when no Event id was supplied", () => {
    renderWithoutEventId();

    expect(screen.getByRole("alert")).toHaveTextContent("No Event was specified.");
  });

  it("shows an Event load failure", async () => {
    vi.spyOn(httpClient, "get").mockImplementation((url: string) => {
      if (url === "/events/event-1") {
        return Promise.reject(
          new ApiError({ code: "NOT_FOUND", status: 404, title: "Not Found", detail: "missing" }),
        );
      }
      return Promise.resolve(axiosResponse(VENUES));
    });

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load Venue booking (NOT_FOUND).");
  });

  it("shows a Venue list load failure", async () => {
    vi.spyOn(httpClient, "get").mockImplementation((url: string) => {
      if (url === "/events/event-1") {
        return Promise.resolve(axiosResponse(EVENT));
      }
      return Promise.reject(
        new ApiError({ code: "NETWORK_ERROR", status: 0, title: "Network Error", detail: "offline" }),
      );
    });

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load Venue booking (NETWORK_ERROR).",
    );
  });

  it("shows an empty state when no Venue has been created", async () => {
    vi.spyOn(httpClient, "get").mockImplementation((url: string) =>
      Promise.resolve(
        axiosResponse(url === "/events/event-1" ? EVENT : { ...VENUES, items: [], total: 0 }),
      ),
    );

    renderPage();

    expect(await screen.findByText("No Venues are available yet.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Book this venue" })).not.toBeInTheDocument();
  });

  it("shows a Venue-day timeline failure", async () => {
    vi.spyOn(httpClient, "get").mockImplementation((url: string) => {
      if (url === "/events/event-1") {
        return Promise.resolve(axiosResponse(EVENT));
      }
      if (url === "/venues") {
        return Promise.resolve(axiosResponse(VENUES));
      }
      return Promise.reject(
        new ApiError({ code: "NOT_FOUND", status: 404, title: "Not Found", detail: "missing" }),
      );
    });

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load Venue timeline (NOT_FOUND).");
    expect(screen.getByRole("button", { name: "Book this venue" })).toBeDisabled();
  });

  it("keeps a non-contention booking refusal stable and clear", async () => {
    const user = userEvent.setup();
    mockReads();
    vi.spyOn(httpClient, "put").mockRejectedValue(
      new ApiError({
        code: "SLOT_IN_DST_TRANSITION",
        status: 409,
        title: "Conflict",
        detail: "transition",
      }),
    );

    renderPage();
    await user.click(await screen.findByRole("button", { name: "Book this venue" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The Venue could not be booked (SLOT_IN_DST_TRANSITION). Your Event has not changed.",
    );
  });

  it("shows a release failure without hiding the current booking", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockImplementation((url: string) => {
      if (url === "/events/event-1") {
        return Promise.resolve(axiosResponse({ ...EVENT, venueId: "venue-1" }));
      }
      if (url === "/venues") {
        return Promise.resolve(axiosResponse(VENUES));
      }
      return Promise.resolve(
        axiosResponse({ venue: VENUES.items[0], date: "2026-03-20", bookings: [] }),
      );
    });
    vi.spyOn(httpClient, "delete").mockRejectedValue(
      new ApiError({ code: "NETWORK_ERROR", status: 0, title: "Network Error", detail: "offline" }),
    );

    renderPage();
    await user.click(await screen.findByRole("button", { name: "Release venue" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Venue could not be released (NETWORK_ERROR).",
    );
    expect(screen.getByRole("button", { name: "Release venue" })).toBeInTheDocument();
  });
});

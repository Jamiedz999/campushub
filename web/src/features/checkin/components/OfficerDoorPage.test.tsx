import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosHeaders } from "axios";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../lib/apiError";
import { httpClient } from "../../../lib/httpClient";
import { DoorSocketDouble } from "../doorSocketDouble";
import { OfficerDoorPage } from "./OfficerDoorPage";

const DOOR_CODE = {
  eventId: "event-1",
  title: "Intro to Climbing",
  token: "event-1.29566667.signature",
  rotatesAt: "2026-03-20T18:05:00Z",
  checkInOpensAt: "2026-03-20T17:45:00Z",
  checkInClosesAt: "2026-03-20T20:00:00Z",
  checkInOpen: true,
};

const ROSTER = {
  eventId: "event-1",
  title: "Intro to Climbing",
  items: [
    { studentId: "student-1", displayName: "R. Nolan", at: "2026-03-20T18:04:00Z", method: "SCANNED" },
    { studentId: "student-2", displayName: "S. Kaur", at: "2026-03-20T18:09:00Z", method: "MANUAL" },
    { studentId: "student-3", displayName: "T. Adeyemi", at: null, method: null },
  ],
};

// The same Roster after the last Seat holder has scanned in — what a re-read returns once three
// people are in the room rather than two.
const SEATS_ALL_TAKEN = {
  ...ROSTER,
  items: ROSTER.items.map((entry) =>
    entry.at === null ? { ...entry, at: "2026-03-20T18:11:00Z", method: "SCANNED" } : entry,
  ),
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

function mockReads(doorCode: unknown = DOOR_CODE, roster: unknown = ROSTER) {
  return vi.spyOn(httpClient, "get").mockImplementation((url: string) => {
    if (url === "/events/event-1/door-code") {
      return Promise.resolve(axiosResponse(doorCode));
    }
    if (url === "/events/event-1/attendance") {
      return Promise.resolve(axiosResponse(roster));
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
      <MemoryRouter initialEntries={["/officer/events/event-1/door"]}>
        <Routes>
          <Route path="/officer/events/:eventId/door" element={<OfficerDoorPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("OfficerDoorPage", () => {
  beforeEach(() => {
    DoorSocketDouble.reset();
    vi.stubGlobal("WebSocket", DoorSocketDouble);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  // The link inside the code is asserted where it is built, in scanUrl.test.ts — the encoded SVG does
  // not carry it back out. What matters here is that a code is on screen at all while the door is open.
  it("renders the rotating code as a QR while the door is open", async () => {
    mockReads();

    const { container } = renderPage();

    expect(await screen.findByText("Door · Intro to Climbing")).toBeInTheDocument();
    expect(container.querySelector("svg")).not.toBeNull();
    expect(container.querySelectorAll("path").length).toBeGreaterThan(0);
  });

  // Derived from the Roster rather than from anything the socket or the door-code endpoint said. The
  // socket only ever says "re-read"; every number on this screen comes from the snapshot that follows.
  it("derives the count from the Roster rather than reading one off the code", async () => {
    mockReads();

    renderPage();

    const doorCode = await screen.findByRole("region", { name: "Door code" });
    expect(within(doorCode).getByText("2")).toBeInTheDocument();
    expect(within(doorCode).getByText("/ 3")).toBeInTheDocument();
    expect(within(doorCode).getByText(/1 marked by hand/i)).toBeInTheDocument();
    expect(within(doorCode).getByText(/rotates at 18:05/i)).toBeInTheDocument();
  });

  it("says when the door is shut instead of showing a code that would not admit anyone", async () => {
    mockReads({ ...DOOR_CODE, checkInOpen: false });

    const { container } = renderPage();

    expect(await screen.findByText(/check-in opens at 17:45/i)).toBeInTheDocument();
    expect(container.querySelector("svg")).toBeNull();
  });

  it("keeps scanned and manual records apart in the override list", async () => {
    mockReads();

    renderPage();

    const roster = await screen.findByRole("list", { name: "Enrolled students" });
    const rows = within(roster).getAllByRole("listitem");
    expect(rows[0]).toHaveTextContent("R. Nolan");
    expect(rows[0]).toHaveTextContent("Scanned 18:04");
    expect(rows[1]).toHaveTextContent("Manual 18:09");
    expect(rows[2]).toHaveTextContent("Not in");
  });

  it("offers Mark present only for a Student who is not already in", async () => {
    mockReads();

    renderPage();

    const roster = await screen.findByRole("list", { name: "Enrolled students" });
    expect(within(roster).getAllByRole("button", { name: "Mark present" })).toHaveLength(1);
  });

  it("marks a Student present and re-reads rather than patching the list locally", async () => {
    mockReads();
    const putSpy = vi.spyOn(httpClient, "put").mockResolvedValue(axiosResponse(undefined));

    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Mark present" }));

    expect(putSpy).toHaveBeenCalledWith("/events/event-1/attendance/student-3");
    expect(await screen.findByLabelText("override result")).toHaveTextContent("Marked present.");
  });

  it("reports a refused override by its code", async () => {
    mockReads();
    vi.spyOn(httpClient, "put").mockRejectedValue(
      new ApiError({ code: "NOT_ON_ROSTER", status: 409, title: "Conflict", detail: "" }),
    );

    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Mark present" }));

    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent("could not be marked present (NOT_ON_ROSTER)"),
    );
  });

  it("says so when nobody holds a Seat yet", async () => {
    mockReads(DOOR_CODE, { ...ROSTER, items: [] });

    renderPage();

    expect(await screen.findByText("Nobody holds a Seat yet.")).toBeInTheDocument();
  });

  it("keeps the code on screen even when the roster cannot be read", async () => {
    vi.spyOn(httpClient, "get").mockImplementation((url: string) => {
      if (url === "/events/event-1/door-code") {
        return Promise.resolve(axiosResponse(DOOR_CODE));
      }
      return Promise.reject(new ApiError({ code: "NOT_FOUND", status: 404, title: "", detail: "" }));
    });

    const { container } = renderPage();

    expect(await screen.findByText(/could not load the roster \(NOT_FOUND\)/i)).toBeInTheDocument();
    expect(container.querySelector("svg")).not.toBeNull();
  });

  it("refuses to guess which Event the door belongs to", async () => {
    mockReads();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={["/officer/door"]}>
          <Routes>
            <Route path="/officer/door" element={<OfficerDoorPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("No Event was specified.");
  });

  it("says so when the caller is not this Club's Officer", async () => {
    vi.spyOn(httpClient, "get").mockRejectedValue(
      new ApiError({ code: "NOT_FOUND", status: 404, title: "Not Found", detail: "" }),
    );

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not open the door screen (NOT_FOUND)");
  });

  it("counts up without a reload when a Student scans", async () => {
    const get = mockReads();

    renderPage();

    const doorCode = await screen.findByRole("region", { name: "Door code" });
    expect(within(doorCode).getByText("2")).toBeInTheDocument();

    act(() => DoorSocketDouble.current().connect());
    get.mockImplementation((url: string) =>
      url === "/events/event-1/door-code"
        ? Promise.resolve(axiosResponse(DOOR_CODE))
        : Promise.resolve(axiosResponse(SEATS_ALL_TAKEN)),
    );
    act(() => DoorSocketDouble.current().deliver('{"type":"attendance-changed","eventId":"event-1"}'));

    expect(await within(doorCode).findByText("3")).toBeInTheDocument();
  });

  // The acceptance criterion in Issue #9: a missed message or a dropped connection never leaves the
  // screen wrong. Three people walk in while the projector's wifi is out and no hint reaches this
  // screen at all — reconnecting re-reads, so the number is right without anyone touching it.
  it("shows the right number after a dropped connection that missed every hint", async () => {
    const get = mockReads();

    renderPage();

    const doorCode = await screen.findByRole("region", { name: "Door code" });
    act(() => DoorSocketDouble.current().connect());
    expect(await within(doorCode).findByText("2")).toBeInTheDocument();

    act(() => DoorSocketDouble.current().drop());
    get.mockImplementation((url: string) =>
      url === "/events/event-1/door-code"
        ? Promise.resolve(axiosResponse(DOOR_CODE))
        : Promise.resolve(axiosResponse(SEATS_ALL_TAKEN)),
    );

    await waitFor(() => expect(DoorSocketDouble.opened).toHaveLength(2), { timeout: 3_000 });
    act(() => DoorSocketDouble.current().connect());

    expect(await within(doorCode).findByText("3")).toBeInTheDocument();
  });

  it("says whether it is counting live or falling back to re-reading", async () => {
    mockReads();

    renderPage();

    const live = await screen.findByLabelText("Live count");
    expect(live).toHaveTextContent("Not connected");

    act(() => DoorSocketDouble.current().connect());

    await waitFor(() => expect(live).toHaveTextContent("Counting live as people scan."));
  });
});

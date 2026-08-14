import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosHeaders } from "axios";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../lib/apiError";
import { httpClient } from "../../../lib/httpClient";
import { StudentCheckInPage } from "./StudentCheckInPage";

const TOKEN = "event-1.29566667.signature";

function axiosResponse<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

function renderPage(search = `?token=${TOKEN}`) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/checkin/event-1${search}`]}>
        <Routes>
          <Route path="/checkin/:eventId" element={<StudentCheckInPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function refusal(code: string, extra: Record<string, unknown> = {}) {
  return new ApiError({
    code,
    status: 409,
    title: "Conflict",
    detail: "Refused.",
    extensions: extra,
  });
}

describe("StudentCheckInPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("tells a Student who arrived without a code where to point their camera", async () => {
    const postSpy = vi.spyOn(httpClient, "post");

    renderPage("");

    expect(await screen.findByText(/point your camera at the code/i)).toBeInTheDocument();
    expect(postSpy).not.toHaveBeenCalled();
  });

  it("submits the scanned code on arrival and confirms the check-in", async () => {
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValue(
      axiosResponse({
        eventId: "event-1",
        eventTitle: "Intro to Climbing",
        at: "2026-03-20T18:04:00Z",
        method: "SCANNED",
      }),
    );

    renderPage();

    expect(await screen.findByText("Checked in")).toBeInTheDocument();
    expect(screen.getByText("Intro to Climbing")).toBeInTheDocument();
    expect(screen.getByText("18:04")).toBeInTheDocument();
    expect(postSpy).toHaveBeenCalledTimes(1);
    expect(postSpy).toHaveBeenCalledWith("/events/event-1/attendance", { token: TOKEN });
  });

  it("reads an expired code as a normal retry and offers a fresh scan", async () => {
    vi.spyOn(httpClient, "post").mockRejectedValue(refusal("TOKEN_EXPIRED"));

    renderPage();

    expect(await screen.findByText("Code expired")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Scan again" }));

    // Back to the ready screen: re-sending a code the server refused would only be refused again.
    expect(await screen.findByText(/point your camera at the code/i)).toBeInTheDocument();
  });

  it("tells a second scan when the first one was", async () => {
    vi.spyOn(httpClient, "post").mockRejectedValue(
      refusal("ALREADY_CHECKED_IN", { at: "2026-03-20T18:04:00Z", method: "SCANNED" }),
    );

    renderPage();

    expect(await screen.findByText("Already checked in")).toBeInTheDocument();
    expect(screen.getByText("You checked in at 18:04.")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("is kind to a waitlisted Student and offers no button that would not help", async () => {
    vi.spyOn(httpClient, "post").mockRejectedValue(refusal("NOT_ON_ROSTER"));

    renderPage();

    expect(await screen.findByText("You're on the waitlist")).toBeInTheDocument();
    expect(screen.getByText(/speak to the organiser/i)).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("names the manual override when the request never reaches the server, and retries the same code", async () => {
    const postSpy = vi
      .spyOn(httpClient, "post")
      .mockRejectedValueOnce(new ApiError({ code: "NETWORK_ERROR", status: 0, title: "Network", detail: "" }))
      .mockResolvedValueOnce(
        axiosResponse({
          eventId: "event-1",
          eventTitle: "Intro to Climbing",
          at: "2026-03-20T18:04:00Z",
          method: "SCANNED",
        }),
      );

    renderPage();

    expect(await screen.findByText("Couldn't reach CampusHub")).toBeInTheDocument();
    expect(screen.getByText(/ask the organiser to mark you present/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByText("Checked in")).toBeInTheDocument();
    await waitFor(() => expect(postSpy).toHaveBeenCalledTimes(2));
  });

  it("refuses to guess which Event a code belongs to", async () => {
    const postSpy = vi.spyOn(httpClient, "post");
    const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[`/checkin?token=${TOKEN}`]}>
          <Routes>
            <Route path="/checkin" element={<StudentCheckInPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("No Event was specified.");
    expect(postSpy).not.toHaveBeenCalled();
  });

  it("explains the window when check-in is closed", async () => {
    vi.spyOn(httpClient, "post").mockRejectedValue(refusal("CHECK_IN_WINDOW_CLOSED"));

    renderPage();

    expect(await screen.findByText("Check-in is closed")).toBeInTheDocument();
  });
});

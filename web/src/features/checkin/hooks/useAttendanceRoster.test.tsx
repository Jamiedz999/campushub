import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { AxiosHeaders } from "axios";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { DoorSocketDouble } from "../__doorSocketDouble";
import { useAttendanceRoster } from "./useAttendanceRoster";

function rosterOf(attended: number) {
  return {
    data: {
      eventId: "event-1",
      title: "Intro to Climbing",
      items: [
        { studentId: "student-1", displayName: "R. Nolan", at: null, method: null },
        { studentId: "student-2", displayName: "S. Kaur", at: null, method: null },
      ].map((entry, index) =>
        index < attended ? { ...entry, at: "2026-03-20T18:04:00Z", method: "SCANNED" } : entry,
      ),
    },
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useAttendanceRoster", () => {
  beforeEach(() => {
    DoorSocketDouble.reset();
    vi.stubGlobal("WebSocket", DoorSocketDouble);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("re-reads the whole snapshot on a hint rather than trusting what the hint said", async () => {
    const get = vi.spyOn(httpClient, "get").mockResolvedValue(rosterOf(0));
    const view = renderHook(() => useAttendanceRoster("event-1"), { wrapper });
    await waitFor(() => expect(view.result.current.roster.isSuccess).toBe(true));

    act(() => DoorSocketDouble.current().connect());
    get.mockResolvedValue(rosterOf(1));
    act(() => DoorSocketDouble.current().deliver('{"type":"attendance-changed","eventId":"event-1"}'));

    await waitFor(() =>
      expect(view.result.current.roster.data?.items[0]?.method).toBe("SCANNED"),
    );
    expect(get).toHaveBeenCalledWith("/events/event-1/attendance");
  });

  it("keeps re-reading on a timer while the socket is down, and stops once it is up", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const get = vi.spyOn(httpClient, "get").mockResolvedValue(rosterOf(0));
    const view = renderHook(() => useAttendanceRoster("event-1"), { wrapper });
    await waitFor(() => expect(view.result.current.roster.isSuccess).toBe(true));
    expect(view.result.current.live).toBe(false);

    // The socket never came up — an old browser, a proxy that strips upgrades. The screen is a few
    // seconds behind instead of wrong, which is the whole point of the hint carrying no state.
    await act(() => vi.advanceTimersByTimeAsync(10_000));
    expect(get).toHaveBeenCalledTimes(2);

    act(() => DoorSocketDouble.current().connect());
    await waitFor(() => expect(view.result.current.live).toBe(true));
    const afterConnecting = get.mock.calls.length;

    await act(() => vi.advanceTimersByTimeAsync(30_000));
    expect(get).toHaveBeenCalledTimes(afterConnecting);
  });
});

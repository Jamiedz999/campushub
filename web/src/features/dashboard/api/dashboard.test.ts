import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { getDashboard } from "./dashboard";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

const EMPTY = {
  scope: "CLUB" as const,
  from: "2026-01-01T00:00:00Z",
  to: "2026-08-14T10:15:00Z",
  totals: {
    eventsRun: 0,
    capacity: 0,
    enrolled: 0,
    attended: 0,
    promoted: 0,
    everQueued: 0,
    unmetDemand: 0,
    manualAttendance: 0,
  },
  months: [],
  clubs: [],
  events: [],
  excluded: { draft: 0, cancelled: 0, inProgress: 0 },
};

describe("getDashboard", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("asks for the caller's own scope when nothing is narrowed", async () => {
    const getSpy = vi.spyOn(httpClient, "get").mockResolvedValue(response(EMPTY));

    await getDashboard();

    expect(getSpy).toHaveBeenCalledWith("/dashboard", { params: {} });
  });

  it("passes the Club and the window through as they are given", async () => {
    const getSpy = vi.spyOn(httpClient, "get").mockResolvedValue(response(EMPTY));

    await getDashboard({ clubId: "club-a", from: "2026-03-01T00:00:00Z", to: "2026-08-01T00:00:00Z" });

    expect(getSpy).toHaveBeenCalledWith("/dashboard", {
      params: { clubId: "club-a", from: "2026-03-01T00:00:00Z", to: "2026-08-01T00:00:00Z" },
    });
  });

  it("returns the payload the server sent, untouched", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(response(EMPTY));

    await expect(getDashboard()).resolves.toEqual(EMPTY);
  });
});

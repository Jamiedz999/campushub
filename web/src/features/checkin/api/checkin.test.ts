import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { checkIn, getAttendanceRoster, getDoorCode, markPresent } from "./checkin";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

const DOOR_CODE = {
  eventId: "event-1",
  title: "Intro to Climbing",
  token: "event-1.29566667.signature",
  rotatesAt: "2026-03-20T18:05:00Z",
  checkInOpensAt: "2026-03-20T17:45:00Z",
  checkInClosesAt: "2026-03-20T20:00:00Z",
  checkInOpen: true,
  capacity: 40,
  enrolledCount: 30,
  attendedCount: 23,
};

describe("checkin api", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("reads the door code from the Event's own sub-resource", async () => {
    const getSpy = vi.spyOn(httpClient, "get").mockResolvedValue(response(DOOR_CODE));

    await expect(getDoorCode("event-1")).resolves.toEqual(DOOR_CODE);
    expect(getSpy).toHaveBeenCalledWith("/events/event-1/door-code");
  });

  it("reads the roster from the attendance sub-resource", async () => {
    const roster = {
      eventId: "event-1",
      title: "Intro to Climbing",
      capacity: 40,
      enrolledCount: 1,
      attendedCount: 0,
      items: [{ studentId: "student-1", displayName: "R. Nolan", attendedAt: null, method: null }],
    };
    const getSpy = vi.spyOn(httpClient, "get").mockResolvedValue(response(roster));

    await expect(getAttendanceRoster("event-1")).resolves.toEqual(roster);
    expect(getSpy).toHaveBeenCalledWith("/events/event-1/attendance");
  });

  it("sends only the scanned code — identity comes from the session", async () => {
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValue(
      response({
        eventId: "event-1",
        eventTitle: "Intro to Climbing",
        at: "2026-03-20T18:04:00Z",
        method: "SCANNED",
      }),
    );

    const result = await checkIn("event-1", "event-1.29566667.signature");

    expect(postSpy).toHaveBeenCalledWith("/events/event-1/attendance", {
      token: "event-1.29566667.signature",
    });
    expect(result.method).toBe("SCANNED");
  });

  it("marks a student present through the idempotent per-student sub-resource", async () => {
    const putSpy = vi.spyOn(httpClient, "put").mockResolvedValue(response(undefined));

    await markPresent("event-1", "student-1");

    expect(putSpy).toHaveBeenCalledWith("/events/event-1/attendance/student-1");
  });
});

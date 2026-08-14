import { describe, expect, it } from "vitest";
import { campusMonthStart, formatCampusTime } from "./campusTimeZone";

describe("formatCampusTime", () => {
  it("reads an instant on the campus clock, not the viewer's", () => {
    // 19:04 UTC in July is 20:04 in Dublin, whatever timezone the browser is in.
    expect(formatCampusTime("2026-07-20T19:04:00Z")).toBe("20:04");
  });
});

describe("campusMonthStart", () => {
  const august = new Date("2026-08-14T10:15:00Z");

  it("is midnight on the first, on the campus clock", () => {
    // Summer: Dublin is an hour ahead, so June began at 23:00 UTC on 31 May.
    expect(campusMonthStart(august, 2)).toBe("2026-05-31T23:00:00.000Z");
    // Winter: Dublin is on UTC, so March began at midnight UTC.
    expect(campusMonthStart(august, 5)).toBe("2026-03-01T00:00:00.000Z");
  });

  it("agrees with the twelve-month window the server falls back to", () => {
    expect(campusMonthStart(august, 11)).toBe("2025-08-31T23:00:00.000Z");
  });

  it("crosses a year boundary without inventing a month", () => {
    expect(campusMonthStart(new Date("2026-01-15T00:00:00Z"), 2)).toBe("2025-11-01T00:00:00.000Z");
  });

  it("is the start of the current month when nothing is counted back", () => {
    expect(campusMonthStart(august, 0)).toBe("2026-07-31T23:00:00.000Z");
  });
});

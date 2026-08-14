import { describe, expect, it } from "vitest";
import { RANGE_OPTIONS, rangeStart } from "./range";

const NOW = new Date("2026-08-14T10:15:00Z");

describe("rangeStart", () => {
  it("counts back whole campus months, so the window begins where a month bucket does", () => {
    // "Last 3 months" is June, July and August to date — and June began at 23:00 UTC on 31 May,
    // because Dublin is an hour ahead in summer.
    expect(rangeStart("3m", NOW)).toBe("2026-05-31T23:00:00.000Z");
    expect(rangeStart("6m", NOW)).toBe("2026-03-01T00:00:00.000Z");
    expect(rangeStart("12m", NOW)).toBe("2025-08-31T23:00:00.000Z");
  });

  it("sends no start at all for the whole history, so the server is not asked for a date it knows better", () => {
    expect(rangeStart("all", NOW)).toBeUndefined();
  });

  it("crosses a year boundary without inventing a month", () => {
    expect(rangeStart("3m", new Date("2026-01-15T00:00:00Z"))).toBe("2025-11-01T00:00:00.000Z");
  });
});

describe("RANGE_OPTIONS", () => {
  it("labels every choice the control offers", () => {
    expect(RANGE_OPTIONS.map((option) => option.value)).toEqual(["3m", "6m", "12m", "all"]);
    expect(RANGE_OPTIONS.every((option) => option.label.length > 0)).toBe(true);
  });
});

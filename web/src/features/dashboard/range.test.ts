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

  it("gives the widest choice a window of its own rather than letting it collapse onto another", () => {
    // Sending no `from` would take the server's twelve-month default, making this option and "Last 12
    // months" the same window under two labels.
    expect(rangeStart("24m", NOW)).toBe("2024-08-31T23:00:00.000Z");
    expect(rangeStart("24m", NOW)).not.toBe(rangeStart("12m", NOW));
  });

  it("crosses a year boundary without inventing a month", () => {
    expect(rangeStart("3m", new Date("2026-01-15T00:00:00Z"))).toBe("2025-11-01T00:00:00.000Z");
  });
});

describe("RANGE_OPTIONS", () => {
  it("labels every choice the control offers", () => {
    expect(RANGE_OPTIONS.map((option) => option.value)).toEqual(["3m", "6m", "12m", "24m"]);
    expect(RANGE_OPTIONS.every((option) => option.label.length > 0)).toBe(true);
  });

  it("gives every choice a different window, so no two labels mean the same read", () => {
    const windows = RANGE_OPTIONS.map((option) => rangeStart(option.value, NOW));

    expect(new Set(windows).size).toBe(RANGE_OPTIONS.length);
  });
});

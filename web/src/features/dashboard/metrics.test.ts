import { describe, expect, it } from "vitest";
import {
  attendanceRate,
  fillRate,
  formatCount,
  formatRate,
  manualOverrideShare,
  noShowRate,
  rate,
  waitlistConversion,
} from "./metrics";
import type { MetricTotals } from "./types";

// Every definition in docs/adr/09-define-attendance-dashboard.md's table, and the denominator each one
// names. The numbers below are the repository fixture's — server-side
// DashboardRepositoryIntegrationTest computes the same totals from real documents, so the two halves of
// the feature are checked against one hand-computed example rather than two.
const TOTALS: MetricTotals = {
  eventsRun: 3,
  capacity: 180,
  enrolled: 142,
  attended: 109,
  promoted: 10,
  everQueued: 22,
  unmetDemand: 14,
  manualAttendance: 15,
};

const NOTHING: MetricTotals = {
  eventsRun: 0,
  capacity: 0,
  enrolled: 0,
  attended: 0,
  promoted: 0,
  everQueued: 0,
  unmetDemand: 0,
  manualAttendance: 0,
};

describe("rate", () => {
  it("divides", () => {
    expect(rate(1, 4)).toBe(0.25);
  });

  it("is null rather than zero or Infinity when there is nothing to divide by", () => {
    // Zero would read as "nobody turned up" and Infinity would render as garbage. Neither is true: the
    // honest answer to "what share of nothing" is that there is no answer, and null is what the
    // formatter turns into a dash.
    expect(rate(0, 0)).toBeNull();
    expect(rate(5, 0)).toBeNull();
  });
});

describe("the metric definitions", () => {
  it("measures fill against capacity", () => {
    expect(fillRate(TOTALS)).toBeCloseTo(142 / 180);
  });

  it("measures attendance against enrolled, not against capacity", () => {
    // The whole point of the definition: a half-empty Event and a no-show problem are different
    // failures, and dividing by capacity would conflate them.
    expect(attendanceRate(TOTALS)).toBeCloseTo(109 / 142);
    expect(attendanceRate(TOTALS)).not.toBeCloseTo(109 / 180);
  });

  it("reports no-shows as the rest of the enrolled", () => {
    expect(noShowRate(TOTALS)).toBeCloseTo(1 - 109 / 142);
  });

  it("converts the Waitlist against everyone who ever queued", () => {
    expect(waitlistConversion(TOTALS)).toBeCloseTo(10 / 22);
  });

  it("counts the Student who joined the queue and then left it in the denominator", () => {
    // 22 ever queued, 10 promoted, 14 still queued at the end: 10 + 14 is 24, not 22, because two
    // Students were promoted out of a queue others had already left. Dividing by promoted + unmetDemand
    // is the arithmetic the ADR's amendment threw out, and it gives a different — wrong — answer.
    expect(waitlistConversion(TOTALS)).not.toBeCloseTo(10 / (10 + 14));
  });

  it("measures the manual override share against all attendance records", () => {
    expect(manualOverrideShare(TOTALS)).toBeCloseTo(15 / 109);
  });

  it("has no answer for any of them when no Event ran", () => {
    expect(fillRate(NOTHING)).toBeNull();
    expect(attendanceRate(NOTHING)).toBeNull();
    expect(noShowRate(NOTHING)).toBeNull();
    expect(waitlistConversion(NOTHING)).toBeNull();
    expect(manualOverrideShare(NOTHING)).toBeNull();
  });
});

describe("formatRate", () => {
  it("rounds to a whole percent", () => {
    expect(formatRate(109 / 142)).toBe("77%");
  });

  it("shows a dash where there is no answer, never 0%", () => {
    expect(formatRate(null)).toBe("—");
  });

  it("keeps the ends honest", () => {
    expect(formatRate(0)).toBe("0%");
    expect(formatRate(1)).toBe("100%");
  });
});

describe("formatCount", () => {
  it("groups thousands so a five-figure count is readable", () => {
    expect(formatCount(30000)).toBe("30,000");
    expect(formatCount(7)).toBe("7");
  });
});

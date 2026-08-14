import { describe, expect, it } from "vitest";
import {
  clubComparison,
  enrolledAgainstAttended,
  excludedNotices,
  monthLabel,
  percentTooltip,
  ratesOverTime,
  unmetDemandRows,
} from "./series";
import type { ClubTotals, EventTotals, MonthTotals } from "./types";

function month(overrides: Partial<MonthTotals> & { month: string }): MonthTotals {
  return { eventsRun: 1, capacity: 100, enrolled: 80, attended: 60, ...overrides };
}

function club(overrides: Partial<ClubTotals> & { clubId: string }): ClubTotals {
  return {
    clubName: overrides.clubId,
    eventsRun: 1,
    capacity: 100,
    enrolled: 80,
    attended: 60,
    unmetDemand: 0,
    ...overrides,
  };
}

function event(overrides: Partial<EventTotals> & { eventId: string }): EventTotals {
  return {
    title: overrides.eventId,
    clubId: "club-a",
    clubName: "Robotics Society",
    endsAt: "2026-03-10T20:00:00Z",
    capacity: 100,
    enrolled: 80,
    attended: 60,
    unmetDemand: 0,
    ...overrides,
  };
}

describe("monthLabel", () => {
  it("turns the bucket key into something a person reads", () => {
    expect(monthLabel("2026-03")).toBe("Mar 2026");
  });

  it("leaves anything that is not a bucket key alone rather than inventing a date", () => {
    expect(monthLabel("nonsense")).toBe("nonsense");
  });
});

describe("percentTooltip", () => {
  it("writes a whole percent", () => {
    expect(percentTooltip(78)).toBe("78%");
  });

  it("writes a dash for the gap where a month had nothing to divide by", () => {
    expect(percentTooltip(null)).toBe("—");
  });
});

describe("ratesOverTime", () => {
  it("gives one point per month for each of the two rates, oldest first", () => {
    const series = ratesOverTime([
      month({ month: "2026-03", capacity: 100, enrolled: 80, attended: 60 }),
      month({ month: "2026-04", capacity: 80, enrolled: 62, attended: 49 }),
    ]);

    expect(series.months).toEqual(["Mar 2026", "Apr 2026"]);
    expect(series.fillRate).toEqual([80, 78]);
    expect(series.attendanceRate).toEqual([75, 79]);
  });

  it("leaves a gap rather than a zero for a month with nothing to divide by", () => {
    // A gap is what a line chart should show where there is no answer; a zero would draw a crash that
    // never happened.
    const series = ratesOverTime([month({ month: "2026-05", capacity: 0, enrolled: 0, attended: 0 })]);

    expect(series.fillRate).toEqual([null]);
    expect(series.attendanceRate).toEqual([null]);
  });

  it("is empty when no month is", () => {
    expect(ratesOverTime([])).toEqual({ months: [], fillRate: [], attendanceRate: [] });
  });
});

describe("enrolledAgainstAttended", () => {
  it("pairs the two counts per Event, most recently finished first", () => {
    const series = enrolledAgainstAttended([
      event({ eventId: "e1", title: "Choir", enrolled: 12, attended: 9 }),
      event({ eventId: "e2", title: "Hack night", enrolled: 50, attended: 40 }),
    ]);

    expect(series.titles).toEqual(["Choir", "Hack night"]);
    expect(series.enrolled).toEqual([12, 50]);
    expect(series.attended).toEqual([9, 40]);
    expect(series.trimmed).toBe(0);
  });

  it("keeps the newest Events when there are more than a bar chart can hold, and says how many it dropped", () => {
    // The API deliberately does not cap the list — the time range is what bounds it. Choosing how many
    // bars fit on a screen is this function's job, and it reports the choice rather than hiding it.
    const events = Array.from({ length: 30 }, (_, index) => event({ eventId: `e${index}` }));

    const series = enrolledAgainstAttended(events, 12);

    expect(series.titles).toHaveLength(12);
    expect(series.titles[0]).toBe("e0");
    expect(series.trimmed).toBe(18);
  });
});

describe("clubComparison", () => {
  it("sorts by Events run, least first, because a horizontal bar chart draws upwards", () => {
    const comparison = clubComparison([
      club({ clubId: "club-a", clubName: "Robotics", eventsRun: 2 }),
      club({ clubId: "club-b", clubName: "Choir", eventsRun: 7 }),
      club({ clubId: "club-c", clubName: "Rowing", eventsRun: 4 }),
    ]);

    expect(comparison.clubNames).toEqual(["Robotics", "Rowing", "Choir"]);
    expect(comparison.eventsRun).toEqual([2, 4, 7]);
  });

  it("breaks a tie by name so the order never depends on what the database happened to return", () => {
    const comparison = clubComparison([
      club({ clubId: "club-b", clubName: "Rowing", eventsRun: 3 }),
      club({ clubId: "club-a", clubName: "Choir", eventsRun: 3 }),
    ]);

    expect(comparison.clubNames).toEqual(["Choir", "Rowing"]);
  });

  it("carries enrolled and attended alongside, so the same chart can show what filled", () => {
    const comparison = clubComparison([club({ clubId: "club-a", enrolled: 80, attended: 60 })]);

    expect(comparison.enrolled).toEqual([80]);
    expect(comparison.attended).toEqual([60]);
  });
});

describe("unmetDemandRows", () => {
  it("keeps only Events somebody was still queued for, worst first", () => {
    const rows = unmetDemandRows([
      event({ eventId: "e1", unmetDemand: 0 }),
      event({ eventId: "e2", unmetDemand: 9 }),
      event({ eventId: "e3", unmetDemand: 5 }),
    ]);

    expect(rows.map((row) => row.eventId)).toEqual(["e2", "e3"]);
  });

  it("breaks a tie by the more recent Event, not by whatever order it arrived in", () => {
    const rows = unmetDemandRows([
      event({ eventId: "older", unmetDemand: 4, endsAt: "2026-01-01T20:00:00Z" }),
      event({ eventId: "newer", unmetDemand: 4, endsAt: "2026-05-01T20:00:00Z" }),
    ]);

    expect(rows.map((row) => row.eventId)).toEqual(["newer", "older"]);
  });

  it("is empty when every Event had a Seat for everyone who wanted one", () => {
    expect(unmetDemandRows([event({ eventId: "e1" })])).toEqual([]);
  });
});

describe("excludedNotices", () => {
  it("says what is missing, one sentence per reason", () => {
    expect(excludedNotices({ draft: 2, cancelled: 3, inProgress: 1 })).toEqual([
      "2 Draft Events not shown",
      "3 Cancelled Events not shown",
      "1 Event still running not shown",
    ]);
  });

  it("counts of one read as one", () => {
    expect(excludedNotices({ draft: 1, cancelled: 1, inProgress: 2 })).toEqual([
      "1 Draft Event not shown",
      "1 Cancelled Event not shown",
      "2 Events still running not shown",
    ]);
  });

  it("says nothing when nothing was left out", () => {
    expect(excludedNotices({ draft: 0, cancelled: 0, inProgress: 0 })).toEqual([]);
  });
});

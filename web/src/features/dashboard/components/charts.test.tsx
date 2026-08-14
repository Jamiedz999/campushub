import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import { ClubComparisonChart } from "./ClubComparisonChart";
import { EnrolledAgainstAttendedChart } from "./EnrolledAgainstAttendedChart";
import { RatesOverTimeChart } from "./RatesOverTimeChart";
import { UnmetDemandTable } from "./UnmetDemandTable";
import { WaitlistConversionFigure } from "./WaitlistConversionFigure";
import type { ClubTotals, EventTotals, MetricTotals, MonthTotals } from "../types";

// Smoke tests, per the ADR's split: each chart renders, with the number of series it should have and
// the accessible label a screen reader gets instead of the canvas. The arithmetic behind the series is
// covered exhaustively in metrics.test.ts and series.test.ts, which is where it lives.

const MONTHS: MonthTotals[] = [
  { month: "2026-03", eventsRun: 1, capacity: 100, enrolled: 80, attended: 60 },
  { month: "2026-04", eventsRun: 2, capacity: 80, enrolled: 62, attended: 49 },
];

const CLUBS: ClubTotals[] = [
  {
    clubId: "club-a",
    clubName: "Robotics Society",
    eventsRun: 2,
    capacity: 150,
    enrolled: 130,
    attended: 100,
    unmetDemand: 14,
  },
  {
    clubId: "club-b",
    clubName: "Choir",
    eventsRun: 1,
    capacity: 30,
    enrolled: 12,
    attended: 9,
    unmetDemand: 0,
  },
];

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

const TOTALS: MetricTotals = {
  eventsRun: 3,
  capacity: 180,
  enrolled: 142,
  attended: 109,
  promoted: 10,
  everQueued: 22,
  unmetDemand: 11,
  manualAttendance: 15,
};

describe("RatesOverTimeChart", () => {
  it("draws one line per rate, under a label a screen reader can read", () => {
    render(<RatesOverTimeChart months={MONTHS} />);

    const chart = screen.getByTestId("echart");
    expect(chart).toHaveAttribute("data-series-count", "2");
    expect(chart).toHaveAttribute("data-series", "Fill rate,Attendance rate");
    expect(screen.getByRole("img", { name: "Fill rate and attendance rate by month" })).toBeVisible();
  });

  it("still renders when no month has anything in it", () => {
    render(<RatesOverTimeChart months={[]} />);

    expect(screen.getByTestId("echart")).toHaveAttribute("data-series-count", "2");
  });
});

describe("EnrolledAgainstAttendedChart", () => {
  it("draws the two bars per Event and labels them", () => {
    render(<EnrolledAgainstAttendedChart events={[event({ eventId: "e1" })]} />);

    const chart = screen.getByTestId("echart");
    expect(chart).toHaveAttribute("data-series-count", "2");
    expect(chart).toHaveAttribute("data-series", "Enrolled,Attended");
    expect(screen.getByRole("img", { name: "Enrolled against attended, per Event" })).toBeVisible();
  });

  it("says how many Events were left off rather than dropping them quietly", () => {
    const events = Array.from({ length: 26 }, (_, index) => event({ eventId: `e${index}` }));

    render(<EnrolledAgainstAttendedChart events={events} />);

    expect(screen.getByText(/6 more are in this range/)).toBeVisible();
  });

  it("says nothing about trimming when nothing was trimmed", () => {
    render(<EnrolledAgainstAttendedChart events={[event({ eventId: "e1" })]} />);

    expect(screen.queryByText(/more are in this range/)).not.toBeInTheDocument();
  });
});

describe("ClubComparisonChart", () => {
  it("draws Events run, enrolled and attended per Club, under its own label", () => {
    render(<ClubComparisonChart clubs={CLUBS} />);

    const chart = screen.getByTestId("echart");
    expect(chart).toHaveAttribute("data-series-count", "3");
    expect(chart).toHaveAttribute("data-series", "Events run,Enrolled,Attended");
    expect(screen.getByRole("img", { name: "Events run, enrolled and attended by Club" })).toBeVisible();
  });
});

describe("WaitlistConversionFigure", () => {
  it("shows the figure with both of its components, and no chart at all", () => {
    render(<WaitlistConversionFigure totals={TOTALS} />);

    expect(screen.getByRole("region", { name: "Waitlist conversion" })).toBeVisible();
    expect(screen.getByText("45%")).toBeVisible();
    expect(screen.getByText(/10 promoted of 22 who ever queued/)).toBeVisible();
    expect(screen.queryByTestId("echart")).not.toBeInTheDocument();
  });

  it("names the Students who left the queue, because they are why the denominator is what it is", () => {
    render(<WaitlistConversionFigure totals={TOTALS} />);

    expect(screen.getByText(/1 left the Waitlist before the Event/)).toBeVisible();
  });

  it("says nothing about leavers when nobody left", () => {
    render(<WaitlistConversionFigure totals={{ ...TOTALS, everQueued: 21, unmetDemand: 11 }} />);

    expect(screen.queryByText(/left the Waitlist/)).not.toBeInTheDocument();
  });
});

describe("UnmetDemandTable", () => {
  it("lists the over-subscribed Events, worst first, each linking to its own page", () => {
    render(
      <MemoryRouter>
        <UnmetDemandTable
          events={[
            event({ eventId: "e1", title: "Choir", unmetDemand: 3 }),
            event({ eventId: "e2", title: "Hack night", unmetDemand: 9 }),
          ]}
        />
      </MemoryRouter>,
    );

    const rows = screen.getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("Hack night");
    expect(screen.getByRole("link", { name: "Hack night" })).toHaveAttribute("href", "/events/e2");
  });

  it("says so plainly when every Event had a Seat for everyone", () => {
    render(
      <MemoryRouter>
        <UnmetDemandTable events={[event({ eventId: "e1" })]} />
      </MemoryRouter>,
    );

    expect(screen.getByText(/No Event in this range ended with anyone still on its Waitlist/)).toBeVisible();
  });
});

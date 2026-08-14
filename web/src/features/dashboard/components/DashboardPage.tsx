import { useState } from "react";
import { Link } from "react-router";
import { useDashboard } from "../hooks/useDashboard";
import {
  attendanceRate,
  fillRate,
  formatCount,
  formatRate,
  manualOverrideShare,
  noShowRate,
} from "../metrics";
import { RANGE_OPTIONS, rangeStart, type RangeChoice } from "../range";
import { clubTotals, excludedNotices, monthTotals } from "../series";
import { ClubComparisonChart } from "./ClubComparisonChart";
import { EnrolledAgainstAttendedChart } from "./EnrolledAgainstAttendedChart";
import { EventMetricsTable } from "./EventMetricsTable";
import { RatesOverTimeChart } from "./RatesOverTimeChart";
import { UnmetDemandTable } from "./UnmetDemandTable";
import { WaitlistConversionFigure } from "./WaitlistConversionFigure";

/**
 * The attendance dashboard — a Club Officer's own Clubs, or a University Admin's whole campus. Which
 * one it is comes from the server, from the caller's grants; the only control here is the time range.
 *
 * Everything below counts one population and says so: finished Published Events, with the Draft,
 * Cancelled and still-running Events of the same range shown as a count rather than left to be
 * inferred from a total that does not add up. See docs/adr/09-define-attendance-dashboard.md.
 */
export function DashboardPage() {
  const [range, setRange] = useState<RangeChoice>("12m");
  const dashboard = useDashboard({ from: rangeStart(range, new Date()) });

  if (dashboard.status === "pending") {
    return <p role="status">Loading the dashboard…</p>;
  }

  if (dashboard.status === "error") {
    return (
      <p role="alert">
        {dashboard.error.code === "NOT_FOUND"
          ? "There is no dashboard for this account — it is scoped to the Clubs you are an Officer of."
          : `Could not load the dashboard (${dashboard.error.code}).`}
      </p>
    );
  }

  const { scope, totals, clubMonths, events, excluded } = dashboard.data;
  const notices = excludedNotices(excluded);
  // Club activity arrives per Club per month, which is the grain the ADR defines it at. The trend line
  // wants it summed one way and the comparison the other; both sums are pure functions, so neither
  // chart can quietly disagree with the other about what a month or a Club contained.
  const months = monthTotals(clubMonths);
  const clubs = clubTotals(clubMonths);

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-6 p-6">
      <Link to="/events" className="text-sm text-slate-600">
        &larr; Back to events
      </Link>

      <header className="flex flex-wrap items-baseline justify-between gap-4">
        <h1 className="text-xl font-semibold">
          {scope === "ALL_CLUBS" ? "Attendance across every Club" : "Attendance for your Clubs"}
        </h1>
        <label className="text-sm">
          Time range{" "}
          <select
            className="rounded border px-2 py-1"
            value={range}
            onChange={(changed) => setRange(toRangeChoice(changed.target.value))}
          >
            {RANGE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </header>

      <p className="text-sm text-slate-600">
        Counted over {formatCount(totals.eventsRun)}{" "}
        {totals.eventsRun === 1 ? "finished Event" : "finished Events"} — Published, and already over.
      </p>

      {notices.length > 0 && (
        <p role="note" className="rounded border border-dashed p-3 text-sm text-slate-600">
          {notices.join(" · ")}
        </p>
      )}

      <section aria-label="Headline metrics" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Tile label="Fill rate" value={formatRate(fillRate(totals))} note={`${formatCount(totals.enrolled)} of ${formatCount(totals.capacity)} Seats`} />
        <Tile
          label="Attendance rate"
          value={formatRate(attendanceRate(totals))}
          note={`${formatCount(totals.attended)} of ${formatCount(totals.enrolled)} enrolled`}
        />
        <Tile label="No-show rate" value={formatRate(noShowRate(totals))} note="Of the Students who held a Seat" />
        <Tile
          label="Manual override share"
          value={formatRate(manualOverrideShare(totals))}
          note={`${formatCount(totals.manualAttendance)} of ${formatCount(totals.attended)} records`}
        />
      </section>

      <section aria-label="Rates over time" className="rounded border p-4">
        <RatesOverTimeChart months={months} />
      </section>

      <div className="grid gap-4 lg:grid-cols-2">
        <WaitlistConversionFigure totals={totals} />
        <section aria-label="Unmet demand" className="rounded border p-4">
          <h3 className="mb-2 text-sm font-medium text-slate-600">Unmet demand</h3>
          <UnmetDemandTable events={events} />
        </section>
      </div>

      <section aria-label="Enrolled against attended" className="rounded border p-4">
        <EnrolledAgainstAttendedChart events={events} />
      </section>

      <section aria-label="Every Event" className="rounded border p-4">
        <h3 className="mb-2 text-sm font-medium text-slate-600">Every Event in this range</h3>
        <EventMetricsTable events={events} />
      </section>

      {scope === "ALL_CLUBS" && (
        <section aria-label="Club comparison" className="rounded border p-4">
          <ClubComparisonChart clubs={clubs} />
        </section>
      )}
    </main>
  );
}

function Tile({ label, value, note }: { label: string; value: string; note: string }) {
  return (
    <div className="rounded border p-4">
      <h3 className="text-sm font-medium text-slate-600">{label}</h3>
      <p className="text-3xl font-bold leading-tight">{value}</p>
      <p className="mt-1 text-xs text-slate-600">{note}</p>
    </div>
  );
}

// The select can only hold the values the control put in it; anything else means the DOM was tampered
// with, and falling back is better than trusting it into a query string.
function toRangeChoice(value: string): RangeChoice {
  const known = RANGE_OPTIONS.find((option) => option.value === value);
  return known === undefined ? "12m" : known.value;
}

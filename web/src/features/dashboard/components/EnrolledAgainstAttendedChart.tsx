import { enrolledAgainstAttended } from "../series";
import type { EventTotals } from "../types";
import { Chart } from "./Chart";

/**
 * Enrolled against attended, per Event — the ADR's grouped bar.
 *
 * Two bars side by side rather than one stacked, because the gap between them is the no-show count and
 * a stack would hide exactly that. When there are more Events than bars, the count that was left off is
 * printed beside the chart rather than dropped quietly.
 */
export function EnrolledAgainstAttendedChart({ events }: { events: EventTotals[] }) {
  const series = enrolledAgainstAttended(events);

  return (
    <div className="flex flex-col gap-1">
      <Chart
        label="Enrolled against attended, per Event"
        summary="Each pair is one finished Event: the Seats claimed, and the Students who came through the door."
        option={{
          tooltip: { trigger: "axis" },
          legend: { data: ["Enrolled", "Attended"] },
          grid: { left: 48, right: 24, top: 48, bottom: 90 },
          xAxis: { type: "category", data: series.titles, axisLabel: { rotate: 40, hideOverlap: true } },
          yAxis: { type: "value" },
          series: [
            { name: "Enrolled", type: "bar", data: series.enrolled },
            { name: "Attended", type: "bar", data: series.attended },
          ],
        }}
      />
      {series.trimmed > 0 && (
        <p className="text-sm text-slate-600">
          Showing the {series.titles.length} most recently finished Events; {series.trimmed} more are in
          this range and every one of them is in the table below.
        </p>
      )}
    </div>
  );
}

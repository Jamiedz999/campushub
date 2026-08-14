import { percentTooltip, ratesOverTime } from "../series";
import type { MonthTotals } from "../types";
import { Chart } from "./Chart";

/**
 * Fill rate and attendance rate, month by month — the ADR's line chart.
 *
 * The two lines are deliberately on one pair of axes: they share a scale, and the question the chart
 * answers is whether they move together. A month with nothing to divide by is a gap in the line rather
 * than a point at zero, because "no Event ran" is not "nobody turned up".
 */
export function RatesOverTimeChart({ months }: { months: MonthTotals[] }) {
  const series = ratesOverTime(months);

  return (
    <Chart
      label="Fill rate and attendance rate by month"
      summary="Fill rate is enrolled against capacity; attendance rate is attended against enrolled."
      option={{
        tooltip: { trigger: "axis", valueFormatter: percentTooltip },
        legend: { data: ["Fill rate", "Attendance rate"] },
        grid: { left: 48, right: 24, top: 48, bottom: 40 },
        xAxis: { type: "category", data: series.months },
        yAxis: { type: "value", max: 100, axisLabel: { formatter: "{value}%" } },
        series: [
          { name: "Fill rate", type: "line", data: series.fillRate, connectNulls: false },
          { name: "Attendance rate", type: "line", data: series.attendanceRate, connectNulls: false },
        ],
      }}
    />
  );
}

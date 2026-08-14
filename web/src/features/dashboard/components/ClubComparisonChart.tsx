import { clubComparison } from "../series";
import type { ClubTotals } from "../types";
import { Chart } from "./Chart";

/**
 * Which societies run the most, and how full those Events were — the ADR's horizontal sorted bar, and
 * the one thing a University Admin's view has that a Club Officer's does not.
 *
 * Horizontal because Club names are words and words fit along an axis; sorted because the question is
 * a ranking, and an unsorted ranking is a table with extra steps.
 */
export function ClubComparisonChart({ clubs }: { clubs: ClubTotals[] }) {
  const comparison = clubComparison(clubs);

  return (
    <Chart
      label="Events run, enrolled and attended by Club"
      summary="Every Club that finished at least one Event in this range, most active at the top."
      height={Math.max(240, comparison.clubNames.length * 44)}
      option={{
        tooltip: { trigger: "axis" },
        legend: { data: ["Events run", "Enrolled", "Attended"] },
        grid: { left: 140, right: 32, top: 48, bottom: 40 },
        xAxis: { type: "value" },
        yAxis: { type: "category", data: comparison.clubNames },
        series: [
          { name: "Events run", type: "bar", data: comparison.eventsRun },
          { name: "Enrolled", type: "bar", data: comparison.enrolled },
          { name: "Attended", type: "bar", data: comparison.attended },
        ],
      }}
    />
  );
}

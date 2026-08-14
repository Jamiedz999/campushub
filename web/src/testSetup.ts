import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { createElement } from "react";
import { afterEach, vi } from "vitest";

// ECharts draws onto a canvas, and jsdom has none, so a chart component rendered for real here would
// fail on the environment rather than on anything the test is about. The stand-in reports what the
// component asked ECharts to draw — how many series, and their names — which is exactly the smoke test
// the ADR's testing split calls for: the arithmetic is covered thoroughly in the pure functions, and
// the drawing lightly. See docs/adr/09-define-attendance-dashboard.md.
vi.mock("echarts-for-react/lib/core", () => ({
  default: ({ option }: { option: { series?: { name?: string }[] } }) => {
    const series = option.series ?? [];
    return createElement("div", {
      "data-testid": "echart",
      "data-series-count": String(series.length),
      "data-series": series.map((one) => one.name ?? "").join(","),
    });
  },
}));

// RTL's automatic cleanup detects vitest's global `afterEach`, which this project deliberately does
// not enable (test.globals is off; every file imports afterEach/describe/it explicitly). Without this,
// each render() in a multi-test file stays mounted into the next test, and only tests whose queries
// happen not to collide with earlier leftovers were ever passing by accident.
afterEach(() => {
  cleanup();
});

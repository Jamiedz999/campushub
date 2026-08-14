import { BarChart, LineChart } from "echarts/charts";
import {
  AriaComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from "echarts/components";
import * as echarts from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import type { EChartsOption } from "echarts";
import ReactEChartsCore from "echarts-for-react/lib/core";

// ECharts' default entry point registers every chart type it has, which is most of a megabyte for the
// four this dashboard draws. Registering only what is used keeps the lazily-loaded dashboard chunk to
// something a phone on campus wifi will actually fetch.
echarts.use([
  BarChart,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  AriaComponent,
  CanvasRenderer,
]);

/**
 * The one place a chart is rendered, so every chart on the dashboard is accessible the same way.
 *
 * A canvas is invisible to a screen reader, so the figure around it carries the label instead: `label`
 * says what the chart is of, and `summary` states in words the thing the picture is there to show.
 * ECharts' own aria support is switched on beneath, which gives keyboard users the series and their
 * values.
 */
export function Chart({
  label,
  summary,
  option,
  height = 320,
}: {
  label: string;
  summary: string;
  option: EChartsOption;
  height?: number;
}) {
  return (
    <figure className="m-0">
      <div role="img" aria-label={label}>
        <ReactEChartsCore
          echarts={echarts}
          option={{ aria: { enabled: true }, ...option }}
          style={{ height }}
          notMerge
        />
      </div>
      <figcaption className="mt-2 text-sm text-slate-600">{summary}</figcaption>
    </figure>
  );
}

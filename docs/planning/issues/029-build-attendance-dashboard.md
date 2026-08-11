# CH-029 · Build the attendance dashboard

Sprint: 5
Area: dashboard
Blocked by: 027
Decisions: [attendance dashboard](../../adr/09-define-attendance-dashboard.md)

## Change

- `dashboard` module: read-only MongoDB aggregation pipelines, one per metric, scoped by role. No pre-aggregated collection and no scheduled job.
- Metrics exactly as defined: fill rate, attendance rate against **enrolled**, no-show rate, waitlist conversion from `promotedCount`, unmet demand, manual override share, club activity.
- Two views: Club Officer scoped to their own Clubs, University Admin across all, with a time-range control and no drill-down beyond an Event's own page.
- ECharts: line for rates over time, grouped bar for enrolled against attended, horizontal sorted bar for club comparison, and the **unmet-demand table**.
- **All arithmetic lives in pure data-shaping functions**; chart components stay thin.

## Acceptance

- Every metric matches a hand-computed fixture, denominators included.
- No individual form answers appear in either view.
- Dashboard queries answer well under one second on seeded data; the measured figure goes in the ADR as the baseline a future experiment would compare against.

## Tests

Unit tests for every data-shaping function to the full coverage bar. Smoke tests for each chart asserting series count and accessible labels. Negative test: an officer cannot read another Club's metrics.

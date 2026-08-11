# CH-031 · Package the portfolio release

Sprint: 5
Area: release
Blocked by: 030
Decisions: [naming](../../adr/02-choose-public-project-name.md), [Core boundary and Sprints](../13-set-core-boundary-and-sprints.md)

## Change

**Requires environment inputs**: deployment host, public hostname, secrets, and whether MongoDB is a container or a managed free tier. These were deliberately left open and are supplied here.

- Deploy the image; verify the public URL end to end on a phone as well as a desktop.
- Seeded demo data that makes the product legible in thirty seconds: clubs, a full event with a queue, a past event with attendance, a venue with bookings.
- README: the positioning line, three screenshots, an architecture diagram, `docker compose up`, and a run-it-yourself section proven by someone who has not seen the repo.
- Link check across every ADR, planning document and Issue.
- Record the achieved rubric self-score against the prediction, and note any divergence honestly.

## Acceptance

All Core Acceptance bullets pass. A stranger clones the repository and runs it from the README alone.

## Tests

A CI link check. A smoke test against the deployed URL.

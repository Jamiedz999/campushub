# The achieved rubric score, against the prediction

Status: current
Prediction: [Core boundary and Sprints — predicted rubric score](../13-set-core-boundary-and-sprints.md#predicted-rubric-score)
Gate: [Core Acceptance](../13-set-core-boundary-and-sprints.md#core-acceptance)

## Purpose

The Sprint plan predicted a score before any code existed: **7.5 / 8 on the gate, 7 bonus points**. A
prediction nobody checks afterwards is decoration, so this document is the check — item by item, what
was predicted, what is actually true of the repository today, and where the two differ.

The scoring reference itself is the portfolio rubric (`Side-Project-要求与评分Rubric.md`), which lives
outside this repository. Only the items it scores are restated here.

The point of the exercise is the divergence, not the total. Two items diverged, one upward and one not
yet closed, and both are recorded below in the terms an interviewer would ask about rather than in the
terms that flatter the number.

## Core Acceptance, bullet by bullet

The release Issue's first acceptance line is "All Core Acceptance bullets pass", and that is a different
list from the rubric below — it is this project's own, from [the Sprint plan](../13-set-core-boundary-and-sprints.md#core-acceptance).
Checked here rather than assumed, because a bullet nobody reads out loud is a bullet that quietly stops
being true.

Measured on 2026-08-15, from a clean checkout of this branch.

| Bullet | Holds | How it was checked |
|---|---|---|
| `./server/mvnw verify` green, JaCoCo ≥ 90% line and branch | ✅ | Green. 98% line, 92% branch on the merged unit + integration exec |
| `npm --prefix web run check` green, Vitest ≥ 90% | ✅ | Green. 98.29% lines, 92.48% branches |
| Checkstyle, SpotBugs and ESLint fail the build, all in CI | ✅ | All three in `mvnw verify` / `npm run check`, both run by the `build` job |
| Testcontainers integration tests covering every REST endpoint | ✅ | Route-by-route mapping in [`HARDENING.md`](HARDENING.md#the-endpoint-coverage-sweep) |
| Concurrency tests over all four contended writes | ✅ | The four claims, and the guard-removal record proving each is load-bearing, in [`EVIDENCE.md`](EVIDENCE.md#the-concurrency-suite) |
| A negative authorization test for every row of the permission matrix | ✅ | Row-by-row mapping in [`EVIDENCE.md`](EVIDENCE.md) |
| Cypress in CI over the three journeys | ✅ | The `cypress-journeys` job, which runs the set **twice against the same stack** |
| Deployed at a public URL, reachable, with seeded demo data | ❌ | **The one open bullet.** No host chosen; see the divergence below |
| README with the positioning line, three screenshots, an architecture diagram, and a working `docker compose up` | ✅ | Delivered at [#16](https://github.com/Jamiedz999/campushub/issues/16); the compose path is re-run by CI on every build |
| Every ADR link in the repository resolves | ✅ | The `docs-link-check` job, with `--include-fragments`, over the README, `CONTEXT.md`, `AGENTS.md`, `CLAUDE.md` and all of `docs/` |

Nine of ten. The tenth is the deployment, and it is the same item the rubric's gate is missing.

## The gate

| Item | Predicted | Achieved | Evidence |
|---|---|---|---|
| Backend tests with JaCoCo ≥ 90% | ✅ | ✅ **98% line (2460/2508), 92% branch (588/639)** | `./mvnw verify` on 2026-08-15; it fails below 90% on both counters, against the merged unit + integration exec |
| Frontend tests, Vitest ≥ 90% | ✅ | ✅ **98.29% lines (749/762), 92.48% branches (615/665)** | `npm --prefix web run check` on 2026-08-15 |
| Integration tests over the REST API with Testcontainers | ✅ | ✅ | Every endpoint, mapped route by route in [`HARDENING.md`](HARDENING.md#the-endpoint-coverage-sweep) |
| Checkstyle + SpotBugs + ESLint, failing the build | ✅ | ✅ | All three run in CI; SpotBugs at `Max` effort |
| Swagger / OpenAPI via springdoc | ✅ | ✅ | `springdoc-openapi-starter-webmvc-ui`; `/swagger-ui/index.html` and `/v3/api-docs` answer on a running instance |
| Business logic beyond CRUD | ✅ | ✅ | Search, filter, sort and paging over Events ([ADR 16](../../adr/16-define-event-discovery.md)), and the Seat Ledger, which is the real answer |
| Docker image + GitHub Actions + deployed public URL | ✅ | ⏳ **partly** | Image and pipeline are done and green; **no public URL yet** — see the divergence below |
| Spring Data JPA (多表) | ½ — traded away | ½ — traded away | MongoDB instead, argued in [the map's guardrails](../map.md#mongodb-guardrails--the-anti-resume-driven-design-contract) |

**Achieved today: 6.5 / 8. On deployment: 7.5 / 8, as predicted.** The deployment item is the only
distance between the two, and nothing else has to happen for it to close.

## The bonus points

| Item | Predicted | Achieved |
|---|---|---|
| Docker + cloud ⭐ | +2 | ⏳ Docker done, cloud pending |
| CI/CD ⭐ | +2 | ✅ +2 |
| Linters and tests in CI | +1 | ✅ +1 |
| Dashboard | +1 | ✅ +1 |
| Cypress E2E in CI | +1 | ✅ +1 |
| Team / Map / Microservice / Kafka / Redis | 0, all deliberate | 0, all deliberate |

**Achieved today: 5 🔵. On deployment: 7 🔵, as predicted.**

## Where it diverged

**The deployment is outstanding, and the reason is not technical.** Every gate item that depends on the
code is closed; the one that depends on an account, a card and a hostname is not, because no host has
been chosen. This was foreseen — the plan split the milestones precisely so that [v0.1](../13-set-core-boundary-and-sprints.md#sprints)
made the repository CV-ready without a URL — but foreseeing it is not the same as having done it, and
the honest current score is 6.5 + 5 rather than 7.5 + 7. What exists in place of the deployment is
everything that does not need the host: the image, the profile-gated seed, a `demo` profile that seeds
that data while requiring every secret the way production does, and
[`scripts/smoke-test.sh`](../../../scripts/smoke-test.sh), which CI already
runs against the composed stack on every build and will run against the public URL the moment the
repository variable `PUBLIC_BASE_URL` is set.

**Coverage came out well above the gate, and that is not a win worth claiming.** The prediction said the
90% gate would be *held, not raised*, and the achieved 98% line coverage is a consequence of testing
behaviour rather than of chasing a number — the branch counter, which is the one that resists that kind
of accident, sits at 92% on both sides. The gate is still 90%: raising it to match today's figure would
make a future honest refactor look like a regression. [`HARDENING.md`](HARDENING.md#the-coverage-exclusions)
lists every exclusion that the figure is computed with, because a coverage number is only as honest as
its exclusion list.

**The JPA half-point behaved exactly as predicted**, which is the least interesting row here and the one
most worth stating plainly: it was traded away on purpose, BookInn covers JPA at the portfolio level,
and it is never to be described afterwards as an oversight.

**One item moved between Issues rather than changing value.** The README, screenshots and architecture
diagram were planned as part of the release, and were delivered at the end of Sprint 3 instead, so that
the repository was presentable long before a host existed. The score is unaffected; the ordering is the
whole point of the v0.1 / v1.0 split.

## What this document owes

It is a snapshot with one open row. **When the public URL exists, both totals and the two ⏳ rows are
updated here in the same change that adds the URL to the README** — not left to be inferred from a
green pipeline.

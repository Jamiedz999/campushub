# CH-020 · Scaffold the full-stack walking skeleton

Sprint: 1
Area: platform
Blocked by: none
Decisions: [technical baseline](../implementation/TECHNICAL-BASELINE.md)

## Change

Create a runnable repository that proves React, Spring Boot, MongoDB, Mongock, Docker and CI work together. **No business logic, no domain types, no authentication.** Only the files the baseline's repository shape lists.

- `server/` on Java 25 and Spring Boot 4.1 with the Maven Wrapper, Spring MVC, Bean Validation, Actuator, `spring-boot-starter-data-mongodb`, springdoc-openapi and Mongock. One `system` module exposing `GET /api/system`.
- One Mongock change unit, empty but wired, proving migrations run at startup.
- `web/` on Node 24, React 19.2, strict TypeScript, Vite 8.1, Tailwind, axios, TanStack Query, React Router. One page that calls `/api/system` and renders the result.
- `compose.yaml` with a single-node MongoDB. Multi-stage `Dockerfile` building the React assets into the Boot jar.
- Checkstyle, SpotBugs and JaCoCo bound to `verify`; ESLint and Vitest bound to `npm run check`.
- `.github/workflows/ci.yml` running every build contract from a clean checkout.

## Acceptance

- All build contracts in the baseline pass locally and in CI.
- `/swagger-ui` serves a generated document, guarded by a test that fails if generation stops.
- Coverage gates are configured and enforced from this Issue onward, not retrofitted later.
- `docker compose up --build --wait` serves the React page and `/api/system` from one origin.

## Tests

`SystemControllerIntegrationTest` against Testcontainers MongoDB; one Vitest test for the page; an OpenAPI generation test.

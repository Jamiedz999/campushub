import { defineConfig } from "cypress";

/**
 * The journeys run against the composed stack — `docker compose up --wait` — and nothing else. There
 * is no dev server here and no `/api` proxy: the app and its API come from one origin in production,
 * which is what makes the session cookie these journeys carry the same cookie a real browser gets.
 *
 * CI overrides the origin with CYPRESS_BASE_URL rather than editing this file.
 */
export default defineConfig({
  // Off, so the run does not open with a warning that everyone learns to scroll past. Nothing here
  // reads Cypress.env(): the only thing CI configures is the origin, and that arrives as CYPRESS_BASE_URL.
  allowCypressEnv: false,
  e2e: {
    baseUrl: "http://localhost:8080",
    specPattern: "cypress/e2e/**/*.cy.ts",
    supportFile: "cypress/support/e2e.ts",
    downloadsFolder: "cypress/downloads",
    // Deliberately zero, and it stays zero. A retry count turns a journey that fails one run in three
    // into a journey that passes, which is the opposite of what these are for — see Issue #18, whose
    // acceptance is that they run twice in a row without flaking. If one of them needs a retry, the
    // journey is wrong, not the count.
    retries: 0,
    // Screenshots on failure are enough to see what the door screen or the browse list actually showed.
    // Video costs minutes per CI run to record a passing journey nobody watches.
    video: false,
  },
});

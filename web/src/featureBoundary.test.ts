// @vitest-environment node
//
// Proves the boundary rule has teeth rather than assuming it, the same way
// server/src/test/java/com/campushub/DocumentOwnershipRulesTest.java proves
// its ArchUnit rules do: run the real eslint.config.js (import-x/no-restricted-paths,
// see FEATURES in eslint.config.js) against a real, on-disk violation and
// check it is actually reported. src/features/checkin/__boundaryFixture.ts
// imports from src/features/events/__boundaryFixture.ts — both fixtures exist
// only for this test and are excluded from the routine `npm run lint` gate.
import { ESLint } from "eslint";
import { describe, expect, it } from "vitest";

describe("the feature boundary rule (eslint.config.js)", () => {
  it("fails a real import from features/events into features/checkin", async () => {
    // ignore:false bypasses eslint.config.js's global `ignores`, which is what
    // keeps this deliberate violation out of the routine `npm run lint` run.
    const eslint = new ESLint({ ignore: false });

    const results = await eslint.lintFiles(["src/features/checkin/__boundaryFixture.ts"]);
    const messages = results.flatMap((result) => result.messages);
    const boundaryViolations = messages.filter((message) => message.ruleId === "import-x/no-restricted-paths");

    expect(boundaryViolations).not.toHaveLength(0);
    expect(boundaryViolations[0]?.message).toContain("restricted zone");
  });

  it("does not flag a feature file that only imports within its own feature", async () => {
    const eslint = new ESLint({ ignore: false });

    const results = await eslint.lintFiles(["src/features/events/__boundaryFixture.ts"]);
    const messages = results.flatMap((result) => result.messages);
    const boundaryViolations = messages.filter((message) => message.ruleId === "import-x/no-restricted-paths");

    expect(boundaryViolations).toHaveLength(0);
  });
});

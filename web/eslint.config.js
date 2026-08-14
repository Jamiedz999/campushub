import js from "@eslint/js";
import tseslint from "typescript-eslint";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import { reactRefresh } from "eslint-plugin-react-refresh";
import importX from "eslint-plugin-import-x";

// A feature may not import from another feature — ADR 17. Shared code moves
// into lib/ or components/, or the two are composed at the route level.
const FEATURES = ["events", "registration", "checkin", "dashboard", "venues"];

function featureBoundaryZones() {
  return FEATURES.map((feature) => ({
    target: `./src/features/${feature}`,
    from: FEATURES.filter((other) => other !== feature).map((other) => `./src/features/${other}`),
  }));
}

export default tseslint.config(
  {
    // __boundaryFixture.ts files exist only to prove the boundary rule below
    // has teeth (see src/featureBoundary.test.ts) and are excluded from the
    // routine gate so that a deliberate violation doesn't fail every build.
    ignores: ["dist/**", "coverage/**", "**/__boundaryFixture.ts"],
  },
  {
    files: ["**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      importX.flatConfigs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite(),
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
    },
    settings: {
      // The pure-JS node resolver rather than the TypeScript-flavoured one:
      // that one shells out to a native resolver binary, which is one
      // portability dependency more than this rule needs.
      "import-x/resolver": {
        node: {
          extensions: [".js", ".jsx", ".ts", ".tsx", ".json"],
        },
      },
    },
    rules: {
      "import-x/no-restricted-paths": [
        "error",
        {
          zones: featureBoundaryZones(),
        },
      ],
    },
  },
  {
    files: ["src/**/*.{ts,tsx}"],
    ignores: ["**/*.test.{ts,tsx}"],
    rules: {
      // No `as` assertion appears in application code — enforced, not assumed.
      "@typescript-eslint/consistent-type-assertions": ["error", { assertionStyle: "never" }],
    },
  },
  {
    files: ["vite.config.ts", "eslint.config.js"],
    languageOptions: {
      globals: globals.node,
    },
  },
);

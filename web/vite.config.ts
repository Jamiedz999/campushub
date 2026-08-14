import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { coverageConfigDefaults, defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      // The door screen's socket. Same origin in production, so it needs proxying only here, where the
      // dev server and the backend are two ports.
      "/ws": {
        target: "ws://localhost:8080",
        ws: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/testSetup.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html"],
      // Four exclusions, each named and justified here rather than only in the pull request that
      // added it. Nothing else is excluded: if the gate blocks, the tests are missing.
      exclude: [
        // Vitest's own defaults — config files, dist/, coverage/, type declarations and the test
        // files themselves. Kept rather than restated, because dropping them would count this very
        // file towards the gate.
        ...coverageConfigDefaults.exclude,
        // Thin entry point with no branching logic of its own.
        "src/main.tsx",
        // Test fixtures for the ESLint boundary rule (see featureBoundary.test.ts),
        // not application code — exercising them would prove nothing.
        "**/__boundaryFixture.ts",
        // A stand-in for the browser's WebSocket, used only by tests. Under the same
        // `__` marker as the fixture above: it ships in no bundle, and counting a
        // test double towards the gate would measure the tests testing themselves.
        "**/__doorSocketDouble.ts",
      ],
      thresholds: {
        lines: 90,
        branches: 90,
        functions: 90,
        statements: 90,
      },
    },
  },
});

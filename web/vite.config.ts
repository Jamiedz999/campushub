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
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/testSetup.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html"],
      exclude: [
        ...coverageConfigDefaults.exclude,
        // Thin entry point with no branching logic of its own.
        "src/main.tsx",
        // Test fixtures for the ESLint boundary rule (see featureBoundary.test.ts),
        // not application code — exercising them would prove nothing.
        "**/__boundaryFixture.ts",
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

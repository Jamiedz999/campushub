import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// RTL's automatic cleanup detects vitest's global `afterEach`, which this project deliberately does
// not enable (test.globals is off; every file imports afterEach/describe/it explicitly). Without this,
// each render() in a multi-test file stays mounted into the next test, and only tests whose queries
// happen not to collide with earlier leftovers were ever passing by accident.
afterEach(() => {
  cleanup();
});

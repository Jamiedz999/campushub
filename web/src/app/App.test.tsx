import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../lib/httpClient";
import { App } from "./App";

describe("App", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("wires the router and query client so the system status route renders", () => {
    vi.spyOn(httpClient, "get").mockReturnValue(new Promise(() => {}));

    render(<App />);

    expect(screen.getByRole("status")).toHaveTextContent(/loading/i);
  });
});

import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../lib/httpClient";
import { App } from "./App";

describe("App", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("wires the router and query client so a signed-in visitor reaches the system status route", async () => {
    vi.spyOn(httpClient, "get").mockImplementation((url: string) => {
      if (url === "/auth/me") {
        return Promise.resolve({
          data: {
            accountId: "account-1",
            email: "student@demo.campushub",
            displayName: "Demo Student",
            systemRole: "STUDENT",
            officerClubIds: [],
          },
        });
      }
      return new Promise(() => {});
    });

    render(<App />);

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent(/loading system status/i));
  });
});

import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { registerForEvent } from "./registerForEvent";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

describe("registerForEvent", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("posts to take a Seat and returns the fresh view", async () => {
    const view = { id: "event-1", enrolled: true };
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValue(response(view));

    await expect(registerForEvent("event-1", { teamName: "Circuit Breakers" })).resolves.toEqual(view);

    expect(postSpy).toHaveBeenCalledWith("/events/event-1/registration", {
      answers: { teamName: "Circuit Breakers" },
    });
  });
});

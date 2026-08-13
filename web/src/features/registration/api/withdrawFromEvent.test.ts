import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { withdrawFromEvent } from "./withdrawFromEvent";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

describe("withdrawFromEvent", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("deletes the registration sub-resource and returns the fresh view", async () => {
    const view = { id: "event-1", enrolled: false, waitlistPosition: null };
    const deleteSpy = vi.spyOn(httpClient, "delete").mockResolvedValue(response(view));

    await expect(withdrawFromEvent("event-1")).resolves.toEqual(view);

    expect(deleteSpy).toHaveBeenCalledWith("/events/event-1/registration");
  });
});

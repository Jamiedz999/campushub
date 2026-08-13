import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { raiseEventCapacity } from "./raiseEventCapacity";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

describe("raiseEventCapacity", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("patches only the new capacity and returns the fresh Officer view", async () => {
    const view = { id: "event-1", capacity: 44, promotedCount: 4 };
    const patchSpy = vi.spyOn(httpClient, "patch").mockResolvedValue(response(view));

    await expect(raiseEventCapacity("event-1", 44)).resolves.toEqual(view);

    expect(patchSpy).toHaveBeenCalledWith("/events/event-1", { capacity: 44 });
  });
});

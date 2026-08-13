import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { getOfficerEvent } from "./getOfficerEvent";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

describe("getOfficerEvent", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("gets the scoped Officer view used by the capacity form", async () => {
    const view = { id: "event-1", capacity: 40, waitlistCount: 4 };
    const getSpy = vi.spyOn(httpClient, "get").mockResolvedValue(response(view));

    await expect(getOfficerEvent("event-1")).resolves.toEqual(view);

    expect(getSpy).toHaveBeenCalledWith("/events/event-1");
  });
});

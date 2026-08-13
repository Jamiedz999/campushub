import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { getEventRegistration } from "./getEventRegistration";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

describe("getEventRegistration", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("gets the Student's view of one Event by id", async () => {
    const view = { id: "event-1", enrolled: false };
    const getSpy = vi.spyOn(httpClient, "get").mockResolvedValue(response(view));

    await expect(getEventRegistration("event-1")).resolves.toEqual(view);

    expect(getSpy).toHaveBeenCalledWith("/events/event-1/registration");
  });
});

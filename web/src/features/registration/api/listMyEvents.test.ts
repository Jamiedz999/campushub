import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { listMyEvents } from "./listMyEvents";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

describe("listMyEvents", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("sends page and size and returns the page response", async () => {
    const page = { items: [], page: 0, size: 20, total: 0 };
    const getSpy = vi.spyOn(httpClient, "get").mockResolvedValue(response(page));

    await expect(listMyEvents({ page: 0, size: 20 })).resolves.toEqual(page);

    expect(getSpy).toHaveBeenCalledWith("/events/mine", { params: { page: 0, size: 20 } });
  });
});

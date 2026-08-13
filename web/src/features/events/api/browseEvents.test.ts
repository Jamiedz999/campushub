import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { browseEvents } from "./browseEvents";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

describe("browseEvents", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("always sends page and size, omitting every unset filter", async () => {
    const getSpy = vi
      .spyOn(httpClient, "get")
      .mockResolvedValue(response({ items: [], page: 0, size: 20, total: 0 }));

    await browseEvents({ page: 0, size: 20 });

    expect(getSpy).toHaveBeenCalledWith("/events", { params: { page: 0, size: 20 } });
  });

  it("includes every filter that is set", async () => {
    const getSpy = vi
      .spyOn(httpClient, "get")
      .mockResolvedValue(response({ items: [], page: 1, size: 10, total: 0 }));

    await browseEvents({
      page: 1,
      size: 10,
      q: "robot",
      openForRegistration: true,
      hasFreeSeat: true,
      startsAtFrom: "2026-03-01T00:00:00Z",
      startsAtTo: "2026-04-01T00:00:00Z",
      sort: "STARTS_AT_DESC",
    });

    expect(getSpy).toHaveBeenCalledWith("/events", {
      params: {
        page: 1,
        size: 10,
        q: "robot",
        openForRegistration: true,
        hasFreeSeat: true,
        startsAtFrom: "2026-03-01T00:00:00Z",
        startsAtTo: "2026-04-01T00:00:00Z",
        sort: "STARTS_AT_DESC",
      },
    });
  });

  it("returns the response body", async () => {
    const page = { items: [], page: 0, size: 20, total: 0 };
    vi.spyOn(httpClient, "get").mockResolvedValue(response(page));

    await expect(browseEvents({ page: 0, size: 20 })).resolves.toEqual(page);
  });
});

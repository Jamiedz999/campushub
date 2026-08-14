import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { listOfficerRegistrationAnswers } from "./listOfficerRegistrationAnswers";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

describe("listOfficerRegistrationAnswers", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("gets the paged Officer answer report", async () => {
    const report = { eventId: "event-1", items: [], page: 1, size: 20, total: 22 };
    const getSpy = vi.spyOn(httpClient, "get").mockResolvedValue(response(report));

    await expect(listOfficerRegistrationAnswers("event-1", { page: 1, size: 20 })).resolves.toEqual(report);

    expect(getSpy).toHaveBeenCalledWith("/events/event-1/registration-answers", {
      params: { page: 1, size: 20 },
    });
  });
});

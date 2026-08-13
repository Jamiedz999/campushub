import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import { retryRegistrationAnswers } from "./retryRegistrationAnswers";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

describe("retryRegistrationAnswers", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("updates only the enrolled Student's answer sub-resource", async () => {
    const view = { id: "event-1", enrolled: true, answersSaved: true };
    const putSpy = vi.spyOn(httpClient, "put").mockResolvedValue(response(view));

    await expect(retryRegistrationAnswers("event-1", { teamName: "Fixed" })).resolves.toEqual(view);

    expect(putSpy).toHaveBeenCalledWith("/events/event-1/registration/answers", {
      answers: { teamName: "Fixed" },
    });
  });
});

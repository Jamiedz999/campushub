import { AxiosHeaders } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { httpClient } from "../../../lib/httpClient";
import type { RegistrationForm } from "../../../types/registrationForm";
import { updateRegistrationForm } from "./updateRegistrationForm";

function response<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

describe("updateRegistrationForm", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("replaces the ordered form definition", async () => {
    const form: RegistrationForm = {
      fields: [
        {
          type: "SHORT_TEXT",
          fieldId: "stable-1",
          label: "Team name",
          helpText: "",
          required: true,
          maxLength: 40,
        },
      ],
    };
    const view = { id: "event-1", registrationForm: form };
    const putSpy = vi.spyOn(httpClient, "put").mockResolvedValue(response(view));

    await expect(updateRegistrationForm("event-1", form)).resolves.toEqual(view);

    expect(putSpy).toHaveBeenCalledWith("/events/event-1/registration-form", { fields: form.fields });
  });
});

import { describe, expect, it } from "vitest";
import { describeRegistrationError } from "./describeRegistrationError";

describe("describeRegistrationError", () => {
  it("describes EVENT_FULL", () => {
    expect(describeRegistrationError("EVENT_FULL")).toBe("This Event is full.");
  });

  it("describes ALREADY_ENROLLED", () => {
    expect(describeRegistrationError("ALREADY_ENROLLED")).toBe("You are already registered for this Event.");
  });

  it("describes ALREADY_WAITLISTED", () => {
    expect(describeRegistrationError("ALREADY_WAITLISTED")).toBe(
      "You are already on the Waitlist for this Event.",
    );
  });

  it("describes REGISTRATION_NOT_OPEN", () => {
    expect(describeRegistrationError("REGISTRATION_NOT_OPEN")).toBe("Registration is not open yet.");
  });

  it("describes REGISTRATION_CLOSED", () => {
    expect(describeRegistrationError("REGISTRATION_CLOSED")).toBe("Registration has closed.");
  });

  it("describes EVENT_STARTED", () => {
    expect(describeRegistrationError("EVENT_STARTED")).toBe("This Event has already started.");
  });

  it("describes EVENT_CANCELLED", () => {
    expect(describeRegistrationError("EVENT_CANCELLED")).toBe("This Event was cancelled.");
  });

  it("describes NOT_FOUND", () => {
    expect(describeRegistrationError("NOT_FOUND")).toBe("This Event could not be found.");
  });

  it("falls back to a generic message for an unrecognised code", () => {
    expect(describeRegistrationError("SOMETHING_NEW")).toBe("Something went wrong. Please try again.");
  });
});

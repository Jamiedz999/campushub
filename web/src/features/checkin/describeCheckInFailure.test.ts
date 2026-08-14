import { describe, expect, it } from "vitest";
import { describeCheckInFailure } from "./describeCheckInFailure";

describe("describeCheckInFailure", () => {
  it("words an expired code as a normal retry rather than an error", () => {
    const failure = describeCheckInFailure("TOKEN_EXPIRED");

    expect(failure.headline).toBe("Code expired");
    expect(failure.message).toContain("changes every minute");
    expect(failure.action).toBe("RESCAN");
  });

  it("tells an unrecognised code apart from an expired one", () => {
    expect(describeCheckInFailure("TOKEN_INVALID").headline).toBe("Code not recognised");
    expect(describeCheckInFailure("TOKEN_INVALID").action).toBe("RESCAN");
  });

  it("is kind to a waitlisted student and points at a human", () => {
    const failure = describeCheckInFailure("NOT_ON_ROSTER");

    expect(failure.message).toContain("Speak to the organiser");
    expect(failure.action).toBe("NONE");
  });

  it("names the manual override when there is no signal, because there is no offline queue", () => {
    const failure = describeCheckInFailure("NETWORK_ERROR");

    expect(failure.message).toContain("ask the organiser to mark you present");
    expect(failure.action).toBe("RETRY");
  });

  it("treats a second scan as reassurance with nothing left to do", () => {
    expect(describeCheckInFailure("ALREADY_CHECKED_IN").action).toBe("NONE");
  });

  it("explains the window when check-in is closed", () => {
    expect(describeCheckInFailure("CHECK_IN_WINDOW_CLOSED").message).toContain("15 minutes before");
  });

  it("says so plainly when the event is not found", () => {
    expect(describeCheckInFailure("NOT_FOUND").headline).toBe("Event not found");
  });

  it("falls back to a retry for a code it has never seen", () => {
    const failure = describeCheckInFailure("SOMETHING_NEW");

    expect(failure.headline).toBe("Something went wrong");
    expect(failure.action).toBe("RETRY");
  });
});

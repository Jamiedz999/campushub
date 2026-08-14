import { describe, expect, it } from "vitest";
import { isAttendanceHint } from "./attendanceHint";

describe("isAttendanceHint", () => {
  it("recognises the hint the server sends", () => {
    expect(isAttendanceHint('{"type":"attendance-changed","eventId":"event-1"}')).toBe(true);
  });

  it("still recognises one carrying a field this build has never heard of", () => {
    // Forward compatibility in the only direction that matters: the frame is a trigger, so a newer
    // server adding to it must not stop an older screen re-reading.
    expect(isAttendanceHint('{"type":"attendance-changed","eventId":"event-1","sentAt":"now"}')).toBe(
      true,
    );
  });

  it.each([
    ['{"type":"something-else","eventId":"event-1"}', "another kind of message"],
    ["not json at all", "a frame that is not JSON"],
    ['"attendance-changed"', "a bare string that happens to say the right thing"],
    ["null", "null"],
    ["[]", "an array"],
    ['{"eventId":"event-1"}', "a message with no type"],
  ])("ignores %s (%s)", (frame) => {
    expect(isAttendanceHint(frame)).toBe(false);
  });

  it("ignores a frame that is not text at all", () => {
    expect(isAttendanceHint(new ArrayBuffer(8))).toBe(false);
  });
});

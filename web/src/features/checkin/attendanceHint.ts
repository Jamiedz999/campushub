/** Mirrors com.campushub.realtime.internal.AttendanceHint. */
const ATTENDANCE_CHANGED = "attendance-changed";

/**
 * Whether a frame is the hint this screen knows how to act on.
 *
 * <p>Nothing is read out of the frame beyond that. The hint is a <b>trigger, not data</b>: the screen
 * answers it by re-reading its snapshot over HTTP, so a message that arrived out of order, twice, or
 * carrying a field this build has never heard of still leads to exactly one correct outcome. That is
 * also why an unparseable frame is ignored rather than surfaced — there is no error for the Officer to
 * act on, and the next re-read is already on its way.
 */
export function isAttendanceHint(frame: unknown): boolean {
  if (typeof frame !== "string") {
    return false;
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(frame);
  } catch {
    return false;
  }
  return (
    typeof parsed === "object" &&
    parsed !== null &&
    "type" in parsed &&
    parsed.type === ATTENDANCE_CHANGED
  );
}

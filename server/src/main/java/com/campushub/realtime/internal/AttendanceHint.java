package com.campushub.realtime.internal;

/**
 * The whole message. Two fields: what changed, and where.
 *
 * <p>There is no count here, and adding one would be the mistake this whole design exists to avoid —
 * see {@link com.campushub.realtime.RealtimeModule}. {@code eventId} is not the client's authorization
 * to read anything; the scope was authorized at the handshake, and the snapshot is authorized again by
 * the HTTP read the hint provokes. It travels so a client can tell which of its scopes woke up.
 */
record AttendanceHint(String type, String eventId) {

    /** Part of the wire contract, mirrored in web/src/features/checkin/attendanceHint.ts. */
    static final String ATTENDANCE_CHANGED = "attendance-changed";

    static AttendanceHint attendanceChanged(String eventId) {
        return new AttendanceHint(ATTENDANCE_CHANGED, eventId);
    }
}

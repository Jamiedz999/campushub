package com.campushub.realtime.internal;

/**
 * The seam named in docs/planning/implementation/TECHNICAL-BASELINE.md: a real WebSocket fan-out in
 * production, a recording no-op in tests.
 *
 * <p>It exists because "did this write publish a hint, and exactly one, and carrying what?" is a
 * question worth asking of every attendance path, and asking it through a real socket would mean a
 * server, a client and a wait in every one of those tests. Behind this interface the answer is a list.
 */
interface AttendanceBroadcaster {

    void attendanceChanged(String eventId);
}

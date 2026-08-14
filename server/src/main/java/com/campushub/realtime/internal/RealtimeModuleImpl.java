package com.campushub.realtime.internal;

import com.campushub.realtime.RealtimeModule;
import org.springframework.stereotype.Component;

@Component
class RealtimeModuleImpl implements RealtimeModule {

    private final AttendanceBroadcaster broadcaster;

    RealtimeModuleImpl(AttendanceBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void publishAttendanceChanged(String eventId) {
        broadcaster.attendanceChanged(eventId);
    }
}

package com.campushub.event.internal;

import com.campushub.event.persistence.EventRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

// Append-only migration for the attendance array introduced with check-in. No index goes with it: the
// attendance write and the door's roster read are both by _id, so nothing here serves a query the
// primary key does not already serve. See docs/adr/07-define-qr-checkin-and-anti-fraud.md.
@ChangeUnit(id = "event-attendance-010", order = "010")
public class EventAttendanceChangeUnit {

    @Execution
    public void execution(EventRepository eventRepository) {
        eventRepository.initializeAttendance();
    }

    @RollbackExecution
    public void rollback() {}
}

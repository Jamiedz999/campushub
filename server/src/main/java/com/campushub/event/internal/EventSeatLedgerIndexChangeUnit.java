package com.campushub.event.internal;

import com.campushub.event.persistence.EventRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

// Always runs, in every profile: the index findEnrolled's "my events" query needs. A separate change
// unit from EventStructuralChangeUnit rather than an edit to it — Mongock change units are append-only
// history, not something later Issues rewrite. See docs/adr/04-define-registration-capacity-and-waitlist.md.
@ChangeUnit(id = "event-seat-ledger-index-005", order = "005")
public class EventSeatLedgerIndexChangeUnit {

    @Execution
    public void execution(EventRepository eventRepository) {
        eventRepository.ensureSeatLedgerIndexes();
    }

    @RollbackExecution
    public void rollback() {}
}

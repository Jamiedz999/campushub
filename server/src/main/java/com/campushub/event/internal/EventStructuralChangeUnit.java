package com.campushub.event.internal;

import com.campushub.event.persistence.EventRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

// Always runs, in every profile: the discovery indexes, including the text index over title and
// description. See docs/adr/16-define-event-discovery.md and the technical baseline — Mongock owns
// every index, and index creation is never inferred from annotations at runtime.
@ChangeUnit(id = "event-structural-004", order = "004")
public class EventStructuralChangeUnit {

    @Execution
    public void execution(EventRepository eventRepository) {
        eventRepository.ensureIndexes();
    }

    @RollbackExecution
    public void rollback() {}
}

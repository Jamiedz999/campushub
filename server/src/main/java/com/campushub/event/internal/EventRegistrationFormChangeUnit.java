package com.campushub.event.internal;

import com.campushub.event.persistence.EventRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/** Append-only migration for Event form defaults introduced with custom registration forms. */
@ChangeUnit(id = "event-registration-form-006", order = "006")
public class EventRegistrationFormChangeUnit {

    @Execution
    public void execution(EventRepository eventRepository) {
        eventRepository.initializeRegistrationForms();
    }

    @RollbackExecution
    public void rollback() {}
}

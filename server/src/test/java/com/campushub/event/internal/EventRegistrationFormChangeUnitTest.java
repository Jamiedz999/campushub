package com.campushub.event.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.campushub.event.persistence.EventRepository;
import org.junit.jupiter.api.Test;

class EventRegistrationFormChangeUnitTest {

    @Test
    void initializesTheFormFieldsOnEventsCreatedBeforeCustomForms() {
        EventRepository repository = mock(EventRepository.class);

        new EventRegistrationFormChangeUnit().execution(repository);

        verify(repository).initializeRegistrationForms();
    }
}

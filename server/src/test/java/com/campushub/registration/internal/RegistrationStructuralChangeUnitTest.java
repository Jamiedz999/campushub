package com.campushub.registration.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.campushub.registration.persistence.RegistrationRepository;
import org.junit.jupiter.api.Test;

class RegistrationStructuralChangeUnitTest {

    @Test
    void createsTheUniqueRegistrationIndexThroughItsOwningRepository() {
        RegistrationRepository repository = mock(RegistrationRepository.class);

        new RegistrationStructuralChangeUnit().execution(repository);

        verify(repository).ensureIndexes();
    }
}

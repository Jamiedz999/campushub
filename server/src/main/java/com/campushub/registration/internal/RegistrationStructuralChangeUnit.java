package com.campushub.registration.internal;

import com.campushub.registration.persistence.RegistrationRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/** Append-only Mongock history for the Registration correctness index. */
@ChangeUnit(id = "registration-structural-007", order = "007")
public class RegistrationStructuralChangeUnit {

    @Execution
    public void execution(RegistrationRepository registrationRepository) {
        registrationRepository.ensureIndexes();
    }

    @RollbackExecution
    public void rollback() {}
}

package com.campushub.club.internal;

import com.campushub.club.persistence.ClubOfficerGrantRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

// Always runs, in every profile.
@ChangeUnit(id = "club-structural-003", order = "003")
public class ClubStructuralChangeUnit {

    @Execution
    public void execution(ClubOfficerGrantRepository grantRepository) {
        grantRepository.ensureIndexes();
    }

    @RollbackExecution
    public void rollback() {}
}

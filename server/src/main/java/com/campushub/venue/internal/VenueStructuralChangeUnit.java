package com.campushub.venue.internal;

import com.campushub.venue.persistence.VenueRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "venue-structural-008", order = "008")
public class VenueStructuralChangeUnit {

    @Execution
    public void execution(VenueRepository venueRepository) {
        venueRepository.ensureIndexes();
    }

    @RollbackExecution
    public void rollback() {}
}

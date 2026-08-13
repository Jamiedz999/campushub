package com.campushub.system.internal;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

// Empty on purpose: proves the migration pipeline runs at startup. Later modules add their own change units.
// public is required here: Mongock finds @Execution/@RollbackExecution via Class#getMethods(), which only sees
// public members, so a package-private class or methods are silently invisible to it rather than an error.
@ChangeUnit(id = "system-baseline-001", order = "001")
public class InitialSystemChangeUnit {

    @Execution
    public void execution() {}

    @RollbackExecution
    public void rollback() {}
}

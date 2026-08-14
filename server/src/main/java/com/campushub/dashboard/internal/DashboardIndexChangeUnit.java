package com.campushub.dashboard.internal;

import com.campushub.dashboard.persistence.DashboardRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

// Indexes are owned by Mongock rather than inferred from annotations, so the dashboard's population
// match gets its own change unit like every other read path that needed one. The compound shape
// (status, endsAt, clubId) is exactly what the five pipelines filter by, in that order.
@ChangeUnit(id = "dashboard-index-011", order = "011")
public class DashboardIndexChangeUnit {

    @Execution
    public void execution(DashboardRepository dashboardRepository) {
        dashboardRepository.ensureIndexes();
    }

    @RollbackExecution
    public void rollback() {}
}

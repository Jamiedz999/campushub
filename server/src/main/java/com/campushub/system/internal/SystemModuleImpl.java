package com.campushub.system.internal;

import com.campushub.system.SystemModule;
import com.campushub.system.domain.SystemStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

@Component
class SystemModuleImpl implements SystemModule {

    private static final String STATUS_UP = "UP";

    private final Clock clock;
    private final BuildProperties buildProperties;

    SystemModuleImpl(Clock clock, ObjectProvider<BuildProperties> buildProperties) {
        this.clock = clock;
        this.buildProperties = buildProperties.getIfAvailable();
    }

    @Override
    public SystemStatus currentStatus() {
        String version = buildProperties != null ? buildProperties.getVersion() : null;
        Instant buildTime = buildProperties != null ? buildProperties.getInstant("time") : null;
        return new SystemStatus(STATUS_UP, version, buildTime, clock.instant());
    }
}

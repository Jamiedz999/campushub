package com.campushub.system.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.system.domain.SystemStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

class SystemModuleImplTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-13T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Test
    void reportsVersionAndBuildTimeWhenBuildPropertiesIsAvailable() {
        BuildProperties buildProperties = new BuildProperties(propertiesWith("1.2.3", "2026-08-01T00:00:00Z"));
        SystemModuleImpl module = new SystemModuleImpl(FIXED_CLOCK, alwaysAvailable(buildProperties));

        SystemStatus status = module.currentStatus();

        assertThat(status.status()).isEqualTo("UP");
        assertThat(status.version()).isEqualTo("1.2.3");
        assertThat(status.buildTime()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(status.serverTime()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void reportsNullVersionAndBuildTimeWhenBuildPropertiesIsUnavailable() {
        SystemModuleImpl module = new SystemModuleImpl(FIXED_CLOCK, unavailable());

        SystemStatus status = module.currentStatus();

        assertThat(status.status()).isEqualTo("UP");
        assertThat(status.version()).isNull();
        assertThat(status.buildTime()).isNull();
        assertThat(status.serverTime()).isEqualTo(FIXED_INSTANT);
    }

    private static java.util.Properties propertiesWith(String version, String time) {
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("version", version);
        properties.setProperty("time", time);
        return properties;
    }

    private static ObjectProvider<BuildProperties> alwaysAvailable(BuildProperties buildProperties) {
        return new ObjectProvider<>() {
            @Override
            public BuildProperties getObject() {
                return buildProperties;
            }

            @Override
            public BuildProperties getIfAvailable() {
                return buildProperties;
            }

            @Override
            public BuildProperties getIfUnique() {
                return buildProperties;
            }
        };
    }

    private static ObjectProvider<BuildProperties> unavailable() {
        return new ObjectProvider<>() {
            @Override
            public BuildProperties getObject() {
                throw new IllegalStateException("not available in this test");
            }

            @Override
            public BuildProperties getIfAvailable() {
                return null;
            }

            @Override
            public BuildProperties getIfUnique() {
                return null;
            }
        };
    }
}

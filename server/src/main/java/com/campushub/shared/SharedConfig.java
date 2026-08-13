package com.campushub.shared;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Every calendar value in CampusHub is derived from these two beans, never from Instant.now() or a hardcoded zone.
@Configuration
@EnableConfigurationProperties({CampusProperties.class, SecuritySecrets.class, CheckInSecrets.class})
public class SharedConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ZoneId campusZone(CampusProperties campusProperties) {
        return campusProperties.getZone();
    }
}

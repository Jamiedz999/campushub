package com.campushub.shared;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campushub")
public class CampusProperties {

    private ZoneId zone = ZoneId.of("Europe/Dublin");

    public ZoneId getZone() {
        return zone;
    }

    public void setZone(ZoneId zone) {
        this.zone = zone;
    }
}

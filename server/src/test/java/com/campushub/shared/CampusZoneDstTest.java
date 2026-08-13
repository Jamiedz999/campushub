package com.campushub.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

// IANA tzdata ships two readings of Europe/Dublin: "vanguard" models Irish Standard Time (UTC+1) as the
// standard offset and winter as a negative daylight offset; "rearguard" keeps the conventional UTC+0
// standard offset with a positive summer offset. OpenJDK's bundled tzdb uses rearguard, so this pins
// that actual behaviour rather than the vanguard reading docs/adr/15 originally assumed.
class CampusZoneDstTest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    private static final Instant JANUARY = Instant.parse("2026-01-15T12:00:00Z");
    private static final Instant JULY = Instant.parse("2026-07-15T12:00:00Z");

    @Test
    void javaTimeModelsUtcAsTheStandardOffsetYearRound() {
        ZoneRules rules = DUBLIN.getRules();

        assertThat(rules.getStandardOffset(JANUARY)).isEqualTo(ZoneOffset.UTC);
        assertThat(rules.getStandardOffset(JULY)).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void javaTimeModelsSummerAsAPositiveDaylightOffset() {
        ZoneRules rules = DUBLIN.getRules();

        assertThat(rules.getDaylightSavings(JANUARY)).isEqualTo(Duration.ZERO);
        assertThat(rules.getDaylightSavings(JULY)).isEqualTo(Duration.ofHours(1));
        assertThat(rules.isDaylightSavings(JANUARY)).isFalse();
        assertThat(rules.isDaylightSavings(JULY)).isTrue();
    }

    @Test
    void javaTimeConvertsWallClockTimesCorrectly() {
        assertThat(JANUARY.atZone(DUBLIN).getHour()).isEqualTo(12);
        assertThat(JULY.atZone(DUBLIN).getHour()).isEqualTo(13);
    }

    @Test
    void legacyCalendarApiAgreesWithJavaTimeOnThisJdksTzdata() {
        TimeZone legacy = TimeZone.getTimeZone(DUBLIN);

        assertThat(legacy.inDaylightTime(Date.from(JANUARY))).isFalse();
        assertThat(legacy.inDaylightTime(Date.from(JULY))).isTrue();
    }
}

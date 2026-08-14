package com.campushub.dashboard.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

// The time-range control's arithmetic, kept out of the pipelines so it can be tested without a
// database. See docs/adr/09-define-attendance-dashboard.md and docs/adr/15-define-http-api-and-time-contract.md.
class DashboardRangeTest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    private static final Instant NOW = Instant.parse("2026-08-14T10:15:00Z");

    @Test
    void anAbsentRangeIsTheTwelveWholeMonthsEndingNow() {
        DashboardRange range = DashboardRange.resolve(null, null, NOW, DUBLIN);

        assertThat(range.to()).isEqualTo(NOW);
        // Midnight Dublin on 1 September 2025 is 23:00 UTC on 31 August: the campus timezone is where
        // a month begins, not UTC.
        assertThat(range.from()).isEqualTo(Instant.parse("2025-08-31T23:00:00Z"));
    }

    @Test
    void theEndOfTheRangeNeverRunsPastNow() {
        DashboardRange range = DashboardRange.resolve(null, Instant.parse("2027-01-01T00:00:00Z"), NOW, DUBLIN);

        assertThat(range.to()).isEqualTo(NOW);
    }

    @Test
    void aRangeTheCallerGivesInFullIsUsedAsGiven() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T00:00:00Z");

        DashboardRange range = DashboardRange.resolve(from, to, NOW, DUBLIN);

        assertThat(range.from()).isEqualTo(from);
        assertThat(range.to()).isEqualTo(to);
    }

    @Test
    void aBackwardsRangeCollapsesToItsEndRatherThanMatchingEverything() {
        DashboardRange range = DashboardRange.resolve(
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), NOW, DUBLIN);

        assertThat(range.from()).isEqualTo(range.to());
    }

    @Test
    void theDefaultStartIsTakenFromTheClampedEndNotTheRequestedOne() {
        // A caller asking for everything up to 2027 gets the twelve months ending now, not the twelve
        // months ending in 2027 — otherwise the clamp above would silently widen the window.
        DashboardRange range = DashboardRange.resolve(null, Instant.parse("2027-01-01T00:00:00Z"), NOW, DUBLIN);

        assertThat(range.from()).isEqualTo(Instant.parse("2025-08-31T23:00:00Z"));
    }
}

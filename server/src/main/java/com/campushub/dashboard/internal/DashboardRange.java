package com.campushub.dashboard.internal;

import com.campushub.dashboard.DashboardModule;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

// The time-range control, resolved once per read and then handed to every pipeline, so that the five
// aggregations behind one dashboard can never disagree about which window they cover.
record DashboardRange(Instant from, Instant to) {

    /**
     * Turns whatever the caller sent into a window the pipelines can trust.
     *
     * <p>The end never runs past {@code now}: the population is Events that have finished, so a range
     * reaching into the future would widen nothing and would only make the default start wrong. The
     * default start is the first instant of the month {@code DEFAULT_RANGE_MONTHS - 1} months back,
     * taken in the campus timezone — the month buckets are calendar months there, so the window that
     * fills them has to begin where one does. A backwards range collapses to an empty one rather than
     * being reordered: a caller who sent it meant something, and matching everything instead is the
     * worse of the two guesses.
     */
    static DashboardRange resolve(Instant from, Instant to, Instant now, ZoneId zone) {
        Instant end = to == null || to.isAfter(now) ? now : to;
        Instant start = from == null ? defaultStart(end, zone) : from;
        return new DashboardRange(start.isAfter(end) ? end : start, end);
    }

    private static Instant defaultStart(Instant end, ZoneId zone) {
        ZonedDateTime endOfWindow = end.atZone(zone);
        return endOfWindow
                .minusMonths(DashboardModule.DEFAULT_RANGE_MONTHS - 1L)
                .truncatedTo(ChronoUnit.DAYS)
                .withDayOfMonth(1)
                .toInstant();
    }
}

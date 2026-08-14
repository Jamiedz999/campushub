package com.campushub.dashboard.web;

import com.campushub.dashboard.DashboardModule.ClubMonthTotals;
import com.campushub.dashboard.DashboardModule.DashboardView;
import com.campushub.dashboard.DashboardModule.EventTotals;
import com.campushub.dashboard.DashboardModule.ExcludedEvents;
import com.campushub.dashboard.DashboardModule.MetricTotals;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// The whole dashboard payload. Counts only: no rate is computed here, because the ADR puts every
// division in a pure frontend function that the coverage gate can actually hold — see
// docs/adr/09-define-attendance-dashboard.md, "How this survives the 90% frontend coverage gate".
//
// No Student id and no form answer appears anywhere below. That is not an oversight to be careful
// about later: the aggregations count array lengths and never project their contents, so there is no
// field here for one to leak through. See docs/adr/08-define-roles-and-resource-authorization.md.
record DashboardResponse(
        DashboardScope scope,
        Instant from,
        Instant to,
        Totals totals,
        List<ClubMonth> clubMonths,
        List<Event> events,
        Excluded excluded) {

    /** Which of the ADR's two views this payload is, so the client knows whether to compare Clubs. */
    enum DashboardScope {
        CLUB,
        ALL_CLUBS
    }

    record Totals(
            long eventsRun,
            long capacity,
            long enrolled,
            long attended,
            long promoted,
            long everQueued,
            long unmetDemand,
            long manualAttendance) {}

    /**
     * One Club's activity in one calendar month. The trend line sums these across Clubs and the
     * cross-club comparison sums them across months; both rollups are pure functions on the client.
     */
    record ClubMonth(
            String clubId,
            String clubName,
            String month,
            long eventsRun,
            long capacity,
            long enrolled,
            long attended,
            long unmetDemand) {}

    record Event(
            String eventId,
            String title,
            String clubId,
            String clubName,
            Instant endsAt,
            long capacity,
            long enrolled,
            long attended,
            long unmetDemand) {}

    record Excluded(long draft, long cancelled, long inProgress) {}

    /**
     * {@code clubNames} labels the Clubs in {@code view}; a Club whose name is missing falls back to its
     * id rather than to a blank, so a row never loses its identity because a lookup came up short.
     */
    static DashboardResponse from(DashboardView view, DashboardScope scope, Map<String, String> clubNames) {
        MetricTotals totals = view.totals();
        ExcludedEvents excluded = view.excluded();
        return new DashboardResponse(
                scope,
                view.from(),
                view.to(),
                new Totals(
                        totals.eventsRun(),
                        totals.capacity(),
                        totals.enrolled(),
                        totals.attended(),
                        totals.promoted(),
                        totals.everQueued(),
                        totals.unmetDemand(),
                        totals.manualAttendance()),
                view.clubMonths().stream()
                        .map(row -> clubMonth(row, clubNames.getOrDefault(row.clubId(), row.clubId())))
                        .toList(),
                view.events().stream()
                        .map(event -> event(event, clubNames.getOrDefault(event.clubId(), event.clubId())))
                        .toList(),
                new Excluded(excluded.draft(), excluded.cancelled(), excluded.inProgress()));
    }

    private static ClubMonth clubMonth(ClubMonthTotals row, String clubName) {
        return new ClubMonth(
                row.clubId(),
                clubName,
                row.month(),
                row.eventsRun(),
                row.capacity(),
                row.enrolled(),
                row.attended(),
                row.unmetDemand());
    }

    private static Event event(EventTotals event, String clubName) {
        return new Event(
                event.eventId(),
                event.title(),
                event.clubId(),
                clubName,
                event.endsAt(),
                event.capacity(),
                event.enrolled(),
                event.attended(),
                event.unmetDemand());
    }
}

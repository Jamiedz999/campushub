package com.campushub.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Set;

// dashboard owns no documents. It runs read-only aggregations across collections owned by other
// modules and returns counts — never their document types. See
// docs/planning/implementation/TECHNICAL-BASELINE.md's module table and
// docs/adr/09-define-attendance-dashboard.md.
//
// Two things about the shape below are deliberate.
//
// First, **every record here is a count, never a rate.** The ADR fixes each metric's denominator, and
// a denominator only means something beside its numerator; shipping a pre-divided double would hide
// which population it came from and would make the frontend's pure data-shaping functions — the ones
// the 90% coverage gate actually rests on — a formatting layer with nothing to test. Division happens
// once, on the client, over numbers that arrive with their denominators attached.
//
// Second, **the population is applied here and is not a caller-facing filter.** Every count below is
// over Events where status is Published and endsAt has passed. Draft, Cancelled and still-running
// Events are excluded from all of them, club activity included, and are reported separately as
// {@link ExcludedEvents} so that a total which omits rows says so.
public interface DashboardModule {

    /** How far back a dashboard read looks when the caller names no start. */
    int DEFAULT_RANGE_MONTHS = 12;

    /**
     * Every metric's numerator and denominator over the whole reported population, in one record.
     *
     * <p>{@code everQueued} and {@code promoted} are read from the two counters the Event document
     * carries rather than derived from the Waitlist, because a Student who joined the queue and then
     * left it is in neither array afterwards — the case that broke the original conversion arithmetic.
     * {@code unmetDemand} is the Waitlist's length at the end, which is a different number on purpose:
     * someone who left is a lost registrant, not unmet demand.
     */
    record MetricTotals(
            long eventsRun,
            long capacity,
            long enrolled,
            long attended,
            long promoted,
            long everQueued,
            long unmetDemand,
            long manualAttendance) {

        public static MetricTotals empty() {
            return new MetricTotals(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * One calendar month of club activity. {@code month} is {@code YYYY-MM} in the campus timezone,
     * bucketed by the Event's endsAt — the moment it entered the population.
     */
    record MonthTotals(String month, long eventsRun, long capacity, long enrolled, long attended) {}

    /** One Club's activity over the whole range, for the cross-club comparison. */
    record ClubTotals(
            String clubId, long eventsRun, long capacity, long enrolled, long attended, long unmetDemand) {}

    /** One finished Event. The grouped bar and the unmet-demand table are both built from these. */
    record EventTotals(
            String eventId,
            String title,
            String clubId,
            Instant endsAt,
            long capacity,
            long enrolled,
            long attended,
            long unmetDemand) {}

    /**
     * The Events inside the range that the population deliberately leaves out, counted rather than
     * silently dropped. A number that quietly omits rows is a number nobody can check.
     */
    record ExcludedEvents(long draft, long cancelled, long inProgress) {

        public static ExcludedEvents none() {
            return new ExcludedEvents(0, 0, 0);
        }

        public long total() {
            return draft + cancelled + inProgress;
        }
    }

    /** One dashboard read: the range it covers, the counts, and what the range left out. */
    record DashboardView(
            Instant from,
            Instant to,
            MetricTotals totals,
            List<MonthTotals> months,
            List<ClubTotals> clubs,
            List<EventTotals> events,
            ExcludedEvents excluded) {

        public DashboardView {
            months = List.copyOf(months);
            clubs = List.copyOf(clubs);
            events = List.copyOf(events);
        }
    }

    /**
     * Every count over the finished Published Events of {@code clubIds} only.
     *
     * <p>This is the Club Officer's view, and it is also how a University Admin narrows to one Club:
     * the caller decides the scope, the query is scoped by it, and nothing is loaded and then checked.
     * An empty set therefore returns empty counts rather than everything — see
     * docs/adr/08-define-roles-and-resource-authorization.md.
     */
    DashboardView findForClubs(Set<String> clubIds, Instant from, Instant to);

    /** The University Admin's cross-club view, unscoped by Club. */
    DashboardView findAcrossAllClubs(Instant from, Instant to);
}

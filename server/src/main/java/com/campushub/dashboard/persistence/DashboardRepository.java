package com.campushub.dashboard.persistence;

import com.campushub.dashboard.DashboardModule.ClubMonthTotals;
import com.campushub.dashboard.DashboardModule.EventTotals;
import com.campushub.dashboard.DashboardModule.ExcludedEvents;
import com.campushub.dashboard.DashboardModule.MetricTotals;
import com.campushub.dashboard.domain.ClubScope;
import com.campushub.dashboard.domain.ClubScope.NamedClubs;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

// Read-only aggregations over the Events collection. Nothing here writes, and nothing here touches the
// Event document type: the dashboard is the one module the technical baseline lets read across a peer's
// collection, and it pays for that by depending on field names rather than on a class it does not own.
//
// **The population is a private constant of this class, not a parameter.** Every metric is computed
// over Events where status is Published and endsAt falls inside the range — which itself never runs
// past now, so "finished" needs no separate clause. A caller cannot widen it, which is the point: the
// ADR fixed the denominators and then fixed the set they are computed over, and a filter would put
// that back in the caller's hands. Club scoping is the one thing a caller does choose, and it arrives
// as a {@link ClubScope} rather than as a nullable set.
@Component
public class DashboardRepository {

    private static final String EVENTS = "events";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String DRAFT = "DRAFT";
    private static final String CANCELLED = "CANCELLED";
    private static final String MANUAL = "MANUAL";

    private final MongoTemplate mongoTemplate;

    public DashboardRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Mongock-owned index for the population match, on the Events collection. It lives here rather than
     * beside the Event module's own indexes because this is the only query shape that needs it, and an
     * index nobody can point at the query it serves is an index nobody dares drop.
     */
    public void ensureIndexes() {
        mongoTemplate
                .getCollection(EVENTS)
                .createIndex(new Document("status", 1).append("endsAt", 1).append("clubId", 1));
    }

    /** Every numerator and denominator in one pass over the population. */
    public MetricTotals totals(ClubScope scope, Instant from, Instant to) {
        Document group = activityCounts(null)
                .append("promoted", sum(intOrZero("$promotedCount")))
                .append("everQueued", sum(intOrZero("$everQueuedCount")))
                .append("manualAttendance", sum(new Document("$size", manualAttendance())));

        Document result = first(List.of(match(population(scope, from, to)), new Document("$group", group)));
        return result == null
                ? MetricTotals.empty()
                : new MetricTotals(
                        count(result, "eventsRun"),
                        count(result, "capacity"),
                        count(result, "enrolled"),
                        count(result, "attended"),
                        count(result, "promoted"),
                        count(result, "everQueued"),
                        count(result, "unmetDemand"),
                        count(result, "manualAttendance"));
    }

    /**
     * Club activity at the granularity the ADR defines it — per Club, per calendar month. One row is
     * one Club's month; the trend line and the cross-club comparison are both rollups of these, done in
     * the frontend's pure functions rather than by asking the database the same question twice.
     *
     * <p>The bucket is the month endsAt falls in, taken in the campus timezone: an Event that ends at
     * 00:30 Dublin on 1 April belongs to April, and the timezone is where that becomes true. See
     * docs/adr/15-define-http-api-and-time-contract.md.
     */
    public List<ClubMonthTotals> clubMonthTotals(ClubScope scope, Instant from, Instant to, ZoneId zone) {
        Document bucket = new Document("clubId", "$clubId")
                .append(
                        "month",
                        new Document(
                                "$dateToString",
                                new Document("format", "%Y-%m")
                                        .append("date", "$endsAt")
                                        .append("timezone", zone.getId())));

        return all(List.of(
                        match(population(scope, from, to)),
                        new Document("$group", activityCounts(bucket)),
                        new Document("$sort", new Document("_id.clubId", 1).append("_id.month", 1))))
                .stream()
                .map(document -> {
                    Document key = document.get("_id", Document.class);
                    return new ClubMonthTotals(
                            key.getString("clubId"),
                            key.getString("month"),
                            count(document, "eventsRun"),
                            count(document, "capacity"),
                            count(document, "enrolled"),
                            count(document, "attended"),
                            count(document, "unmetDemand"));
                })
                .toList();
    }

    /**
     * One row per finished Event, most recently finished first. Deliberately uncapped: the time range is
     * what bounds this, and a silent cap would be the same defect as a total that quietly omits rows.
     */
    public List<EventTotals> eventTotals(ClubScope scope, Instant from, Instant to) {
        Document project = new Document("title", 1)
                .append("clubId", 1)
                .append("endsAt", 1)
                .append("capacity", 1)
                .append("enrolled", sizeOf("$enrolled"))
                .append("attended", sizeOf("$attendance"))
                .append("unmetDemand", sizeOf("$waitlist"));

        return all(List.of(
                        match(population(scope, from, to)),
                        new Document("$project", project),
                        new Document("$sort", new Document("endsAt", -1))))
                .stream()
                .map(document -> new EventTotals(
                        identifierOf(document),
                        document.getString("title"),
                        document.getString("clubId"),
                        document.getDate("endsAt").toInstant(),
                        count(document, "capacity"),
                        count(document, "enrolled"),
                        count(document, "attended"),
                        count(document, "unmetDemand")))
                .toList();
    }

    /**
     * What the range covers but the population leaves out, grouped by the Status that got them left
     * out. Draft and Cancelled Events are counted where they would have ended. A Published Event is in
     * progress when it had started by the range's end — which is never later than now — and had not
     * finished; no lower bound on startsAt, because an Event that began before the window and is still
     * running is exactly the row that must not go missing from both the metrics and the count of what
     * is missing from them.
     */
    public ExcludedEvents excludedEvents(ClubScope scope, Instant from, Instant to) {
        Date start = Date.from(from);
        Date end = Date.from(to);
        Document notOffered = new Document("status", new Document("$in", List.of(DRAFT, CANCELLED)))
                .append("endsAt", between(start, end));
        Document stillRunning = new Document("status", PUBLISHED)
                .append("endsAt", new Document("$gt", end))
                .append("startsAt", new Document("$lte", end));

        Document match = clubScoped(new Document("$or", List.of(notOffered, stillRunning)), scope);
        Document group = new Document("_id", "$status").append("count", sum(1));

        long draft = 0;
        long cancelled = 0;
        long inProgress = 0;
        for (Document document : all(List.of(match(match), new Document("$group", group)))) {
            long count = count(document, "count");
            switch (document.getString("_id")) {
                case DRAFT -> draft = count;
                case CANCELLED -> cancelled = count;
                // The only Published Events this match admits are the running ones — the finished ones
                // are the population itself, and they are matched by the query above, not by this one.
                default -> inProgress = count;
            }
        }
        return new ExcludedEvents(draft, cancelled, inProgress);
    }

    /** The four counts every activity rollup shares, grouped by {@code bucket} ({@code null} for one row). */
    private static Document activityCounts(Object bucket) {
        return new Document("_id", bucket)
                .append("eventsRun", sum(1))
                .append("capacity", sum("$capacity"))
                .append("enrolled", sum(sizeOf("$enrolled")))
                .append("attended", sum(sizeOf("$attendance")))
                .append("unmetDemand", sum(sizeOf("$waitlist")));
    }

    private static Document population(ClubScope scope, Instant from, Instant to) {
        return clubScoped(
                new Document("status", PUBLISHED).append("endsAt", between(Date.from(from), Date.from(to))),
                scope);
    }

    private static Document clubScoped(Document match, ClubScope scope) {
        return scope instanceof NamedClubs named
                ? match.append("clubId", new Document("$in", List.copyOf(named.clubIds())))
                : match;
    }

    private static Document between(Date from, Date to) {
        return new Document("$gte", from).append("$lte", to);
    }

    private static Document match(Document criteria) {
        return new Document("$match", criteria);
    }

    private static Document sum(Object expression) {
        return new Document("$sum", expression);
    }

    /** The array's length, treating a missing array as an empty one — legacy documents predate all three. */
    private static Document sizeOf(String arrayField) {
        return new Document("$size", arrayOrEmpty(arrayField));
    }

    private static Document arrayOrEmpty(String arrayField) {
        return new Document("$ifNull", List.of(arrayField, List.of()));
    }

    private static Document intOrZero(String field) {
        return new Document("$ifNull", List.of(field, 0));
    }

    private static Document manualAttendance() {
        return new Document(
                "$filter",
                new Document("input", arrayOrEmpty("$attendance"))
                        .append("cond", new Document("$eq", List.of("$$this.method", MANUAL))));
    }

    /**
     * The hexadecimal string form every identifier takes across the API — see
     * docs/adr/15-define-http-api-and-time-contract.md. Read through the raw driver, {@code _id} is the
     * BSON ObjectId itself rather than the String the mapping layer would have handed back.
     */
    private static String identifierOf(Document document) {
        Object id = document.get("_id");
        return id instanceof ObjectId objectId ? objectId.toHexString() : String.valueOf(id);
    }

    /** Aggregation sums come back as whatever BSON numeric type fits; the caller only ever wants a count. */
    private static long count(Document document, String field) {
        Object value = document.get(field);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Document first(List<Document> pipeline) {
        return mongoTemplate.getCollection(EVENTS).aggregate(pipeline).first();
    }

    private List<Document> all(List<Document> pipeline) {
        List<Document> results = new ArrayList<>();
        mongoTemplate.getCollection(EVENTS).aggregate(pipeline).into(results);
        return results;
    }
}

package com.campushub.event.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.event.domain.Event;
import com.campushub.event.domain.EventBrowseQuery;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.EventSort;
import com.campushub.event.domain.EventStatus;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// Real MongoDB (Testcontainers): discovery's single $match, its text search and its total-consistency
// guarantee only mean anything proven against a real query planner. See
// docs/adr/16-define-event-discovery.md.
@Testcontainers
class EventRepositoryBrowseIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private static final Instant OPENS = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant CLOSES = Instant.parse("2026-03-10T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-03-05T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-03-20T02:00:00Z");

    private MongoTemplate mongoTemplate;
    private EventRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO_DB.getConnectionString()), "event-browse-test-" + UUID.randomUUID());
        repository = new EventRepository(mongoTemplate);
        repository.ensureIndexes();
    }

    @Test
    void draftAndCancelledEventsNeverAppearInDiscovery() {
        repository.insertDraft(new Event("club-a", "Draft-ish", "stays a draft", OPENS, CLOSES, OPENS, ENDS, 5));
        String cancelledId = publish("club-a", "Cancelled talk", "was cancelled", OPENS, 5, 0);
        mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(cancelledId)),
                new Update().set("status", EventStatus.CANCELLED),
                Event.class);
        publish("club-a", "Visible talk", "is published", OPENS, 5, 0);

        EventPage page = repository.browse(defaultQuery(), EventSort.STARTS_AT_ASC, NOW);

        assertThat(page.items()).extracting(Event::getTitle).containsExactly("Visible talk");
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void filtersByClubId() {
        publish("club-a", "A talk", "description", OPENS, 5, 0);
        publish("club-b", "B talk", "description", OPENS, 5, 0);

        EventPage page = repository.browse(queryWithClub("club-a"), EventSort.STARTS_AT_ASC, NOW);

        assertThat(page.items()).extracting(Event::getTitle).containsExactly("A talk");
    }

    @Test
    void filtersByOpenForRegistrationWindow() {
        String openId =
                repository.insertDraft(new Event("club-a", "Open now", "description", OPENS, CLOSES, ENDS, ENDS, 5));
        repository.publish(openId, Set.of("club-a"));
        Instant notYetOpen = NOW.plusSeconds(1_000_000);
        String notOpenYetId = repository.insertDraft(new Event(
                "club-a", "Not open yet", "description", notYetOpen, notYetOpen.plusSeconds(1_000), ENDS, ENDS, 5));
        repository.publish(notOpenYetId, Set.of("club-a"));

        EventPage page = repository.browse(queryOpenForRegistration(), EventSort.STARTS_AT_ASC, NOW);

        assertThat(page.items()).extracting(Event::getTitle).containsExactly("Open now");
    }

    @Test
    void filtersByStartsAtRange() {
        Instant early = Instant.parse("2026-04-01T00:00:00Z");
        Instant late = Instant.parse("2026-06-01T00:00:00Z");
        publishAt("club-a", "Early", "description", early);
        publishAt("club-a", "Late", "description", late);

        EventBrowseQuery query = new EventBrowseQuery(
                null,
                null,
                null,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"),
                null,
                null,
                0,
                20);

        EventPage page = repository.browse(query, EventSort.STARTS_AT_ASC, NOW);

        assertThat(page.items()).extracting(Event::getTitle).containsExactly("Late");
    }

    @Test
    void totalAgreesWithItemsWhenTheFreeSeatExprFilterIsActive() {
        publish("club-a", "Full", "description", OPENS, 2, 2);
        publish("club-a", "Has room", "description", OPENS, 2, 1);
        publish("club-a", "Also has room", "description", OPENS, 2, 0);

        EventBrowseQuery query = new EventBrowseQuery(null, null, null, null, null, true, null, 0, 1);
        EventPage firstPage = repository.browse(query, EventSort.STARTS_AT_ASC, NOW);
        EventPage secondPage = repository.browse(
                new EventBrowseQuery(null, null, null, null, null, true, null, 1, 1), EventSort.STARTS_AT_ASC, NOW);

        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(secondPage.total()).isEqualTo(2);
        assertThat(firstPage.items()).hasSize(1);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(firstPage.items().get(0).getTitle())
                .isNotEqualTo(secondPage.items().get(0).getTitle());
    }

    @Test
    void startsAtAscendingIsTheDefaultOrdering() {
        Instant early = Instant.parse("2026-04-01T00:00:00Z");
        Instant late = Instant.parse("2026-06-01T00:00:00Z");
        publishAt("club-a", "Late", "description", late);
        publishAt("club-a", "Early", "description", early);

        EventPage page = repository.browse(defaultQuery(), EventSort.STARTS_AT_ASC, NOW);

        assertThat(page.items()).extracting(Event::getTitle).containsExactly("Early", "Late");
    }

    @Test
    void startsAtDescendingReversesTheOrder() {
        Instant early = Instant.parse("2026-04-01T00:00:00Z");
        Instant late = Instant.parse("2026-06-01T00:00:00Z");
        publishAt("club-a", "Late", "description", late);
        publishAt("club-a", "Early", "description", early);

        EventPage page = repository.browse(defaultQuery(), EventSort.STARTS_AT_DESC, NOW);

        assertThat(page.items()).extracting(Event::getTitle).containsExactly("Late", "Early");
    }

    @Test
    void createdAtDescendingShowsTheNewestFirst() {
        publish("club-a", "First created", "description", OPENS, 5, 0);
        publish("club-a", "Second created", "description", OPENS, 5, 0);

        EventPage page = repository.browse(defaultQuery(), EventSort.CREATED_AT_DESC, NOW);

        assertThat(page.items()).extracting(Event::getTitle).containsExactly("Second created", "First created");
    }

    @Test
    void searchMatchesTitleAndDescriptionAndUsesTheTextIndexRatherThanACollectionScan() {
        publish("club-a", "Robotics workshop", "build a small robot", OPENS, 5, 0);
        publish("club-a", "Chess night", "casual games", OPENS, 5, 0);

        EventBrowseQuery query = new EventBrowseQuery("robot", null, null, null, null, null, null, 0, 20);
        EventPage page = repository.browse(query, EventSort.RELEVANCE, NOW);

        assertThat(page.items()).extracting(Event::getTitle).containsExactly("Robotics workshop");

        Document explain = mongoTemplate
                .getCollection("events")
                .find(new Document("$text", new Document("$search", "robot")))
                .explain();
        String plan = explain.toJson();
        assertThat(plan).contains("TEXT");
        assertThat(plan).doesNotContain("COLLSCAN");
    }

    private EventBrowseQuery defaultQuery() {
        return new EventBrowseQuery(null, null, null, null, null, null, null, 0, 20);
    }

    private EventBrowseQuery queryWithClub(String clubId) {
        return new EventBrowseQuery(null, clubId, null, null, null, null, null, 0, 20);
    }

    private EventBrowseQuery queryOpenForRegistration() {
        return new EventBrowseQuery(null, null, true, null, null, null, null, 0, 20);
    }

    private String publish(
            String clubId, String title, String description, Instant startsAt, int capacity, int enrolledCount) {
        String id =
                repository.insertDraft(new Event(clubId, title, description, OPENS, CLOSES, startsAt, ENDS, capacity));
        repository.publish(id, Set.of(clubId));
        if (enrolledCount > 0) {
            List<String> students =
                    IntStream.range(0, enrolledCount).mapToObj(index -> "student-" + index).toList();
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("id").is(id)), new Update().set("enrolled", students), Event.class);
        }
        return id;
    }

    private String publishAt(String clubId, String title, String description, Instant startsAt) {
        return publish(clubId, title, description, startsAt, 5, 0);
    }
}

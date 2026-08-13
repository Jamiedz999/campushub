package com.campushub.event.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.event.domain.Event;
import com.campushub.event.persistence.EventRepository;
import com.mongodb.client.MongoClients;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class EventStructuralChangeUnitIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private MongoTemplate mongoTemplate;
    private EventStructuralChangeUnit changeUnit;

    @BeforeEach
    void setUp() {
        mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO_DB.getConnectionString()), "event-structural-test-" + UUID.randomUUID());
        changeUnit = new EventStructuralChangeUnit();
    }

    @Test
    void createsATextIndexOverTitleAndDescription() {
        changeUnit.execution(new EventRepository(mongoTemplate));

        List<String> indexNames =
                mongoTemplate.indexOps(Event.class).getIndexInfo().stream().map(IndexInfo::getName).toList();

        assertThat(indexNames).anyMatch(name -> name.contains("text"));
    }

    @Test
    void createsTheCompoundIndexesDiscoveryFiltersAndSortsBy() {
        changeUnit.execution(new EventRepository(mongoTemplate));

        List<String> indexNames =
                mongoTemplate.indexOps(Event.class).getIndexInfo().stream().map(IndexInfo::getName).toList();

        assertThat(indexNames).contains("status_1_startsAt_1", "status_1_clubId_1");
    }
}

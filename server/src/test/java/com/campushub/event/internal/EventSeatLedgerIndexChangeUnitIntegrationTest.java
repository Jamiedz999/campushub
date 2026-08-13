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
class EventSeatLedgerIndexChangeUnitIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private MongoTemplate mongoTemplate;
    private EventSeatLedgerIndexChangeUnit changeUnit;

    @BeforeEach
    void setUp() {
        mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO_DB.getConnectionString()), "seat-ledger-idx-" + UUID.randomUUID());
        changeUnit = new EventSeatLedgerIndexChangeUnit();
    }

    @Test
    void createsTheIndexFindEnrolledQueriesBy() {
        changeUnit.execution(new EventRepository(mongoTemplate));

        List<String> indexNames =
                mongoTemplate.indexOps(Event.class).getIndexInfo().stream().map(IndexInfo::getName).toList();

        assertThat(indexNames).contains("enrolled.studentId_1");
    }
}

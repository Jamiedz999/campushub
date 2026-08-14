package com.campushub.venue.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.venue.domain.VenueDay;
import com.campushub.venue.persistence.VenueRepository;
import com.mongodb.client.MongoClients;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class VenueStructuralChangeUnitIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    @Test
    void createsTheUniqueVenueAndDateIndex() {
        MongoTemplate mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO_DB.getConnectionString()), "venue-structural-test-" + UUID.randomUUID());

        new VenueStructuralChangeUnit().execution(new VenueRepository(mongoTemplate));

        IndexInfo venueDayIndex = mongoTemplate.indexOps(VenueDay.class).getIndexInfo().stream()
                .filter(index -> "venueId_1_date_1".equals(index.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(venueDayIndex.isUnique()).isTrue();
    }
}

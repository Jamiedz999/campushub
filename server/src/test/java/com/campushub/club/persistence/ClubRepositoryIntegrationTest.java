package com.campushub.club.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.club.domain.Club;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class ClubRepositoryIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private MongoTemplate mongoTemplate;
    private ClubRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = new MongoTemplate(MongoClients.create(MONGO_DB.getConnectionString()), "club-test");
        repository = new ClubRepository(mongoTemplate);
    }

    @Test
    void createReturnsAUsableId() {
        String id = repository.create("Chess Club");

        Club saved = mongoTemplate.findById(id, Club.class);
        assertThat(saved).isNotNull();
        assertThat(saved.getName()).isEqualTo("Chess Club");
    }
}

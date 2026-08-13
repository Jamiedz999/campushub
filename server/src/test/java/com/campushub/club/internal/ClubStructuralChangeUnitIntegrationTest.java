package com.campushub.club.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campushub.club.domain.ClubOfficerGrant;
import com.campushub.club.persistence.ClubOfficerGrantRepository;
import com.mongodb.client.MongoClients;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class ClubStructuralChangeUnitIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private MongoTemplate mongoTemplate;
    private ClubStructuralChangeUnit changeUnit;

    @BeforeEach
    void setUp() {
        mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO_DB.getConnectionString()), "club-structural-test-" + UUID.randomUUID());
        changeUnit = new ClubStructuralChangeUnit();
    }

    @Test
    void createsAUniqueIndexOnClubIdAndAccountId() {
        changeUnit.execution(new ClubOfficerGrantRepository(mongoTemplate));
        mongoTemplate.insert(new ClubOfficerGrant("club-a", "account-1"));

        assertThatThrownBy(() -> mongoTemplate.insert(new ClubOfficerGrant("club-a", "account-1")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void theSameAccountCanStillOfficerADifferentClub() {
        changeUnit.execution(new ClubOfficerGrantRepository(mongoTemplate));
        mongoTemplate.insert(new ClubOfficerGrant("club-a", "account-1"));

        assertThatCode(() -> mongoTemplate.insert(new ClubOfficerGrant("club-b", "account-1")))
                .doesNotThrowAnyException();
    }
}

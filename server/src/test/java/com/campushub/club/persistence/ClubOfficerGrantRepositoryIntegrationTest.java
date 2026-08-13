package com.campushub.club.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// Real MongoDB (Testcontainers), not mocks: the whole point of this repository is the guarded,
// idempotent findAndModify-style upsert on (clubId, accountId) — that behaviour only means anything
// proven against a real database.
@Testcontainers
class ClubOfficerGrantRepositoryIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private ClubOfficerGrantRepository repository;

    @BeforeEach
    void setUp() {
        MongoTemplate mongoTemplate =
                new MongoTemplate(MongoClients.create(MONGO_DB.getConnectionString()), "club-officer-grant-test");
        repository = new ClubOfficerGrantRepository(mongoTemplate);
    }

    @Test
    void grantingTwiceIsIdempotent() {
        repository.grant("club-a", "account-1");
        repository.grant("club-a", "account-1");

        assertThat(repository.officerAccountIdsOf("club-a")).containsExactly("account-1");
    }

    @Test
    void aStudentCanHoldGrantsInTwoClubs() {
        repository.grant("club-a", "account-1");
        repository.grant("club-b", "account-1");

        assertThat(repository.clubIdsOfficeredBy("account-1")).containsExactlyInAnyOrder("club-a", "club-b");
    }

    @Test
    void revokingRemovesOnlyThatGrant() {
        repository.grant("club-a", "account-1");
        repository.grant("club-a", "account-2");

        repository.revoke("club-a", "account-1");

        assertThat(repository.officerAccountIdsOf("club-a")).containsExactly("account-2");
    }

    @Test
    void revokingAGrantThatWasNeverHeldIsANoOp() {
        repository.revoke("club-a", "account-1");

        assertThat(repository.officerAccountIdsOf("club-a")).isEmpty();
    }

    @Test
    void officersOfOneClubDoNotLeakIntoAnother() {
        repository.grant("club-a", "account-1");
        repository.grant("club-b", "account-2");

        assertThat(repository.officerAccountIdsOf("club-a")).containsExactly("account-1");
        assertThat(repository.officerAccountIdsOf("club-b")).containsExactly("account-2");
    }
}

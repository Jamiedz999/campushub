package com.campushub.identityaccess.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.identityaccess.domain.Account;
import com.campushub.identityaccess.domain.SystemRole;
import com.mongodb.client.MongoClients;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class AccountRepositoryIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private AccountRepository repository;

    @BeforeEach
    void setUp() {
        MongoTemplate mongoTemplate =
                new MongoTemplate(MongoClients.create(MONGO_DB.getConnectionString()), "account-test");
        repository = new AccountRepository(mongoTemplate);
    }

    @Test
    void insertedAccountIsFoundByEmail() {
        repository.insert(new Account("student@demo.campushub", "hash", "Demo Student", SystemRole.STUDENT));

        Optional<Account> found = repository.findByEmail("student@demo.campushub");

        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Demo Student");
        assertThat(found.get().getSystemRole()).isEqualTo(SystemRole.STUDENT);
    }

    @Test
    void findByEmailIsAbsentForAnUnknownEmail() {
        assertThat(repository.findByEmail("nobody@demo.campushub")).isEmpty();
    }

    @Test
    void insertedAccountIsFoundById() {
        Account saved =
                repository.insert(new Account("officer@demo.campushub", "hash", "Demo Officer", SystemRole.STUDENT));

        Optional<Account> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("officer@demo.campushub");
    }

    @Test
    void findByIdIsAbsentForAnUnknownId() {
        assertThat(repository.findById("000000000000000000000000")).isEmpty();
    }

    @Test
    void insertIfAbsentInsertsWhenNoAccountHasThatEmail() {
        Account saved = repository.insertIfAbsent(
                new Account("admin@demo.campushub", "hash", "University Admin", SystemRole.UNIVERSITY_ADMIN));

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findByEmail("admin@demo.campushub")).isPresent();
    }

    @Test
    void insertIfAbsentLeavesTheExistingAccountUntouched() {
        Account original = repository.insert(
                new Account("admin@demo.campushub", "original-hash", "University Admin", SystemRole.UNIVERSITY_ADMIN));

        Account result = repository.insertIfAbsent(
                new Account("admin@demo.campushub", "different-hash", "Different Name", SystemRole.UNIVERSITY_ADMIN));

        assertThat(result.getId()).isEqualTo(original.getId());
        assertThat(result.getPasswordHash()).isEqualTo("original-hash");
    }
}

package com.campushub.identityaccess.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campushub.identityaccess.domain.Account;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.identityaccess.persistence.AccountRepository;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class IdentityAccessStructuralChangeUnitIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private static final PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private MongoTemplate mongoTemplate;
    private AccountRepository accountRepository;
    private IdentityAccessStructuralChangeUnit changeUnit;

    @BeforeEach
    void setUp() {
        mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO_DB.getConnectionString()), "identityaccess-structural-test");
        accountRepository = new AccountRepository(mongoTemplate);
        changeUnit = new IdentityAccessStructuralChangeUnit();
    }

    @Test
    void createsAWorkingUniversityAdminAccount() {
        changeUnit.execution(accountRepository, PASSWORD_ENCODER);

        Account admin = accountRepository.findByEmail("admin@demo.campushub").orElseThrow();
        assertThat(admin.getSystemRole()).isEqualTo(SystemRole.UNIVERSITY_ADMIN);
        assertThat(PASSWORD_ENCODER.matches("123456", admin.getPasswordHash())).isTrue();
    }

    @Test
    void runningTwiceDoesNotDuplicateTheAdminAccount() {
        changeUnit.execution(accountRepository, PASSWORD_ENCODER);
        changeUnit.execution(accountRepository, PASSWORD_ENCODER);

        long adminCount = mongoTemplate.findAll(Account.class).stream()
                .filter(account -> account.getEmail().equals("admin@demo.campushub"))
                .count();
        assertThat(adminCount).isEqualTo(1);
    }

    @Test
    void createsAUniqueIndexOnAccountEmail() {
        changeUnit.execution(accountRepository, PASSWORD_ENCODER);
        accountRepository.insert(new Account("duplicate@demo.campushub", "hash", "First", SystemRole.STUDENT));

        assertThatThrownBy(() -> accountRepository.insert(
                        new Account("duplicate@demo.campushub", "hash", "Second", SystemRole.STUDENT)))
                .isInstanceOf(DuplicateKeyException.class);
    }
}

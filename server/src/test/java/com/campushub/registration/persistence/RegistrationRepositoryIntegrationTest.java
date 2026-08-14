package com.campushub.registration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.campushub.registration.domain.Registration;
import com.mongodb.client.MongoClients;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class RegistrationRepositoryIntegrationTest {

    private static final Long FIRST_ENROLLMENT_VERSION = 1L;
    private static final Long SECOND_ENROLLMENT_VERSION = 2L;

    @Container
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8");

    private MongoTemplate mongoTemplate;
    private RegistrationRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO_DB.getConnectionString()), "registration-" + UUID.randomUUID());
        repository = new RegistrationRepository(mongoTemplate);
        repository.ensureIndexes();
    }

    @Test
    void answersRoundTripByEventAndStudentAndAnUpsertKeepsOneRegistration() {
        repository.upsertAnswers(
                "event-1",
                "student-1",
                FIRST_ENROLLMENT_VERSION,
                Map.of("topics", List.of("AI", "Robotics"), "teamSize", BigDecimal.valueOf(3)));
        repository.upsertAnswers(
                "event-1", "student-1", SECOND_ENROLLMENT_VERSION, Map.of("teamSize", BigDecimal.valueOf(4)));

        Registration registration = repository.find("event-1", "student-1").orElseThrow();

        assertThat((BigDecimal) registration.getAnswers().get("teamSize"))
                .isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(registration.getEnrollmentVersion()).isEqualTo(SECOND_ENROLLMENT_VERSION);
        assertThat(registration.getAnswers()).doesNotContainKey("topics");
        assertThat(mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(), Registration.class))
                .isEqualTo(1);
        assertThat(mongoTemplate.indexOps(Registration.class).getIndexInfo())
                .anySatisfy(index -> {
                    assertThat(index.isUnique()).isTrue();
                    assertThat(index.getIndexFields()).extracting(field -> field.getKey())
                            .containsExactly("eventId", "studentId");
                });
    }

    @Test
    void anEmptyAnswerMapStillCreatesARegistrationThatCanBeDistinguishedFromMissingAnswers() {
        repository.upsertAnswers("event-1", "student-1", FIRST_ENROLLMENT_VERSION, Map.of());

        assertThat(repository.find("event-1", "student-1")).isPresent();
        assertThat(repository.find("event-1", "student-2")).isEmpty();
    }

    @Test
    void aNewEnrollmentAtomicallyReplacesTheOldAnswersAndTheirAssociation() {
        repository.upsertAnswers(
                "event-1", "student-1", FIRST_ENROLLMENT_VERSION, Map.of("name", "Old answer"));

        repository.upsertAnswers(
                "event-1", "student-1", SECOND_ENROLLMENT_VERSION, Map.of("name", "New answer"));

        Registration registration = repository.find("event-1", "student-1").orElseThrow();
        assertThat(registration.getEnrollmentVersion()).isEqualTo(SECOND_ENROLLMENT_VERSION);
        assertThat(registration.getAnswers()).containsExactlyEntriesOf(Map.of("name", "New answer"));
    }

    @Test
    void anOlderEnrollmentCannotOverwriteAnswersAlreadySavedForANewerOne() {
        assertThat(repository.upsertAnswers(
                        "event-1",
                        "student-1",
                        SECOND_ENROLLMENT_VERSION,
                        Map.of("name", "New answer")))
                .isTrue();

        assertThat(repository.upsertAnswers(
                        "event-1",
                        "student-1",
                        FIRST_ENROLLMENT_VERSION,
                        Map.of("name", "Old retry")))
                .isFalse();

        Registration registration = repository.find("event-1", "student-1").orElseThrow();
        assertThat(registration.getEnrollmentVersion()).isEqualTo(SECOND_ENROLLMENT_VERSION);
        assertThat(registration.getAnswers()).containsExactlyEntriesOf(Map.of("name", "New answer"));
    }

    @Test
    void aLegacyEnrollmentIsTheOldestFenceAndCanNeverReplaceAnAssignedId() {
        assertThat(repository.upsertAnswers(
                        "event-1", "student-1", null, Map.of("name", "Legacy answer")))
                .isTrue();
        assertThat(repository.upsertAnswers(
                        "event-1",
                        "student-1",
                        FIRST_ENROLLMENT_VERSION,
                        Map.of("name", "Current answer")))
                .isTrue();

        assertThat(repository.upsertAnswers(
                        "event-1", "student-1", null, Map.of("name", "Late legacy retry")))
                .isFalse();

        Registration registration = repository.find("event-1", "student-1").orElseThrow();
        assertThat(registration.getEnrollmentVersion()).isEqualTo(FIRST_ENROLLMENT_VERSION);
        assertThat(registration.getAnswers()).containsExactlyEntriesOf(Map.of("name", "Current answer"));
    }
}

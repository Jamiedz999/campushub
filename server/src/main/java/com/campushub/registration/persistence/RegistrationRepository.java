package com.campushub.registration.persistence;

import com.campushub.registration.domain.Registration;
import com.mongodb.client.result.UpdateResult;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.types.Decimal128;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class RegistrationRepository {

    private final MongoTemplate mongoTemplate;

    public RegistrationRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Mongock-owned correctness index: at most one answer document per Event and Student. */
    public void ensureIndexes() {
        mongoTemplate.indexOps(Registration.class).createIndex(new Index()
                .on("eventId", Sort.Direction.ASC)
                .on("studentId", Sort.Direction.ASC)
                .unique());
    }

    /**
     * Saves answers only when this enrollment is not older than the Registration already stored.
     * The Event's atomic Seat Ledger write assigns the monotonically increasing version; a missing
     * legacy version is the oldest possible fence. The unique index turns a filtered-out upsert into
     * a safe rejection.
     */
    public boolean upsertAnswers(
            String eventId,
            String studentId,
            Long enrollmentVersion,
            Map<String, Object> answers) {
        Criteria enrollmentFence = enrollmentVersion == null
                ? Criteria.where("enrollmentVersion").is(null)
                : new Criteria()
                        .orOperator(
                                Criteria.where("enrollmentVersion").is(null),
                                Criteria.where("enrollmentVersion").lte(enrollmentVersion));
        Query query = Query.query(new Criteria()
                .andOperator(
                        Criteria.where("eventId").is(eventId),
                        Criteria.where("studentId").is(studentId),
                        enrollmentFence));
        Update update = new Update()
                .set("eventId", eventId)
                .set("studentId", studentId)
                .set("enrollmentVersion", enrollmentVersion)
                .set("answers", new java.util.LinkedHashMap<>(answers));
        try {
            return applied(mongoTemplate.upsert(query, update, Registration.class));
        } catch (DuplicateKeyException ignored) {
            // Another writer created the unique Event/Student row after this upsert checked. Retry
            // as an update: a newer enrollment may replace it; an older one still cannot match.
            return mongoTemplate.updateFirst(query, update, Registration.class).getMatchedCount() > 0;
        }
    }

    public Optional<Registration> find(String eventId, String studentId) {
        Query query = Query.query(Criteria.where("eventId").is(eventId).and("studentId").is(studentId));
        return Optional.ofNullable(mongoTemplate.findOne(query, Registration.class))
                .map(RegistrationRepository::withPortableNumbers);
    }

    public List<Registration> findByEventAndStudents(String eventId, List<String> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(
                Criteria.where("eventId").is(eventId).and("studentId").in(studentIds));
        return mongoTemplate.find(query, Registration.class).stream()
                .map(RegistrationRepository::withPortableNumbers)
                .toList();
    }

    /**
     * Values inside the heterogeneous answers map have no declared Java target type. The Mongo
     * driver therefore returns Decimal128 for number answers; convert that persistence detail back
     * to the API's ordinary BigDecimal value at this boundary.
     */
    private static Registration withPortableNumbers(Registration registration) {
        Map<String, Object> answers = new LinkedHashMap<>();
        registration.getAnswers().forEach((fieldId, answer) -> answers.put(fieldId, portableValue(answer)));
        return new Registration(
                registration.getEventId(),
                registration.getStudentId(),
                registration.getEnrollmentVersion(),
                answers);
    }

    private static Object portableValue(Object value) {
        if (value instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue();
        }
        if (value instanceof List<?> values) {
            return values.stream().map(RegistrationRepository::portableValue).toList();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value;
    }

    private static boolean applied(UpdateResult result) {
        return result.getMatchedCount() > 0 || result.getUpsertedId() != null;
    }
}

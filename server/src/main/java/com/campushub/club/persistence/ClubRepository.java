package com.campushub.club.persistence;

import com.campushub.club.domain.Club;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class ClubRepository {

    private final MongoTemplate mongoTemplate;

    public ClubRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Inserts a new Club and returns its generated id. */
    public String create(String name) {
        return mongoTemplate.insert(new Club(name)).getId();
    }

    /**
     * Names for the named Clubs, keyed by id; ids that match nothing are simply absent. Returns the
     * labels rather than the documents so that the Club document never leaves this package — the same
     * rule the module interface states, held one layer lower where it is actually enforceable.
     */
    public Map<String, String> namesOf(Set<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mongoTemplate.find(Query.query(Criteria.where("id").in(ids)), Club.class).stream()
                .collect(Collectors.toUnmodifiableMap(Club::getId, Club::getName));
    }
}

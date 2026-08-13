package com.campushub.club.persistence;

import com.campushub.club.domain.Club;
import org.springframework.data.mongodb.core.MongoTemplate;
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
}

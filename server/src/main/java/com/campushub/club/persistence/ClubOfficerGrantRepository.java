package com.campushub.club.persistence;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.campushub.club.domain.ClubOfficerGrant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class ClubOfficerGrantRepository {

    private final MongoTemplate mongoTemplate;

    public ClubOfficerGrantRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Mongock-owned: called only from ClubStructuralChangeUnit. The compound unique index is the hard
     * guarantee behind grant()'s idempotency, holding even for a write that bypassed this repository;
     * the accountId index serves clubIdsOfficeredBy's reverse lookup, run on every request that builds
     * a CurrentActor.
     */
    public void ensureIndexes() {
        mongoTemplate
                .indexOps(ClubOfficerGrant.class)
                .createIndex(new Index()
                        .on("clubId", Sort.Direction.ASC)
                        .on("accountId", Sort.Direction.ASC)
                        .unique());
        mongoTemplate.indexOps(ClubOfficerGrant.class).createIndex(new Index("accountId", Sort.Direction.ASC));
    }

    /** Idempotent: granting an already-held grant leaves exactly one document behind. */
    public void grant(String clubId, String accountId) {
        Update update = new Update().setOnInsert("clubId", clubId).setOnInsert("accountId", accountId);
        mongoTemplate.upsert(grantQuery(clubId, accountId), update, ClubOfficerGrant.class);
    }

    public void revoke(String clubId, String accountId) {
        mongoTemplate.remove(grantQuery(clubId, accountId), ClubOfficerGrant.class);
    }

    public List<String> officerAccountIdsOf(String clubId) {
        return mongoTemplate.find(query(where("clubId").is(clubId)), ClubOfficerGrant.class).stream()
                .map(ClubOfficerGrant::getAccountId)
                .toList();
    }

    public Set<String> clubIdsOfficeredBy(String accountId) {
        return mongoTemplate.find(query(where("accountId").is(accountId)), ClubOfficerGrant.class).stream()
                .map(ClubOfficerGrant::getClubId)
                .collect(Collectors.toSet());
    }

    private static Query grantQuery(String clubId, String accountId) {
        return query(where("clubId").is(clubId).and("accountId").is(accountId));
    }
}

package com.campushub.identityaccess.persistence;

import com.campushub.identityaccess.domain.Account;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class AccountRepository {

    private final MongoTemplate mongoTemplate;

    public AccountRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Mongock-owned: called only from IdentityAccessStructuralChangeUnit. */
    public void ensureEmailUniqueIndex() {
        mongoTemplate.indexOps(Account.class).createIndex(new Index("email", Sort.Direction.ASC).unique());
    }

    public Account insert(Account account) {
        return mongoTemplate.insert(account);
    }

    public Optional<Account> findByEmail(String email) {
        return Optional.ofNullable(
                mongoTemplate.findOne(Query.query(Criteria.where("email").is(email)), Account.class));
    }

    public Optional<Account> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, Account.class));
    }

    /**
     * Inserts {@code draft} unless an account with that email already exists, in which case the
     * existing account is returned untouched. Used only by startup seeding, which runs single-threaded,
     * so this check-then-insert does not need findAndModify's guarded-write treatment.
     */
    public Account insertIfAbsent(Account draft) {
        return findByEmail(draft.getEmail()).orElseGet(() -> insert(draft));
    }
}

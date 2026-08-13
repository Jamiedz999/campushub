package archrulesfixtures.mongotemplateviolation;

import org.springframework.data.mongodb.core.MongoTemplate;

// Fixture only: proves DocumentOwnershipRulesTest's MongoTemplate rule actually fails a real violation.
public class BadService {

    public MongoTemplate mongoTemplate;
}

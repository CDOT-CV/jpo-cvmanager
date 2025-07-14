package us.dot.its.jpo.ode.api.accessors.rsuState;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import java.util.Date;

import us.dot.its.jpo.ode.api.models.snmp.RsuState;

@Component
public class RsuStateRepositoryImpl implements RsuStateRepository {

    private final MongoTemplate mongoTemplate;
    private final String collectionName = "IntersectionApiRsuStatus";

    @Autowired
    public RsuStateRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void add(RsuState item) {
        mongoTemplate.insert(item, collectionName);
    }

    @Override
    public List<RsuState> findByRsuIPAndTimestampBetween(String rsuIP, long start, long end) {
        Criteria criteria = Criteria.where("rsuIP").is(rsuIP)
                .and("timestamp").gte(new Date(start)).lte(new Date(end));
        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.ASC, "timestamp"));
        return mongoTemplate.find(query, RsuState.class, collectionName);
    }

    @Override
    public List<RsuState> findByRsuIPOrderByTimestampDesc(String rsuIP) {
        Criteria criteria = Criteria.where("rsuIP").is(rsuIP);
        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "timestamp"));

        List<RsuState> results = mongoTemplate.find(query, RsuState.class, collectionName);
        return results;
    }
}
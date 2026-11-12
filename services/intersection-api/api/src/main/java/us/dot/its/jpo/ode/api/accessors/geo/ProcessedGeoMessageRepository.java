package us.dot.its.jpo.ode.api.accessors.geo;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

/** Executes the raw Mongo query for the CV Manager processed-message polygon endpoint. */
@Component
public class ProcessedGeoMessageRepository {

    private final MongoTemplate mongoTemplate;

    public ProcessedGeoMessageRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Stream<Document> find(
            String collectionName,
            List<List<Double>> polygon,
            String startInclusive,
            String endInclusive) {
        Document timeBounds = new Document("$gte", startInclusive)
                .append("$lte", endInclusive);
        Document geometry = new Document("$geoWithin", new Document("$geometry",
                new Document("type", "Polygon")
                        .append("coordinates", List.of(polygon))));
        Query query = new BasicQuery(new Document("properties.timeStamp", timeBounds)
                .append("geometry", geometry));

        // Match the Python endpoint's timestamp ascending + 2dsphere compound index.
        mongoTemplate.indexOps(collectionName).createIndex(new CompoundIndexDefinition(
                new Document("properties.timeStamp", 1).append("geometry", "2dsphere")));

        // Keep the natural cursor order. The Python endpoint chooses its unique-record
        // cap before sorting the retained result by the original timestamp string.
        return mongoTemplate.stream(query, Document.class, collectionName);
    }
}

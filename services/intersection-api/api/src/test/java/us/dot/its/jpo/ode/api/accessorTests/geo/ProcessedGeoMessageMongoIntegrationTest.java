package us.dot.its.jpo.ode.api.accessorTests.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.MongoDBContainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import us.dot.its.jpo.ode.api.accessors.geo.ProcessedGeoMessageRepository;
import us.dot.its.jpo.ode.api.models.geo.ProcessedGeoMessageRequest;
import us.dot.its.jpo.ode.api.services.ProcessedGeoMessageService;

/** Verifies the real Mongo query, index, raw document shape, and configured collections. */
@Testcontainers(disabledWithoutDocker = true)
class ProcessedGeoMessageMongoIntegrationTest {
    private static final String DATABASE = "geo_query_integration";
    private static final String BSM_COLLECTION = "configured_bsm";
    private static final String PSM_COLLECTION = "configured_psm";
    private static final List<List<Double>> POLYGON = List.of(
            List.of(-106.0, 39.0),
            List.of(-104.0, 39.0),
            List.of(-104.0, 41.0),
            List.of(-106.0, 41.0),
            List.of(-106.0, 39.0));

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    private static MongoClient mongoClient;
    private MongoDatabase database;
    private ProcessedGeoMessageService service;

    @BeforeEach
    void setUp() {
        if (mongoClient == null) {
            mongoClient = MongoClients.create(MONGO.getReplicaSetUrl());
        }
        database = mongoClient.getDatabase(DATABASE);
        database.drop();
        ProcessedGeoMessageRepository repository = new ProcessedGeoMessageRepository(
                new org.springframework.data.mongodb.core.MongoTemplate(mongoClient, DATABASE));
        service = new ProcessedGeoMessageService(repository, BSM_COLLECTION, PSM_COLLECTION, "100");
    }

    @AfterAll
    static void closeClient() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @Test
    void filtersPolygonAndInclusiveTimestampBoundsDeduplicatesAndPreservesRawProperties() throws Exception {
        MongoCollection<Document> collection = database.getCollection(BSM_COLLECTION);
        collection.insertMany(List.of(
                feature("first-at-start", "2024-01-01T00:00:00Z", "duplicate", -105.0, 40.0, "first"),
                feature("duplicate-inside-window", "2024-01-01T00:00:05Z", "duplicate", -105.0, 40.0, "duplicate"),
                feature("end-boundary", "2024-01-01T00:00:10Z", "end", -105.5, 40.5, "inclusive-end"),
                feature("outside-polygon", "2024-01-01T00:00:04Z", "outside", 0.0, 0.0, "outside"),
                feature("after-time-range", "2024-01-01T00:00:11Z", "after", -105.0, 40.0, "after")));
        // A matching record in the default collection must not be selected.
        database.getCollection("ProcessedBsm").insertOne(
                feature("wrong-collection", "2024-01-01T00:00:01Z", "wrong", -105.0, 40.0, "wrong"));

        List<Map<String, Object>> result = service.query(new ProcessedGeoMessageRequest(
                POLYGON,
                "2024-01-01T00:00:00Z",
                "2024-01-01T00:00:10Z",
                "bSm"));

        assertEquals(2, result.size());
        assertEquals("first", marker(result.get(0)));
        assertEquals("inclusive-end", marker(result.get(1)));
        assertFalse(result.get(0).containsKey("_id"));
        assertFalse(result.get(0).containsKey("recordGeneratedAt"));
        assertEquals("BSM", ((Map<?, ?>) result.get(0).get("properties")).get("messageType"));
        assertTrue(((Map<?, ?>) result.get(0).get("properties")).containsKey("arbitraryPayload"));

        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("\"_id\""));
        assertFalse(json.contains("recordGeneratedAt"));
        assertTrue(json.contains("arbitraryPayload"));

        List<Document> indexes = database.getCollection(BSM_COLLECTION)
                .listIndexes().into(new ArrayList<>());
        assertTrue(indexes.stream().anyMatch(index -> {
            Document keys = index.get("key", Document.class);
            return keys != null
                    && Integer.valueOf(1).equals(keys.get("properties.timeStamp"))
                    && "2dsphere".equals(keys.get("geometry"));
        }), "the timestamp ascending and geometry 2dsphere compound index should exist");
    }

    @Test
    void usesConfiguredPsmCollectionAndReturnsBareFeatureData() {
        Document psm = feature("psm", "2024-01-01T00:00:00Z", "pedestrian-1", -105.0, 40.0, "psm-result");
        psm.put("properties", new Document("schemaVersion", 2)
                .append("id", "pedestrian-1")
                .append("timeStamp", "2024-01-01T00:00:00Z")
                .append("messageType", "PSM")
                .append("basicType", "aPEDESTRIAN")
                .append("arbitraryPayload", new Document("marker", "psm-result")));
        database.getCollection(PSM_COLLECTION).insertOne(psm);

        List<Map<String, Object>> result = service.query(new ProcessedGeoMessageRequest(
                POLYGON,
                "2024-01-01T00:00:00Z",
                "2024-01-01T00:00:01Z",
                "PSM"));

        assertEquals(1, result.size());
        assertEquals("PSM", ((Map<?, ?>) result.getFirst().get("properties")).get("messageType"));
        assertEquals("psm-result", marker(result.getFirst()));
    }

    private static Document feature(
            String name, String timestamp, String id, double longitude, double latitude, String marker) {
        Document properties = new Document("schemaVersion", 2)
                .append("id", id)
                .append("timeStamp", timestamp)
                .append("messageType", "BSM")
                .append("arbitraryPayload", new Document("marker", marker));
        return new Document("type", "Feature")
                .append("geometry", new Document("type", "Point")
                        .append("coordinates", List.of(longitude, latitude)))
                .append("properties", properties)
                .append("recordGeneratedAt", "remove-" + name);
    }

    private static String marker(Map<String, Object> feature) {
        Map<?, ?> properties = (Map<?, ?>) feature.get("properties");
        Map<?, ?> payload = (Map<?, ?>) properties.get("arbitraryPayload");
        return (String) payload.get("marker");
    }
}

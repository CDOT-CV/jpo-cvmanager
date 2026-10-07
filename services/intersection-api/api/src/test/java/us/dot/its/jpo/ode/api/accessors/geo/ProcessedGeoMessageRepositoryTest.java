package us.dot.its.jpo.ode.api.accessors.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;

class ProcessedGeoMessageRepositoryTest {

    @Test
    void ensuresLegacyCompoundIndexThenStreamsUnsortedUnboundedGeoQuery() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        @SuppressWarnings("unchecked")
        Stream<Document> cursor = mock(Stream.class);
        when(mongoTemplate.indexOps("processed_bsm")).thenReturn(indexOperations);
        when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("processed_bsm")))
                .thenReturn(cursor);
        ProcessedGeoMessageRepository repository = new ProcessedGeoMessageRepository(mongoTemplate);
        List<List<Double>> polygon = List.of(
                List.of(-105d, 39d), List.of(-104d, 39d),
                List.of(-104d, 40d), List.of(-105d, 39d));

        assertEquals(cursor, repository.find("processed_bsm", polygon,
                "2024-01-01T00:00:00Z", "2024-01-01T00:01:00Z"));

        ArgumentCaptor<IndexDefinition> indexCaptor = ArgumentCaptor.forClass(IndexDefinition.class);
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        InOrder order = inOrder(mongoTemplate, indexOperations);
        order.verify(mongoTemplate).indexOps("processed_bsm");
        order.verify(indexOperations).createIndex(indexCaptor.capture());
        order.verify(mongoTemplate).stream(queryCaptor.capture(), eq(Document.class), eq("processed_bsm"));

        assertEquals(new Document("properties.timeStamp", 1).append("geometry", "2dsphere"),
                indexCaptor.getValue().getIndexKeys());
        Document expectedQuery = new Document("properties.timeStamp",
                new Document("$gte", "2024-01-01T00:00:00Z").append("$lte", "2024-01-01T00:01:00Z"))
                .append("geometry", new Document("$geoWithin", new Document("$geometry",
                        new Document("type", "Polygon").append("coordinates", List.of(polygon)))));
        assertEquals(expectedQuery, queryCaptor.getValue().getQueryObject());
        assertEquals(0, queryCaptor.getValue().getLimit());
        assertEquals(new Document(), queryCaptor.getValue().getSortObject());
    }
}

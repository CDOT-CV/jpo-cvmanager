package us.dot.its.jpo.ode.api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import us.dot.its.jpo.ode.api.accessors.geo.ProcessedGeoMessageRepository;
import us.dot.its.jpo.ode.api.models.geo.ProcessedGeoMessageRequest;

class ProcessedGeoMessageServiceTest {

    private static final List<List<Double>> POLYGON = List.of(
            List.of(-1d, -1d), List.of(1d, -1d), List.of(1d, 1d),
            List.of(-1d, 1d), List.of(-1d, -1d));

    @Test
    void skipsOldSchemaRecordsBeforeApplyingTheCapAndDeduplicatesFirstEncounter() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        AtomicBoolean cursorClosed = new AtomicBoolean();
        when(repository.find(eq("ProcessedBsm"), anyList(), anyString(), anyString()))
                .thenReturn(cursor(List.of(
                        feature("old-schema", "2024-01-01T00:00:21Z", 0d, 0d, 1),
                        feature("first", "2024-01-01T00:00:20Z", 0d, 0d),
                        feature("first", "2024-01-01T00:00:21Z", 0d, 0d),
                        feature("second", "2024-01-01T00:00:10Z", 0.5d, 0.5d),
                        feature("after-cap", "2024-01-01T00:00:05Z", 0.7d, 0.7d)), cursorClosed));
        ProcessedGeoMessageService service = service(repository, "2");

        List<Map<String, Object>> result = service.query(request("bSm"));

        assertEquals(List.of("second", "first"), ids(result));
        assertTrue(cursorClosed.get(), "Mongo cursor must close after successful iteration");
        verify(repository).find(eq("ProcessedBsm"), anyList(), eq("2024-01-01T00:00:00Z"),
                eq("2024-01-01T00:01:00Z"));
    }

    @Test
    void usesBsmAndPsmCollectionsForCaseInsensitiveMessageTypes() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        when(repository.find(eq("ProcessedBsm"), anyList(), anyString(), anyString()))
                .thenReturn(cursor(List.of(feature("bsm-id", "2024-01-01T00:00:30Z", 0d, 0d)),
                        new AtomicBoolean()));
        when(repository.find(eq("ProcessedPsm"), anyList(), anyString(), anyString()))
                .thenReturn(cursor(List.of(feature("psm-id", "2024-01-01T00:00:31Z", 0d, 0d)),
                        new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "100");

        List<Map<String, Object>> bsm = service.query(request("bSm"));
        List<Map<String, Object>> psm = service.query(request("pSm"));

        assertEquals(List.of("bsm-id"), ids(bsm));
        assertEquals(List.of("psm-id"), ids(psm));
        verify(repository).find(eq("ProcessedBsm"), anyList(), anyString(), anyString());
        verify(repository).find(eq("ProcessedPsm"), anyList(), anyString(), anyString());
    }

    @Test
    void formatsOffsetAndTimezoneLessRequestTimesAsUtcWholeSeconds() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(List.of(), new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "");
        ProcessedGeoMessageRequest request = new ProcessedGeoMessageRequest(
                POLYGON,
                "2024-01-01T01:00:00.999+01:00",
                "2024-01-01T00:01:00.999",
                "BSM");

        assertTrue(service.query(request).isEmpty());

        verify(repository).find(eq("ProcessedBsm"), anyList(), eq("2024-01-01T00:00:00Z"),
                eq("2024-01-01T00:01:00Z"));
    }

    @Test
    void normalizesBasicNumericOffsetsOnRequestBounds() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(List.of(), new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "10");
        ProcessedGeoMessageRequest request = new ProcessedGeoMessageRequest(
                POLYGON,
                "2024-01-01T01:00:00+0100",
                "2024-01-01T00:01:00+0000",
                "BSM");

        assertTrue(service.query(request).isEmpty());

        verify(repository).find(eq("ProcessedBsm"), anyList(), eq("2024-01-01T00:00:00Z"),
                eq("2024-01-01T00:01:00Z"));
    }

    @Test
    void retainsDifferentNegativeEpochTimeBucketsUsingPythonTruncation() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        List<Document> cursorDocuments = List.of(
                feature("same", "1969-12-31T23:59:49.800Z", 0d, 0d),
                feature("same", "1969-12-31T23:59:50.100Z", 0d, 0d));
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(cursorDocuments, new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "10");

        List<Map<String, Object>> result = service.query(request("BSM"));

        assertEquals(2, result.size(), "Python int(timestamp()) truncates negative fractions toward zero");
    }

    @Test
    void truncatesNegativeCoordinateBucketsTowardZeroAndKeepsFirstEncounteredFeature() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(List.of(
                        feature("same", "2024-01-01T00:00:28.900Z", -0.00009d, -0.00009d),
                        feature("same", "2024-01-01T00:00:27.100Z", 0.00001d, 0.00001d),
                        feature("same", "2024-01-01T00:00:28.500Z", -0.00011d, -0.00011d),
                        feature("same", "2024-01-01T00:00:28.200Z", 0.00011d, 0.00011d)),
                        new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "10");

        List<Map<String, Object>> result = service.query(request("BSM"));

        assertEquals(3, result.size());
        assertEquals(List.of(
                List.of(0.00011d, 0.00011d),
                List.of(-0.00011d, -0.00011d),
                List.of(-0.00009d, -0.00009d)),
                result.stream()
                        .map(feature -> ((Map<?, ?>) feature.get("geometry")).get("coordinates"))
                        .toList());
    }

    @Test
    void deduplicatesFractionalTimestampsAndKeepsTheFirstFeatureForTheKey() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        List<Document> cursorDocuments = List.of(
                feature("same", "2024-01-01T00:00:21.125Z", 0.25d, 0.25d),
                feature("same", "2024-01-01T00:00:21.999Z", 0.25d, 0.25d));
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(cursorDocuments, new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "10");

        List<Map<String, Object>> result = service.query(request("BSM"));

        assertEquals(1, result.size());
        assertEquals("2024-01-01T00:00:21.125Z",
                ((Map<?, ?>) result.getFirst().get("properties")).get("timeStamp"));
    }

    @Test
    void truncatesStoredNanosecondsToPythonMicrosecondPrecisionBeforeHashing() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        List<Document> cursorDocuments = List.of(
                feature("submicrosecond", "2024-01-01T00:00:09.999999999Z", 0.25d, 0.25d),
                feature("submicrosecond", "2024-01-01T00:00:09.999999Z", 0.25d, 0.25d));
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(cursorDocuments, new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "10");

        List<Map<String, Object>> result = service.query(request("BSM"));

        assertEquals(1, result.size());
        assertEquals("2024-01-01T00:00:09.999999999Z",
                ((Map<?, ?>) result.getFirst().get("properties")).get("timeStamp"),
                "Deduplication should match Python while preserving the original timestamp string");
    }

    @Test
    void truncatesNegativeSubmicrosecondTimestampsTowardEpochBeforeHashing() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        List<Document> cursorDocuments = List.of(
                feature("negative-submicrosecond", "1969-12-31T23:59:59.999999999Z", 0d, 0d),
                feature("negative-submicrosecond", "1969-12-31T23:59:59.999999Z", 0d, 0d));
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(cursorDocuments, new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "10");

        List<Map<String, Object>> result = service.query(request("BSM"));

        assertEquals(1, result.size());
        assertEquals("1969-12-31T23:59:59.999999999Z",
                ((Map<?, ?>) result.getFirst().get("properties")).get("timeStamp"));
    }

    @Test
    void deduplicatesEquivalentStoredBasicAndColonOffsets() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        List<Document> cursorDocuments = List.of(
                feature("offset-equivalent", "2024-01-01T00:00:21.500+0000", 0.25d, 0.25d),
                feature("offset-equivalent", "2024-01-01T01:00:21.500+01:00", 0.25d, 0.25d));
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(cursorDocuments, new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "10");

        List<Map<String, Object>> result = service.query(request("BSM"));

        assertEquals(1, result.size());
        assertEquals("2024-01-01T00:00:21.500+0000",
                ((Map<?, ?>) result.getFirst().get("properties")).get("timeStamp"));
    }

    @Test
    void sortsByTimestampStringAndKeepsEncounterOrderForEqualStrings() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(List.of(
                        feature("whole-equal-first", "2024-01-01T00:00:21Z", 0d, 0d),
                        feature("fractional", "2024-01-01T00:00:21.125Z", 0d, 0d),
                        feature("whole-equal-second", "2024-01-01T00:00:21Z", 0d, 0d),
                        feature("whole-equal-third", "2024-01-01T00:00:21Z", 0d, 0d)),
                        new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "10");

        List<Map<String, Object>> result = service.query(request("BSM"));

        assertEquals(List.of("fractional", "whole-equal-first", "whole-equal-second", "whole-equal-third"),
                ids(result));
    }

    @Test
    void removesOnlyTopLevelMongoMetadataAndPreservesTheRemainingFeatureExactly() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        Document source = new Document("_id", "mongo-private-id")
                .append("recordGeneratedAt", "mongo-generated-time")
                .append("type", "Feature")
                .append("bbox", List.of(-105d, 39d, -104d, 40d))
                .append("geometry", new Document("type", "Point")
                        .append("coordinates", List.of(-105d, 40d))
                        .append("customGeometryField", "preserve geometry data"))
                .append("properties", new Document("schemaVersion", 2)
                        .append("id", "custom-id")
                        .append("timeStamp", "2024-01-01T00:00:30Z")
                        .append("messageType", "PSM")
                        .append("customPayload", new Document("nested", List.of(1, "two"))
                                .append("enabled", true))
                        .append("recordGeneratedAt", "preserve nested property")
                        .append("_id", "preserve nested property id"))
                .append("customTopLevel", new Document("untouched", true));
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(List.of(source), new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "10");

        List<Map<String, Object>> result = service.query(request("PSM"));

        Map<String, Object> expectedFeature = Map.of(
                "type", "Feature",
                "bbox", List.of(-105d, 39d, -104d, 40d),
                "geometry", Map.of("type", "Point", "coordinates", List.of(-105d, 40d),
                        "customGeometryField", "preserve geometry data"),
                "properties", Map.of("schemaVersion", 2, "id", "custom-id",
                        "timeStamp", "2024-01-01T00:00:30Z", "messageType", "PSM",
                        "customPayload", Map.of("nested", List.of(1, "two"), "enabled", true),
                        "recordGeneratedAt", "preserve nested property",
                        "_id", "preserve nested property id"),
                "customTopLevel", Map.of("untouched", true));
        assertEquals(List.of(expectedFeature), result);
    }

    @Test
    void capIsAppliedToEncounterOrderBeforeTimestampSorting() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        List<Document> cursorDocuments = List.of(
                feature("later", "2024-01-01T00:00:40Z", 0d, 0d),
                feature("middle", "2024-01-01T00:00:20Z", 0.5d, 0.5d),
                feature("earlier-after-cap", "2024-01-01T00:00:01Z", 1d, 1d));
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(cursorDocuments, new AtomicBoolean()));
        ProcessedGeoMessageService service = service(repository, "2");

        List<Map<String, Object>> result = service.query(request("BSM"));

        assertEquals(List.of("middle", "later"), ids(result));
    }

    @Test
    void zeroCapReturnsEmptyWithoutOpeningMongoCursor() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        ProcessedGeoMessageService service = service(repository, "0");

        assertTrue(service.query(request("BSM")).isEmpty());

        verify(repository, never()).find(anyString(), anyList(), anyString(), anyString());
    }

    @Test
    void invalidMessageTypeOrUnclosedPolygonReturnsBadRequestBeforeMongo() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        ProcessedGeoMessageService service = service(repository, "10");
        ProcessedGeoMessageRequest valid = request("BSM");

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(new ProcessedGeoMessageRequest(
                valid.geometry(), valid.start(), valid.end(), "TIM")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(new ProcessedGeoMessageRequest(
                valid.geometry().subList(0, valid.geometry().size() - 1), valid.start(), valid.end(), "BSM")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(new ProcessedGeoMessageRequest(
                valid.geometry(), "invalid", valid.end(), "BSM")));
        verify(repository, never()).find(anyString(), anyList(), anyString(), anyString());
    }

    @Test
    void rejectsMissingRequestAndTimestampFieldsBeforeMongo() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        ProcessedGeoMessageService service = service(repository, "10");
        ProcessedGeoMessageRequest valid = request("BSM");

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(new ProcessedGeoMessageRequest(
                null, valid.start(), valid.end(), "BSM")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(new ProcessedGeoMessageRequest(
                valid.geometry(), " ", valid.end(), "BSM")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(new ProcessedGeoMessageRequest(
                valid.geometry(), valid.start(), null, "BSM")));

        verify(repository, never()).find(anyString(), anyList(), anyString(), anyString());
    }

    @Test
    void rejectsNegativeAndNonIntegerRecordCapsAtStartup() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);

        assertThrows(IllegalArgumentException.class, () -> service(repository, "-1"));
        assertThrows(IllegalArgumentException.class, () -> service(repository, "ten"));
    }

    @Test
    void rejectsInvalidCoordinateValuesAndShapesBeforeMongo() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        ProcessedGeoMessageService service = service(repository, "10");
        ProcessedGeoMessageRequest valid = request("BSM");

        List<List<Double>> outOfRange = new ArrayList<>(valid.geometry());
        outOfRange.set(0, List.of(181d, -1d));
        List<List<Double>> nonfinite = new ArrayList<>(valid.geometry());
        nonfinite.set(0, List.of(Double.NaN, -1d));
        List<List<Double>> nullCoordinate = new ArrayList<>(valid.geometry());
        nullCoordinate.set(0, null);
        List<List<Double>> wrongPairSize = new ArrayList<>(valid.geometry());
        wrongPairSize.set(0, List.of(-1d, -1d, 0d));

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(new ProcessedGeoMessageRequest(
                outOfRange, valid.start(), valid.end(), "BSM")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(new ProcessedGeoMessageRequest(
                nonfinite, valid.start(), valid.end(), "BSM")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(new ProcessedGeoMessageRequest(
                nullCoordinate, valid.start(), valid.end(), "BSM")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(new ProcessedGeoMessageRequest(
                wrongPairSize, valid.start(), valid.end(), "BSM")));

        verify(repository, never()).find(anyString(), anyList(), anyString(), anyString());
    }

    @Test
    void reportsMongoAvailabilityFailuresAs503AndUnexpectedFailuresAs500() {
        ProcessedGeoMessageRepository unavailableRepository = mock(ProcessedGeoMessageRepository.class);
        when(unavailableRepository.find(anyString(), anyList(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("internal db detail"));
        ProcessedGeoMessageService unavailableService = service(unavailableRepository, "10");
        ResponseStatusException unavailable = assertThrows(ResponseStatusException.class,
                () -> unavailableService.query(request("BSM")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
        assertFalse(unavailable.getReason().contains("internal db detail"));

        ProcessedGeoMessageRepository brokenRepository = mock(ProcessedGeoMessageRepository.class);
        when(brokenRepository.find(anyString(), anyList(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("internal processing detail"));
        ProcessedGeoMessageService brokenService = service(brokenRepository, "10");
        ResponseStatusException broken = assertThrows(ResponseStatusException.class,
                () -> brokenService.query(request("BSM")));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, broken.getStatusCode());
        assertFalse(broken.getReason().contains("internal processing detail"));

        ProcessedGeoMessageRepository queryFailureRepository = mock(ProcessedGeoMessageRepository.class);
        when(queryFailureRepository.find(anyString(), anyList(), anyString(), anyString()))
                .thenThrow(new MongoException("non-availability mongo failure"));
        ProcessedGeoMessageService queryFailureService = service(queryFailureRepository, "10");
        ResponseStatusException queryFailure = assertThrows(ResponseStatusException.class,
                () -> queryFailureService.query(request("BSM")));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, queryFailure.getStatusCode());

        ProcessedGeoMessageRepository timeoutRepository = mock(ProcessedGeoMessageRepository.class);
        when(timeoutRepository.find(anyString(), anyList(), anyString(), anyString()))
                .thenThrow(new QueryTimeoutException("internal timeout"));
        ResponseStatusException timeout = assertThrows(ResponseStatusException.class,
                () -> service(timeoutRepository, "10").query(request("BSM")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, timeout.getStatusCode());
    }

    @Test
    void mapsMalformedStoredFeaturesToInternalServerError() {
        Document missingId = feature("missing-id", "2024-01-01T00:00:20Z", 0d, 0d);
        ((Document) missingId.get("properties")).remove("id");

        Document missingTimestamp = feature("missing-time", "2024-01-01T00:00:20Z", 0d, 0d);
        ((Document) missingTimestamp.get("properties")).remove("timeStamp");

        Document missingGeometry = feature("missing-geometry", "2024-01-01T00:00:20Z", 0d, 0d);
        missingGeometry.remove("geometry");

        Document malformedCoordinates = feature("bad-coordinates", "2024-01-01T00:00:20Z", 0d, 0d);
        malformedCoordinates.put("geometry", new Document("coordinates", List.of("west", 0d)));

        Document invalidTimestamp = feature("bad-time", "2024-01-01T00:00:20Z", 0d, 0d);
        ((Document) invalidTimestamp.get("properties")).put("timeStamp", "not-a-timestamp");

        for (Document malformed : List.of(missingId, missingTimestamp, missingGeometry,
                malformedCoordinates, invalidTimestamp)) {
            ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
            when(repository.find(anyString(), anyList(), anyString(), anyString()))
                    .thenReturn(Stream.of(malformed));

            assertStatus(HttpStatus.INTERNAL_SERVER_ERROR,
                    () -> service(repository, "10").query(request("BSM")));
        }
    }

    @Test
    void closesCursorAndReturns503WhenMongoTimesOutDuringIteration() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        AtomicBoolean cursorClosed = new AtomicBoolean();
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(failingCursor(new MongoTimeoutException("internal timeout"), cursorClosed));
        ProcessedGeoMessageService service = service(repository, "10");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.query(request("BSM")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertTrue(cursorClosed.get(), "Mongo cursor must close after iteration failure");
    }

    @Test
    void closesCursorAndReturns500WhenDocumentProcessingFailsDuringIteration() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        AtomicBoolean cursorClosed = new AtomicBoolean();
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(failingCursor(new IllegalStateException("malformed document"), cursorClosed));
        ProcessedGeoMessageService service = service(repository, "10");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.query(request("BSM")));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertTrue(cursorClosed.get(), "Mongo cursor must close after processing failure");
    }

    @Test
    void rejectsClosedButDegeneratePolygonBeforeMongo() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        ProcessedGeoMessageService service = service(repository, "10");
        List<List<Double>> degenerate = List.of(
                List.of(0d, 0d), List.of(0d, 0d), List.of(0d, 0d), List.of(0d, 0d));

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.query(new ProcessedGeoMessageRequest(
                degenerate, "2024-01-01T00:00:00Z", "2024-01-01T00:01:00Z", "BSM")));

        verify(repository, never()).find(anyString(), anyList(), anyString(), anyString());
    }

    @Test
    void fallsBackToDefaultCapWhenConfigurationIsBlank() {
        ProcessedGeoMessageRepository repository = mock(ProcessedGeoMessageRepository.class);
        ProcessedGeoMessageService service = service(repository, " ");

        // The blank setting uses the 10,000-record default: the first 10,001 unique
        // records demonstrate that the service remains capped while retaining an
        // order that is independent from its final timestamp sort.
        List<Document> cursorDocuments = new ArrayList<>(10_001);
        for (int index = 0; index < 10_001; index++) {
            cursorDocuments.add(feature("id-" + index, String.format("2024-01-01T00:%02d:%02dZ",
                    (index / 60) % 60, index % 60), (index % 100) / 10d, (index / 100) / 10d));
        }
        when(repository.find(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(cursor(cursorDocuments, new AtomicBoolean()));

        List<Map<String, Object>> result = service.query(request("BSM"));

        assertEquals(10_000, result.size());
    }

    private static ProcessedGeoMessageService service(ProcessedGeoMessageRepository repository, String cap) {
        return new ProcessedGeoMessageService(repository, "ProcessedBsm", "ProcessedPsm", cap);
    }

    private static ProcessedGeoMessageRequest request(String messageType) {
        return new ProcessedGeoMessageRequest(POLYGON,
                "2024-01-01T00:00:00Z", "2024-01-01T00:01:00Z", messageType);
    }

    private static List<String> ids(List<Map<String, Object>> features) {
        return features.stream()
                .map(feature -> ((Map<?, ?>) feature.get("properties")).get("id").toString())
                .toList();
    }

    private static Document feature(String id, String timestamp, double longitude, double latitude) {
        return feature(id, timestamp, longitude, latitude, 2);
    }

    private static Document feature(String id, String timestamp, double longitude, double latitude,
            int schemaVersion) {
        return new Document("_id", "mongo-" + id + timestamp)
                .append("recordGeneratedAt", "remove-me")
                .append("type", "Feature")
                .append("geometry", new Document("type", "Point")
                        .append("coordinates", List.of(longitude, latitude)))
                .append("properties", new Document("schemaVersion", schemaVersion)
                        .append("id", id)
                        .append("timeStamp", timestamp));
    }

    private static Stream<Document> cursor(List<Document> documents, AtomicBoolean closed) {
        return documents.stream().onClose(() -> closed.set(true));
    }

    private static Stream<Document> failingCursor(RuntimeException failure, AtomicBoolean closed) {
        Spliterator<Document> failingSpliterator = new Spliterators.AbstractSpliterator<>(
                Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override
            public boolean tryAdvance(Consumer<? super Document> action) {
                throw failure;
            }
        };
        return StreamSupport.stream(failingSpliterator, false).onClose(() -> closed.set(true));
    }

    private static void assertStatus(HttpStatus expected, Runnable invocation) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, invocation::run);
        assertEquals(expected, exception.getStatusCode());
    }
}

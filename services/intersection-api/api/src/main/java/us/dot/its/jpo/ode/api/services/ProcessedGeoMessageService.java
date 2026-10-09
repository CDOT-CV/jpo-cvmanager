package us.dot.its.jpo.ode.api.services;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.QueryTimeoutException;

import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;

import us.dot.its.jpo.ode.api.accessors.geo.ProcessedGeoMessageRepository;
import us.dot.its.jpo.ode.api.models.geo.ProcessedGeoMessageRequest;

/** Implements the legacy CV Manager polygon-query selection and response behavior. */
@Service
public class ProcessedGeoMessageService {

    private static final Logger log = LoggerFactory.getLogger(ProcessedGeoMessageService.class);
    private static final double COORDINATE_RESOLUTION = 0.0001d;
    private static final DateTimeFormatter QUERY_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    private static final Pattern BASIC_NUMERIC_OFFSET = Pattern.compile("([+-]\\d{2})(\\d{2})$");
    private static final int DEFAULT_MAX_RECORDS = 10_000;

    private final ProcessedGeoMessageRepository repository;
    private final String bsmCollection;
    private final String psmCollection;
    private final int maxRecords;

    public ProcessedGeoMessageService(
            ProcessedGeoMessageRepository repository,
            @Value("${geo-query.bsm-collection:ProcessedBsm}") String bsmCollection,
            @Value("${geo-query.psm-collection:ProcessedPsm}") String psmCollection,
            @Value("${geo-query.max-records:}") String configuredMaxRecords) {
        this.repository = repository;
        this.bsmCollection = requireCollectionName(bsmCollection, "BSM");
        this.psmCollection = requireCollectionName(psmCollection, "PSM");
        this.maxRecords = parseMaxRecords(configuredMaxRecords);
    }

    /**
     * Queries one processed-message collection, retaining first-seen schema-2
     * messages until the configured cap and then sorting those messages by their
     * original timestamp strings.
     */
    public List<Map<String, Object>> query(ProcessedGeoMessageRequest request) {
        ValidatedRequest validated = validate(request);
        if (maxRecords == 0) {
            return List.of();
        }

        String collection = validated.messageType().equals("BSM") ? bsmCollection : psmCollection;
        String start = QUERY_TIMESTAMP_FORMAT.format(validated.start().truncatedTo(ChronoUnit.SECONDS));
        String end = QUERY_TIMESTAMP_FORMAT.format(validated.end().truncatedTo(ChronoUnit.SECONDS));
        long startedAt = System.nanoTime();
        long scanned = 0;
        long schemaSkipped = 0;
        int returned = 0;

        try (Stream<Document> documents = repository.find(
                collection, validated.geometry(), start, end)) {
            Map<String, Map<String, Object>> selected = new LinkedHashMap<>();
            Iterator<Document> cursor = documents.iterator();
            while (cursor.hasNext()) {
                Document document = cursor.next();
                scanned++;

                Map<String, Object> properties = nestedMap(document, "properties");
                if (!isSchemaVersionTwo(properties.get("schemaVersion"))) {
                    schemaSkipped++;
                    log.warn("Skipping processed geo message with schema version {}",
                            properties.get("schemaVersion"));
                    continue;
                }

                String key = deduplicationKey(document, properties);
                if (!selected.containsKey(key) && selected.size() < maxRecords) {
                    Map<String, Object> feature = new LinkedHashMap<>(document);
                    feature.remove("_id");
                    feature.remove("recordGeneratedAt");
                    selected.put(key, feature);
                }
            }

            List<Map<String, Object>> result = new ArrayList<>(selected.values());
            // List.sort is stable; compare the exact timestamp strings as Python does.
            result.sort(Comparator.comparing(feature -> timestampString(feature)));
            returned = result.size();
            return result;
        } catch (DataAccessException | MongoException exception) {
            if (isMongoUnavailable(exception)) {
                log.error("Mongo is unavailable for processed geo query in collection {}", collection, exception);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Geo message data is temporarily unavailable.");
            }
            log.error("Mongo query failed for processed geo messages in collection {}", collection, exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to process geo message data.");
        } catch (RuntimeException exception) {
            log.error("Failed to process geo message query for collection {}", collection, exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to process geo message data.");
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("Processed geo message query completed: type={} collection={} scanned={} schemaSkipped={} returned={} durationMs={}",
                    validated.messageType(), collection, scanned, schemaSkipped, returned, durationMs);
        }
    }

    private static ValidatedRequest validate(ProcessedGeoMessageRequest request) {
        if (request == null) {
            throw badRequest("A request body is required.");
        }
        String messageType = request.msgType() == null ? "" : request.msgType().trim().toUpperCase();
        if (!messageType.equals("BSM") && !messageType.equals("PSM")) {
            throw badRequest("msg_type must be BSM or PSM.");
        }

        List<List<Double>> geometry = validateGeometry(request.geometry());
        Instant start = parseRequestTimestamp(request.start(), "start");
        Instant end = parseRequestTimestamp(request.end(), "end");
        return new ValidatedRequest(messageType, geometry, start, end);
    }

    private static List<List<Double>> validateGeometry(List<List<Double>> geometry) {
        if (geometry == null || geometry.size() < 4) {
            throw badRequest("geometry must be a closed polygon ring containing at least four coordinates.");
        }

        List<List<Double>> validated = new ArrayList<>(geometry.size());
        for (List<Double> coordinate : geometry) {
            if (coordinate == null || coordinate.size() != 2) {
                throw badRequest("Each geometry coordinate must contain longitude and latitude.");
            }
            Double longitude = coordinate.get(0);
            Double latitude = coordinate.get(1);
            if (longitude == null || latitude == null
                    || !Double.isFinite(longitude) || !Double.isFinite(latitude)
                    || longitude < -180 || longitude > 180
                    || latitude < -90 || latitude > 90) {
                throw badRequest("geometry coordinates must be finite longitude/latitude values in range.");
            }
            validated.add(List.of(longitude, latitude));
        }

        List<Double> first = validated.getFirst();
        List<Double> last = validated.getLast();
        if (!first.equals(last)) {
            throw badRequest("geometry must be a closed polygon ring.");
        }
        long distinctVertices = validated.subList(0, validated.size() - 1).stream().distinct().count();
        if (distinctVertices < 3) {
            throw badRequest("geometry must contain at least three distinct polygon vertices.");
        }
        return List.copyOf(validated);
    }

    private static boolean isMongoUnavailable(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof DataAccessResourceFailureException
                    || current instanceof QueryTimeoutException
                    || current instanceof MongoTimeoutException
                    || current instanceof MongoSocketException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Instant parseRequestTimestamp(String value, String field) {
        if (value == null || value.isBlank()) {
            throw badRequest(field + " is required and must be an ISO-8601 timestamp.");
        }
        String timestamp = value.trim();
        try {
            return OffsetDateTime.parse(normalizeBasicOffset(timestamp)).toInstant();
        } catch (DateTimeParseException offsetError) {
            try {
                return LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException localError) {
                throw badRequest(field + " must be an ISO-8601 timestamp.");
            }
        }
    }

    private static String deduplicationKey(Document document, Map<String, Object> properties) {
        Object id = properties.get("id");
        if (id == null) {
            throw new IllegalArgumentException("Processed geo message is missing properties.id");
        }
        String timestamp = timestampString(document);
        long epochSeconds = pythonIntegerTimestamp(parseStoredTimestamp(timestamp));
        Map<String, Object> geometry = nestedMap(document, "geometry");
        Object coordinatesValue = geometry.get("coordinates");
        if (!(coordinatesValue instanceof List<?> coordinates) || coordinates.size() < 2) {
            throw new IllegalArgumentException("Processed geo message has invalid geometry.coordinates");
        }
        double longitude = numericCoordinate(coordinates.get(0));
        double latitude = numericCoordinate(coordinates.get(1));

        // Python int() truncates toward zero. Java's cast has the same behavior for
        // finite doubles within long range, including negative coordinate buckets.
        long timeBucket = epochSeconds / 10;
        long longitudeBucket = (long) (longitude / COORDINATE_RESOLUTION);
        long latitudeBucket = (long) (latitude / COORDINATE_RESOLUTION);
        return id + "_" + timeBucket + "_" + longitudeBucket + "_" + latitudeBucket;
    }

    private static long pythonIntegerTimestamp(Instant timestamp) {
        // Python's datetime parser stores microseconds. Truncate Mongo's nanosecond
        // representation to that precision before reproducing int(timestamp()).
        int microsecondNanos = (timestamp.getNano() / 1_000) * 1_000;
        double seconds = timestamp.getEpochSecond() + microsecondNanos / 1_000_000_000d;
        return (long) seconds;
    }

    private static Instant parseStoredTimestamp(String timestamp) {
        try {
            return OffsetDateTime.parse(normalizeBasicOffset(timestamp)).toInstant();
        } catch (DateTimeParseException offsetError) {
            try {
                return LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException localError) {
                throw new IllegalArgumentException("Processed geo message has invalid properties.timeStamp", localError);
            }
        }
    }

    private static String normalizeBasicOffset(String timestamp) {
        if (timestamp.indexOf('T') < 0 && timestamp.indexOf('t') < 0) {
            return timestamp;
        }
        Matcher matcher = BASIC_NUMERIC_OFFSET.matcher(timestamp);
        return matcher.find() ? matcher.replaceFirst("$1:$2") : timestamp;
    }

    private static double numericCoordinate(Object coordinate) {
        if (!(coordinate instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Processed geo message has invalid coordinates");
        }
        return number.doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?> nested)) {
            throw new IllegalArgumentException("Processed geo message is missing " + key);
        }
        return (Map<String, Object>) nested;
    }

    private static boolean isSchemaVersionTwo(Object value) {
        return value instanceof Number number && number.doubleValue() == 2d;
    }

    private static String timestampString(Map<String, Object> feature) {
        Object propertiesValue = feature.get("properties");
        if (!(propertiesValue instanceof Map<?, ?> properties)
                || !(properties.get("timeStamp") instanceof String timestamp)) {
            throw new IllegalArgumentException("Processed geo message is missing properties.timeStamp");
        }
        return timestamp;
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static String requireCollectionName(String collection, String messageType) {
        if (collection == null || collection.isBlank()) {
            throw new IllegalArgumentException("geo-query collection name for " + messageType + " must not be blank");
        }
        return collection;
    }

    private static int parseMaxRecords(String configuredMaxRecords) {
        if (configuredMaxRecords == null || configuredMaxRecords.isBlank()) {
            return DEFAULT_MAX_RECORDS;
        }
        try {
            int value = Integer.parseInt(configuredMaxRecords.trim());
            if (value < 0) {
                throw new IllegalArgumentException("geo-query.max-records must be zero or greater");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("geo-query.max-records must be an integer", exception);
        }
    }

    private record ValidatedRequest(String messageType, List<List<Double>> geometry, Instant start, Instant end) {
    }
}

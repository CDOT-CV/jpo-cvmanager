package us.dot.its.jpo.ode.api.accessors.counts;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.geotools.referencing.GeodeticCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.ode.api.accessors.IntersectionCriteria;
import us.dot.its.jpo.ode.api.accessors.PageableQuery;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.services.PrometheusService;
import us.dot.its.jpo.ode.model.OdeBsmData;

@Slf4j
@Component
public class CountsRepositoryImpl implements CountsRepository, PageableQuery {

    private final MongoTemplate mongoTemplate;
    private final PrometheusService prometheusService;
    private final ObjectMapper mapper = DateJsonMapper.getInstance()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final String collectionName = "OdeBsmJson";
    private final String DATE_FIELD = "metadata.odeReceivedAt";
    private final String ORIGIN_IP_FIELD = "metadata.originIp";
    private final String VEHICLE_ID_FIELD = "payload.data.coreData.id";

    @Value("${prometheus.rsu.counts.metric.name:kafka_produced_rsu_messages_total}")
    private String prometheusMetricName;

    private static final String[] MESSAGE_TYPES = { "BSM", "TIM", "Map", "SPaT", "SRM", "SSM", "PSM", "SDSM" };

    @Autowired
    public CountsRepositoryImpl(MongoTemplate mongoTemplate, PrometheusService prometheusService) {
        this.mongoTemplate = mongoTemplate;
        this.prometheusService = prometheusService;
    }

    /**
     * Calculate the latitude range for a given center point and distance
     * 
     * @param centerLng the center longitude
     * @param centerLat the center latitude
     * @param distance  the distance in meters
     * @return double[] containing the min and max latitudes
     */
    private double[] calculateLatitudes(double centerLng, double centerLat, double distance) {
        GeodeticCalculator calculator = new GeodeticCalculator();
        calculator.setStartingGeographicPoint(centerLng, centerLat);

        calculator.setDirection(0, distance);
        double maxLat = calculator.getDestinationGeographicPoint().getY();

        calculator.setDirection(180, distance);
        double minLat = calculator.getDestinationGeographicPoint().getY();

        return new double[] { minLat, maxLat };
    }

    /**
     * Calculate the longitude range for a given center point and distance
     * 
     * @param centerLng the center longitude
     * @param centerLat the center latitude
     * @param distance  the distance in meters
     * @return double[] containing the min and max longitudes
     */
    private double[] calculateLongitudes(double centerLng, double centerLat, double distance) {
        GeodeticCalculator calculator = new GeodeticCalculator();
        calculator.setStartingGeographicPoint(centerLng, centerLat);

        calculator.setDirection(90, distance);
        double maxLng = calculator.getDestinationGeographicPoint().getX();

        calculator.setDirection(270, distance);
        double minLng = calculator.getDestinationGeographicPoint().getX();

        return new double[] { minLng, maxLng };
    }

    /**
     * Filter OdeBsmData by originIp, vehicleId, startTime, endTime, and a bounding
     * box
     * 
     * @param originIp  the origin IP
     * @param vehicleId the vehicle ID
     * @param startTime the start time
     * @param endTime   the end time
     * @param centerLng the longitude (in degrees) of the center of the bounding box
     * @param centerLat the latitude (in degrees) of the center of the bounding box
     * @param distance  the "radius" of the bounding box, in meters (total width is
     *                  2x distance)
     */
    public Page<OdeBsmData> find(String originIp, String vehicleId, Long startTime, Long endTime,
            Double centerLng, Double centerLat, Double distance, Pageable pageable) {

        Criteria criteria = new IntersectionCriteria()
                .whereOptional(ORIGIN_IP_FIELD, originIp)
                .whereOptional(VEHICLE_ID_FIELD, vehicleId)
                .withinTimeWindow(DATE_FIELD, startTime, endTime, true);

        if (centerLng != null && centerLat != null && distance != null) {
            double[] latitudes = calculateLatitudes(centerLng, centerLat, distance);
            double[] longitudes = calculateLongitudes(centerLng, centerLat, distance);
            criteria = criteria.and("payload.data.coreData.position.latitude")
                    .gte(Math.min(latitudes[0], latitudes[1])).lte(Math.max(latitudes[0], latitudes[1]))
                    .and("payload.data.coreData.position.longitude")
                    .gte(Math.min(longitudes[0], longitudes[1])).lte(Math.max(longitudes[0], longitudes[1]));
        }
        Sort sort = Sort.by(Sort.Direction.DESC, DATE_FIELD);
        List<String> excludedFields = List.of("recordGeneratedAt");

        Page<Document> aggregationResult = findDocumentsWithPagination(mongoTemplate, collectionName, pageable,
                criteria, sort, excludedFields);

        List<OdeBsmData> bsms = aggregationResult.getContent().stream()
                .map(document -> mapper.convertValue(document, OdeBsmData.class)).toList();

        return new PageImpl<>(bsms, pageable, aggregationResult.getTotalElements());
    }

    /**
     * Count filtered OdeBsmData by originIp, vehicleId, startTime, endTime, and a
     * bounding box
     * 
     * @param originIp  the origin IP
     * @param vehicleId the vehicle ID
     * @param startTime the start time
     * @param endTime   the end time
     * @param centerLng the longitude (in degrees) of the center of the bounding box
     * @param centerLat the latitude (in degrees) of the center of the bounding box
     * @param distance  the "radius" of the bounding box, in meters (total width is
     *                  2x distance)
     */
    public long count(
            String originIp,
            String vehicleId,
            Long startTime,
            Long endTime,
            Double centerLng,
            Double centerLat,
            Double distance) {

        Criteria criteria = new IntersectionCriteria()
                .whereOptional(ORIGIN_IP_FIELD, originIp)
                .whereOptional(VEHICLE_ID_FIELD, vehicleId)
                .withinTimeWindow(DATE_FIELD, startTime, endTime, true);

        if (centerLng != null && centerLat != null && distance != null) {
            double[] latitudes = calculateLatitudes(centerLng, centerLat, distance);
            double[] longitudes = calculateLongitudes(centerLng, centerLat, distance);
            criteria = criteria.and("payload.data.coreData.position.latitude")
                    .gte(Math.min(latitudes[0], latitudes[1])).lte(Math.max(latitudes[0], latitudes[1]))
                    .and("payload.data.coreData.position.longitude")
                    .gte(Math.min(longitudes[0], longitudes[1])).lte(Math.max(longitudes[0], longitudes[1]));
        }
        Query query = Query.query(criteria);
        return mongoTemplate.count(query, Map.class, collectionName);
    }

    @Override
    public void add(OdeBsmData item) {
        mongoTemplate.insert(item, collectionName);
    }

    @Override
    public List<MessageCount> getMessageCounts(String rsuIp, Long startTime, Long endTime) {
        return getMessageCountsFromPrometheus(rsuIp, startTime, endTime);
    }

    /**
     * Get message counts from Prometheus
     */
    private List<MessageCount> getMessageCountsFromPrometheus(String rsuIp, Long startTime, Long endTime) {
        List<MessageCount> counts = new ArrayList<>();

        try {
            LocalDateTime startDateTime = LocalDateTime.ofEpochSecond(startTime / 1000, 0, ZoneOffset.UTC);
            LocalDateTime endDateTime = LocalDateTime.ofEpochSecond(endTime / 1000, 0, ZoneOffset.UTC);

            // Calculate duration in hours
            long durationHours = (endTime - startTime) / (1000 * 60 * 60);
            if (durationHours == 0)
                durationHours = 1; // Minimum 1 hour

            // Query for each message type
            for (String messageType : MESSAGE_TYPES) {
                // Query for "in" counts (raw encoded messages)
                String inQuery = String.format(
                        "sum by (topic) (increase(%s{rsu_ip=\"%s\"}[%dh]))",
                        prometheusMetricName, rsuIp, durationHours);

                String inResponse = prometheusService.query(inQuery);
                processPrometheusResponse(inResponse, rsuIp, messageType, "in", startDateTime, counts);

                // Query for "out" counts (decoded messages)
                String outQuery = String.format(
                        "sum by (topic) (increase(%s{rsu_ip=\"%s\"}[%dh]))",
                        prometheusMetricName, rsuIp, durationHours);

                String outResponse = prometheusService.query(outQuery);
                processPrometheusResponse(outResponse, rsuIp, messageType, "out", startDateTime, counts);
            }

            log.debug("Retrieved {} message counts from Prometheus for RSU {}", counts.size(), rsuIp);
        } catch (Exception e) {
            log.error("Error retrieving message counts from Prometheus for RSU {}: {}", rsuIp, e.getMessage());
        }

        return counts;
    }

    /**
     * Process Prometheus response and extract counts
     */
    private void processPrometheusResponse(String response, String rsuIp, String messageType,
            String countType, LocalDateTime timestamp, List<MessageCount> counts) {
        try {
            JsonNode root = jsonMapper.readTree(response);

            if (root.path("status").asText().equals("success")) {
                JsonNode results = root.path("data").path("result");

                for (JsonNode result : results) {
                    String topic = result.path("metric").path("topic").asText();
                    double value = result.path("value").path(1).asDouble();

                    // Check if topic matches message type
                    String topicLower = topic.toLowerCase().replace("topic.ode", "");
                    String messageTypeLower = messageType.toLowerCase();

                    if (topicLower.contains(messageTypeLower)) {
                        boolean isRawEncoded = topicLower.contains("rawencoded");

                        // Match count type with topic type
                        if ((countType.equals("in") && isRawEncoded) ||
                                (countType.equals("out") && !isRawEncoded)) {

                            MessageCount count = new MessageCount();
                            count.setMessageType(messageType);
                            count.setRsuIp(rsuIp);
                            count.setTimestamp(timestamp);
                            count.setCount((long) value);
                            count.setSource("prometheus");
                            count.setCountType(countType);
                            counts.add(count);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing Prometheus response: {}", e.getMessage());
        }
    }

}

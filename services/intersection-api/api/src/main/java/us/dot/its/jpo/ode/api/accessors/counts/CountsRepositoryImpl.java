package us.dot.its.jpo.ode.api.accessors.counts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.models.CountType;
import us.dot.its.jpo.ode.api.services.PrometheusService;
import us.dot.its.jpo.ode.api.services.PostgresService;

@Slf4j
@Component
public class CountsRepositoryImpl implements CountsRepository {

    private final PrometheusService prometheusService;
    private final PostgresService postgresService;
    private final ObjectMapper jsonMapper = DateJsonMapper.getInstance()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String TOPIC_PREFIX = "topic.Ode";
    private static final String RAW_ENCODED_INDICATOR = "RawEncoded";

    /**
     * Constructs a new CountsRepositoryImpl with the required services.
     * 
     * @param prometheusService service for querying Prometheus metrics
     * @param postgresService   service for querying PostgreSQL database
     */
    public CountsRepositoryImpl(PrometheusService prometheusService,
            PostgresService postgresService) {
        this.prometheusService = prometheusService;
        this.postgresService = postgresService;
    }

    /**
     * Retrieves message counts for a specific RSU within the given time range.
     * 
     * @param rsuIp     the IP address of the RSU
     * @param message   the message type to query for (e.g., "BSM", "MAP")
     * @param startTime start time in UTC milliseconds
     * @param endTime   end time in UTC milliseconds
     * @return list of consolidated message counts by message type
     */
    @Override
    public List<MessageCount> getRsuMessageCounts(String rsuIp, String message, Long startTime, Long endTime) {
        return getMessageCountsFromPrometheus(rsuIp, message, startTime, endTime);
    }

    /**
     * Retrieves message counts from Prometheus using the
     * kafka_produced_rsu_messages_total
     * metric with optimized instant queries.
     * 
     * @param rsuIp     the IP address of the RSU
     * @param message   the message type to query for (e.g., "BSM", "MAP")
     * @param startTime start time in UTC milliseconds
     * @param endTime   end time in UTC milliseconds
     * @return list of consolidated message counts
     */
    private List<MessageCount> getMessageCountsFromPrometheus(String rsuIp, String message, Long startTime,
            Long endTime) {
        List<MessageCount> counts = new ArrayList<>();

        try {
            String inTopic = determineTopicFromMessageType(message, startTime, endTime, true);
            String outTopic = determineTopicFromMessageType(message, startTime, endTime, false);
            String road = postgresService.getRsuPrimaryRoute(rsuIp);
            Map<String, MessageCount> rsuCountsMap = new HashMap<>();

            // Query and process input topic
            if (inTopic != null) {
                queryAndProcessTopic(rsuIp, inTopic, startTime, endTime, rsuCountsMap, road, CountType.ODE_INPUT);
            }

            // Query and process output topic
            if (outTopic != null) {
                queryAndProcessTopic(rsuIp, outTopic, startTime, endTime, rsuCountsMap, road, CountType.ODE_OUTPUT);
            }

            counts.addAll(rsuCountsMap.values());

            // Create default entry if no counts found
            if (counts.isEmpty()) {
                counts.add(new MessageCount(message.toUpperCase(), rsuIp, 0L, 0L, road));
            }
        } catch (Exception e) {
            log.error("Error retrieving message counts from Prometheus for RSU {}: {}", rsuIp, e.getMessage());
            createDefaultEntry(counts, rsuIp, message);
        }

        return counts;
    }

    /**
     * Queries Prometheus for a specific topic and processes the response.
     */
    private void queryAndProcessTopic(String rsuIp, String topic, Long startTime, Long endTime,
            Map<String, MessageCount> rsuCountsMap, String road, CountType countType) {
        String response = prometheusService.getRsuMessageCounts(rsuIp, topic, startTime, endTime);
        String aggregatedResponse = prometheusService.aggregateRangeResponse(response, startTime, endTime);
        processPrometheusResponseByTopic(aggregatedResponse, topic, rsuCountsMap, rsuIp, road, countType);
    }

    /**
     * Creates a default entry with 0 counts when no data is available.
     */
    private void createDefaultEntry(List<MessageCount> counts, String rsuIp, String message) {
        if (counts.isEmpty()) {
            try {
                String road = postgresService.getRsuPrimaryRoute(rsuIp);
                counts.add(new MessageCount(message.toUpperCase(), rsuIp, 0L, 0L, road));
            } catch (Exception roadException) {
                log.error("Error getting road for RSU {}: {}", rsuIp, roadException.getMessage());
                counts.add(new MessageCount(message.toUpperCase(), rsuIp, 0L, 0L, "Unknown"));
            }
        }
    }

    /**
     * Queries Prometheus for a specific organization topic and processes the
     * response.
     */
    private void queryAndProcessOrganizationTopic(String rsuIps, String topic, Long startTime, Long endTime,
            Map<String, MessageCount> rsuCountsMap, Map<String, String> rsuIpToRoadMap, CountType countType) {
        String response = prometheusService.getOrganizationRsuCountsByTopicAccurate(rsuIps, topic, startTime, endTime);
        String aggregatedResponse = prometheusService.aggregateRangeResponse(response, startTime, endTime);
        processOrganizationResponseByTopic(aggregatedResponse, topic, rsuCountsMap, rsuIpToRoadMap, countType);
    }

    /**
     * Extracts the message type from a Kafka topic name.
     * 
     * <p>
     * Handles topic formats such as:
     * <ul>
     * <li>topic.OdeBsmJson (output count)</li>
     * <li>topic.OdeBsmRawEncodedJson (input count)</li>
     * </ul>
     * 
     * @param topic the Kafka topic name
     * @return the extracted message type (e.g., "BSM", "MAP", "SPAT") or null if
     *         not found
     */
    private String extractMessageTypeFromTopic(String topic) {
        if (topic.startsWith(TOPIC_PREFIX)) {
            String messagePart = topic.substring(TOPIC_PREFIX.length());

            String messageType = messagePart
                    .replace("RawEncoded", "")
                    .replace("Json", "");

            return messageType.isEmpty() ? null : messageType.toUpperCase();
        }

        return null;
    }

    /**
     * Determines the topic name for a given message type by querying available
     * topics.
     * 
     * @param messageType  the message type (e.g., "BSM", "MAP")
     * @param startTime    start time in UTC milliseconds
     * @param endTime      end time in UTC milliseconds
     * @param isRawEncoded whether to look for RawEncoded topics (true) or regular
     *                     topics (false)
     * @return the topic name that matches the message type and RawEncoded filter,
     *         or null if not found
     */
    private String determineTopicFromMessageType(String messageType, Long startTime, Long endTime,
            boolean isRawEncoded) {
        try {
            String response = prometheusService.getAvailableTopicCountsAccurate(startTime, endTime);
            String aggregatedResponse = prometheusService.aggregateRangeResponse(response, startTime, endTime);

            JsonNode root = jsonMapper.readTree(aggregatedResponse);
            if (root.path("status").asText().equals("success")) {
                JsonNode results = root.path("data").path("result");

                for (JsonNode result : results) {
                    String topic = result.path("metric").path("topic").asText();
                    String extractedMessageType = extractMessageTypeFromTopic(topic);
                    boolean topicHasRawEncoded = topic.contains(RAW_ENCODED_INDICATOR);

                    if (messageType.equalsIgnoreCase(extractedMessageType) && topicHasRawEncoded == isRawEncoded) {
                        return topic;
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.error("Error determining topic for message type {} (RawEncoded: {}): {}", messageType, isRawEncoded,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Processes Prometheus response for a specific topic and RSU.
     * Updates consolidated MessageCount objects for the RSU.
     * 
     * @param response     the raw Prometheus response JSON
     * @param topic        the Kafka topic being processed
     * @param rsuCountsMap map of consolidated counts by message type
     * @param rsuIp        the RSU IP address
     * @param road         the primary road for the RSU
     * @param countType    the type of count being processed (input or output)
     */
    private void processPrometheusResponseByTopic(String response, String topic,
            Map<String, MessageCount> rsuCountsMap, String rsuIp, String road, CountType countType) {
        try {
            JsonNode root = jsonMapper.readTree(response);
            double value = 0.0;

            if (root.path("status").asText().equals("success")) {
                JsonNode results = root.path("data").path("result");

                for (JsonNode result : results) {
                    String resultTopic = result.path("metric").path("topic").asText();
                    if (topic.equals(resultTopic)) {
                        value = result.path("value").path(1).asDouble();
                        break;
                    }
                }
            }

            String messageType = extractMessageTypeFromTopic(topic);
            if (messageType != null && value > 0) {
                MessageCount rsuCounts = rsuCountsMap.get(messageType);
                if (rsuCounts == null) {
                    rsuCounts = new MessageCount(messageType, rsuIp, 0L, 0L, road);
                    rsuCountsMap.put(messageType, rsuCounts);
                }

                if (countType == CountType.ODE_INPUT) {
                    rsuCounts.setOdeInputCount(rsuCounts.getOdeInputCount() + (long) value);
                } else if (countType == CountType.ODE_OUTPUT) {
                    rsuCounts.setOdeOutputCount(rsuCounts.getOdeOutputCount() + (long) value);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Prometheus response by topic for RSU {} topic {}: {}", rsuIp, topic,
                    e.getMessage());
        }
    }

    /**
     * Processes organization-wide Prometheus response for a specific topic.
     * Updates consolidated MessageCount objects for all RSUs in the organization.
     * 
     * @param response       the raw Prometheus response JSON
     * @param topic          the Kafka topic being processed
     * @param rsuCountsMaps  map of consolidated counts by RSU and message type
     * @param rsuIpToRoadMap mapping of RSU IPs to their primary roads
     * @param countType      the type of count being processed (input or output)
     */
    private void processOrganizationResponseByTopic(String response, String topic,
            Map<String, MessageCount> rsuCountsMaps, Map<String, String> rsuIpToRoadMap, CountType countType) {
        try {
            JsonNode root = jsonMapper.readTree(response);
            Map<String, Long> rsuCounts = new HashMap<>();

            if (root.path("status").asText().equals("success")) {
                JsonNode results = root.path("data").path("result");

                for (JsonNode result : results) {
                    String rsuIp = result.path("metric").path("rsu_ip").asText();
                    String resultTopic = result.path("metric").path("topic").asText();
                    double value = result.path("value").path(1).asDouble();

                    if (topic.equals(resultTopic)) {
                        rsuCounts.put(rsuIp, (long) value);
                    }
                }
            }

            String messageType = extractMessageTypeFromTopic(topic);

            for (Map.Entry<String, String> entry : rsuIpToRoadMap.entrySet()) {
                String rsuIp = entry.getKey();
                String road = entry.getValue();
                Long count = rsuCounts.getOrDefault(rsuIp, 0L);

                String key = rsuIp + "_" + messageType;
                MessageCount rsuCountsMap = rsuCountsMaps.get(key);
                if (rsuCountsMap == null) {
                    rsuCountsMap = new MessageCount(messageType, rsuIp, 0L, 0L, road);
                    rsuCountsMaps.put(key, rsuCountsMap);
                }

                if (countType == CountType.ODE_INPUT) {
                    rsuCountsMap.setOdeInputCount(rsuCountsMap.getOdeInputCount() + count);
                } else if (countType == CountType.ODE_OUTPUT) {
                    rsuCountsMap.setOdeOutputCount(rsuCountsMap.getOdeOutputCount() + count);
                }
            }
        } catch (Exception e) {
            log.error("Error processing optimized organization Prometheus response by topic: {}", e.getMessage());
        }
    }

    /**
     * Retrieves message counts for all RSUs in an organization for a specific
     * message type.
     * 
     * <p>
     * This method queries both input (RawEncoded) and output topics for the
     * specified
     * message type across all RSUs in the organization and consolidates the
     * results.
     * </p>
     * 
     * @param organization the organization name
     * @param messageType  the message type to query (e.g., "BSM", "MAP")
     * @param startTime    start time in UTC milliseconds
     * @param endTime      end time in UTC milliseconds
     * @return list of consolidated message counts for all RSUs in the organization
     */
    @Override
    public List<MessageCount> getRsuOrganizationMessageCounts(String organization, String messageType, Long startTime,
            Long endTime) {
        List<MessageCount> allCounts = new ArrayList<>();
        Map<String, String> rsuIpToRoadMap = new HashMap<>();

        try {
            rsuIpToRoadMap = postgresService.getOrganizationRsuIps(organization);

            if (rsuIpToRoadMap.isEmpty()) {
                return allCounts;
            }

            String inTopic = determineTopicFromMessageType(messageType, startTime, endTime, true);
            String outTopic = determineTopicFromMessageType(messageType, startTime, endTime, false);

            String rsuIps = String.join("|", rsuIpToRoadMap.keySet());
            Map<String, MessageCount> rsuCountsMap = new HashMap<>();

            if (inTopic != null) {
                queryAndProcessOrganizationTopic(rsuIps, inTopic, startTime, endTime, rsuCountsMap, rsuIpToRoadMap,
                        CountType.ODE_INPUT);
            }

            if (outTopic != null) {
                queryAndProcessOrganizationTopic(rsuIps, outTopic, startTime, endTime, rsuCountsMap, rsuIpToRoadMap,
                        CountType.ODE_OUTPUT);
            }

            allCounts.addAll(rsuCountsMap.values());

            // If no counts were found for the specific message type, create entries with 0
            // counts for all RSUs
            if (allCounts.isEmpty()) {
                for (Map.Entry<String, String> entry : rsuIpToRoadMap.entrySet()) {
                    String rsuIp = entry.getKey();
                    String road = entry.getValue();
                    allCounts.add(new MessageCount(messageType.toUpperCase(), rsuIp, 0L, 0L, road));
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving organization message counts for {}: {}", organization, e.getMessage());
        }

        return allCounts;
    }

}

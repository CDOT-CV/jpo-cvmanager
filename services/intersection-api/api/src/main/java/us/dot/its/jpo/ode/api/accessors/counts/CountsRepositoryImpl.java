package us.dot.its.jpo.ode.api.accessors.counts;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.ode.api.accessors.PageableQuery;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.models.CountType;
import us.dot.its.jpo.ode.api.services.PrometheusService;
import us.dot.its.jpo.ode.api.services.PostgresService;
import java.util.HashMap;

@Slf4j
@Component
public class CountsRepositoryImpl implements CountsRepository, PageableQuery {

    private final PrometheusService prometheusService;
    private final PostgresService postgresService;
    private final ObjectMapper jsonMapper = DateJsonMapper.getInstance()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String TOPIC_PREFIX = "topic.Ode";
    private static final String RAW_ENCODED_INDICATOR = "RawEncoded";

    public CountsRepositoryImpl(PrometheusService prometheusService,
            PostgresService postgresService) {
        this.prometheusService = prometheusService;
        this.postgresService = postgresService;
    }

    @Override
    public List<MessageCount> getRsuMessageCounts(String rsuIp, Long startTime, Long endTime) {
        return getMessageCountsFromPrometheus(rsuIp, startTime, endTime);
    }

    /**
     * Get message counts from Prometheus using the
     * kafka_produced_rsu_messages_total metric with 5-minute grouping
     */
    private List<MessageCount> getMessageCountsFromPrometheus(String rsuIp, Long startTime, Long endTime) {
        List<MessageCount> counts = new ArrayList<>();

        try {
            LocalDateTime startDateTime = timestampToLocalDateTime(startTime);
            LocalDateTime endDateTime = timestampToLocalDateTime(endTime);

            // Use optimized instant query instead of range query
            String response = prometheusService.getRsuMessageCounts(rsuIp, startTime, endTime);
            log.debug("Prometheus response: {}", response);
            processPrometheusResponse(response, rsuIp, startDateTime, counts);

            log.debug("Retrieved {} message counts from Prometheus for RSU {}", counts.size(), rsuIp);
        } catch (Exception e) {
            log.error("Error retrieving message counts from Prometheus for RSU {}: {}", rsuIp, e.getMessage());
        }

        return counts;
    }

    /**
     * Process optimized Prometheus response (instant query with proper time range)
     * This eliminates the need for client-side time filtering
     * Returns consolidated message counts with both "in" and "out" counts in a
     * single object
     */
    private void processPrometheusResponse(String response, String rsuIp, LocalDateTime timestamp,
            List<MessageCount> counts) {
        try {
            JsonNode root = jsonMapper.readTree(response);

            if (root.path("status").asText().equals("success")) {
                JsonNode results = root.path("data").path("result");

                // Get road information for this RSU
                String road = postgresService.getRsuPrimaryRoute(rsuIp);

                // Map to consolidate counts by message type
                Map<String, MessageCount> rsuCountsMaps = new HashMap<>();

                for (JsonNode result : results) {
                    String topic = result.path("metric").path("topic").asText();
                    double value = result.path("value").path(1).asDouble();

                    // Extract message type from topic
                    String messageType = extractMessageTypeFromTopic(topic);
                    if (messageType != null && value > 0) {
                        CountType countType = determineCountType(topic);

                        // Get or create consolidated count object for this message type
                        MessageCount rsuCountsMap = rsuCountsMaps.get(messageType);
                        if (rsuCountsMap == null) {
                            rsuCountsMap = new MessageCount(messageType, rsuIp, 0L, 0L, road);
                            rsuCountsMaps.put(messageType, rsuCountsMap);
                        }

                        // Add the count to the appropriate field
                        if (countType == CountType.ODE_INPUT) {
                            rsuCountsMap.setOdeInputCount(rsuCountsMap.getOdeInputCount() + (long) value);
                        } else if (countType == CountType.ODE_OUTPUT) {
                            rsuCountsMap.setOdeOutputCount(rsuCountsMap.getOdeOutputCount() + (long) value);
                        }
                    }
                }

                // Add all consolidated counts to the result list
                counts.addAll(rsuCountsMaps.values());
            }
        } catch (Exception e) {
            log.error("Error processing optimized Prometheus response: {}", e.getMessage());
        }
    }

    /**
     * Extract message type from topic name
     * Handles formats like:
     * - topic.OdeBsmJson (out count)
     * - topic.OdeBsmRawEncodedJson (in count)
     */
    private String extractMessageTypeFromTopic(String topic) {
        if (topic.startsWith(TOPIC_PREFIX)) {
            // Remove "topic.Ode" prefix
            String messagePart = topic.substring(TOPIC_PREFIX.length());

            // Remove "Json" suffix and any "RawEncoded" indicator
            String messageType = messagePart
                    .replace("RawEncoded", "")
                    .replace("Json", "");

            // Return the extracted message type (e.g., "Bsm", "Map", "Spat", etc.)
            return messageType.isEmpty() ? null : messageType.toUpperCase();
        }

        return null;
    }

    /**
     * Convert milliseconds timestamp to LocalDateTime
     */
    private LocalDateTime timestampToLocalDateTime(Long timestamp) {
        return LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC);
    }

    /**
     * Determine count type based on topic name
     */
    private CountType determineCountType(String topic) {
        return topic.contains(RAW_ENCODED_INDICATOR) ? CountType.ODE_INPUT : CountType.ODE_OUTPUT;
    }

    /**
     * Determine topic name from message type by querying available topics
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
            // Query Prometheus to get all available topics for the sample RSU
            String response = prometheusService.getAvailableTopicCounts(startTime, endTime);

            JsonNode root = jsonMapper.readTree(response);
            if (root.path("status").asText().equals("success")) {
                JsonNode results = root.path("data").path("result");

                // Look for topics that match the message type and RawEncoded filter
                for (JsonNode result : results) {
                    String topic = result.path("metric").path("topic").asText();
                    String extractedMessageType = extractMessageTypeFromTopic(topic);
                    boolean topicHasRawEncoded = topic.contains(RAW_ENCODED_INDICATOR);

                    if (messageType.equalsIgnoreCase(extractedMessageType) && topicHasRawEncoded == isRawEncoded) {
                        log.debug("Found topic {} for message type {} (RawEncoded: {})", topic, messageType,
                                isRawEncoded);
                        return topic;
                    }
                }
            }

            log.warn("No topic found for message type {} (RawEncoded: {})", messageType, isRawEncoded);
            return null;
        } catch (Exception e) {
            log.error("Error determining topic for message type {} (RawEncoded: {}): {}", messageType, isRawEncoded,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Process optimized organization Prometheus response by topic (instant query)
     * This eliminates the need for client-side time filtering
     * Returns consolidated MessageCount objects for the specified topic
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

                    // Only process results for the specified topic
                    if (topic.equals(resultTopic)) {
                        rsuCounts.put(rsuIp, (long) value);
                    }
                }
            }

            // Update consolidated MessageCount objects for all RSUs in the organization
            String messageType = extractMessageTypeFromTopic(topic);

            for (Map.Entry<String, String> entry : rsuIpToRoadMap.entrySet()) {
                String rsuIp = entry.getKey();
                String road = entry.getValue();
                Long count = rsuCounts.getOrDefault(rsuIp, 0L);

                // Get or create consolidated count object for this RSU and message type
                String key = rsuIp + "_" + messageType;
                MessageCount rsuCountsMap = rsuCountsMaps.get(key);
                if (rsuCountsMap == null) {
                    rsuCountsMap = new MessageCount(messageType, rsuIp, 0L, 0L, road);
                    rsuCountsMaps.put(key, rsuCountsMap);
                }

                // Add the count to the appropriate field
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

    @Override
    public List<MessageCount> getRsuOrganizationMessageCounts(String organization, String messageType, Long startTime,
            Long endTime) {
        List<MessageCount> allCounts = new ArrayList<>();

        try {
            // Get RSUs for the organization
            Map<String, String> rsuIpToRoadMap = postgresService.getOrganizationRsuIps(organization);

            if (rsuIpToRoadMap.isEmpty()) {
                log.warn("No RSUs found for organization {}", organization);
                return allCounts;
            }

            // Step 1: Query Prometheus to get available topics and determine both "in" and
            // "out" topic names
            String inTopic = determineTopicFromMessageType(messageType, startTime, endTime, true); // RawEncoded
            String outTopic = determineTopicFromMessageType(messageType, startTime, endTime, false); // Regular

            // Step 2: Query for both "in" and "out" topics across all RSUs in the
            // organization
            String rsuIps = String.join("|", rsuIpToRoadMap.keySet());

            // Map to consolidate counts by RSU and message type
            Map<String, MessageCount> rsuCountsMap = new HashMap<>();

            // Query for "in" counts (RawEncoded)
            if (inTopic != null) {
                String inResponse = prometheusService.getOrganizationRsuCountsByTopic(rsuIps, inTopic, startTime,
                        endTime);
                processOrganizationResponseByTopic(inResponse, inTopic, rsuCountsMap, rsuIpToRoadMap,
                        CountType.ODE_INPUT);
            }

            // Query for "out" counts (Regular)
            if (outTopic != null) {
                String outResponse = prometheusService.getOrganizationRsuCountsByTopic(rsuIps, outTopic, startTime,
                        endTime);
                processOrganizationResponseByTopic(outResponse, outTopic, rsuCountsMap, rsuIpToRoadMap,
                        CountType.ODE_OUTPUT);
            }

            // Add all consolidated counts to the result list
            allCounts.addAll(rsuCountsMap.values());

            log.debug(
                    "Retrieved {} message counts for organization {} across {} RSUs for message type {} (in: {}, out: {})",
                    allCounts.size(), organization, rsuIpToRoadMap.size(), messageType, inTopic, outTopic);
        } catch (Exception e) {
            log.error("Error retrieving organization message counts for {}: {}", organization, e.getMessage());
        }

        return allCounts;
    }

}

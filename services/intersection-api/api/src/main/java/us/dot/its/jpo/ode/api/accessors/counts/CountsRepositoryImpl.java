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
     * @param startTime start time in UTC milliseconds
     * @param endTime   end time in UTC milliseconds
     * @return list of consolidated message counts by message type
     */
    @Override
    public List<MessageCount> getRsuMessageCounts(String rsuIp, Long startTime, Long endTime) {
        return getMessageCountsFromPrometheus(rsuIp, startTime, endTime);
    }

    /**
     * Retrieves message counts from Prometheus using the
     * kafka_produced_rsu_messages_total
     * metric with optimized instant queries.
     * 
     * @param rsuIp     the IP address of the RSU
     * @param startTime start time in UTC milliseconds
     * @param endTime   end time in UTC milliseconds
     * @return list of consolidated message counts
     */
    private List<MessageCount> getMessageCountsFromPrometheus(String rsuIp, Long startTime, Long endTime) {
        List<MessageCount> counts = new ArrayList<>();

        try {
            String response = prometheusService.getRsuMessageCounts(rsuIp, startTime, endTime);
            log.debug("Prometheus response: {}", response);
            processPrometheusResponse(response, rsuIp, counts);

            log.debug("Retrieved {} message counts from Prometheus for RSU {}", counts.size(), rsuIp);
        } catch (Exception e) {
            log.error("Error retrieving message counts from Prometheus for RSU {}: {}", rsuIp, e.getMessage());
        }

        return counts;
    }

    /**
     * Processes the Prometheus response and consolidates message counts by message
     * type.
     * Creates consolidated MessageCount objects with both "in" and "out" counts.
     * 
     * @param response the raw Prometheus response JSON
     * @param rsuIp    the IP address of the RSU
     * @param counts   the list to populate with consolidated counts
     */
    private void processPrometheusResponse(String response, String rsuIp,
            List<MessageCount> counts) {
        try {
            JsonNode root = jsonMapper.readTree(response);

            if (root.path("status").asText().equals("success")) {
                JsonNode results = root.path("data").path("result");

                String road = postgresService.getRsuPrimaryRoute(rsuIp);
                Map<String, MessageCount> rsuCountsMaps = new HashMap<>();

                for (JsonNode result : results) {
                    String topic = result.path("metric").path("topic").asText();
                    double value = result.path("value").path(1).asDouble();

                    String messageType = extractMessageTypeFromTopic(topic);
                    if (messageType != null && value > 0) {
                        CountType countType = determineCountType(topic);

                        MessageCount rsuCountsMap = rsuCountsMaps.get(messageType);
                        if (rsuCountsMap == null) {
                            rsuCountsMap = new MessageCount(messageType, rsuIp, 0L, 0L, road);
                            rsuCountsMaps.put(messageType, rsuCountsMap);
                        }

                        if (countType == CountType.ODE_INPUT) {
                            rsuCountsMap.setOdeInputCount(rsuCountsMap.getOdeInputCount() + (long) value);
                        } else if (countType == CountType.ODE_OUTPUT) {
                            rsuCountsMap.setOdeOutputCount(rsuCountsMap.getOdeOutputCount() + (long) value);
                        }
                    }
                }

                counts.addAll(rsuCountsMaps.values());
            }
        } catch (Exception e) {
            log.error("Error processing optimized Prometheus response: {}", e.getMessage());
        }
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
     * Determines the count type based on the topic name.
     * 
     * @param topic the Kafka topic name
     * @return ODE_INPUT for RawEncoded topics, ODE_OUTPUT for regular topics
     */
    private CountType determineCountType(String topic) {
        return topic.contains(RAW_ENCODED_INDICATOR) ? CountType.ODE_INPUT : CountType.ODE_OUTPUT;
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
            String response = prometheusService.getAvailableTopicCounts(startTime, endTime);

            JsonNode root = jsonMapper.readTree(response);
            if (root.path("status").asText().equals("success")) {
                JsonNode results = root.path("data").path("result");

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

        try {
            Map<String, String> rsuIpToRoadMap = postgresService.getOrganizationRsuIps(organization);

            if (rsuIpToRoadMap.isEmpty()) {
                log.warn("No RSUs found for organization {}", organization);
                return allCounts;
            }

            String inTopic = determineTopicFromMessageType(messageType, startTime, endTime, true);
            String outTopic = determineTopicFromMessageType(messageType, startTime, endTime, false);

            String rsuIps = String.join("|", rsuIpToRoadMap.keySet());
            Map<String, MessageCount> rsuCountsMap = new HashMap<>();

            if (inTopic != null) {
                String inResponse = prometheusService.getOrganizationRsuCountsByTopic(rsuIps, inTopic, startTime,
                        endTime);
                processOrganizationResponseByTopic(inResponse, inTopic, rsuCountsMap, rsuIpToRoadMap,
                        CountType.ODE_INPUT);
            }

            if (outTopic != null) {
                String outResponse = prometheusService.getOrganizationRsuCountsByTopic(rsuIps, outTopic, startTime,
                        endTime);
                processOrganizationResponseByTopic(outResponse, outTopic, rsuCountsMap, rsuIpToRoadMap,
                        CountType.ODE_OUTPUT);
            }

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

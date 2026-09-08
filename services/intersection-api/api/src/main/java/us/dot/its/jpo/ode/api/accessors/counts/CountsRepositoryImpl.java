package us.dot.its.jpo.ode.api.accessors.counts;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.ode.api.models.CountType;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.models.PrometheusResponse;
import us.dot.its.jpo.ode.api.models.PrometheusResponse.PrometheusResult;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.services.PrometheusService;

@Slf4j
@Component
public class CountsRepositoryImpl implements CountsRepository {

    private final PrometheusService prometheusService;
    private final RsuRepository rsuRepository;
    private final ObjectMapper jsonMapper = DateJsonMapper.getInstance()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String TOPIC_PREFIX = "topic.Ode";
    private static final String RAW_ENCODED_INDICATOR = "RawEncoded";
    private static final String JSON_SUFFIX = "Json";
    private static final String METRIC_LABEL_TOPIC = "topic";
    private static final String METRIC_LABEL_RSU_IP = "rsu_ip";

    public CountsRepositoryImpl(PrometheusService prometheusService, RsuRepository rsuRepository) {
        this.prometheusService = prometheusService;
        this.rsuRepository = rsuRepository;
    }

    @Override
    public List<MessageCount> getRsuMessageCounts(String rsuIp, String message, Long startTime, Long endTime) {
        return getMessageCountsFromPrometheus(rsuIp, message, startTime, endTime);
    }

    private List<MessageCount> getMessageCountsFromPrometheus(String rsuIp, String message, Long startTime,
            Long endTime) {
        List<MessageCount> counts = new ArrayList<>();

        try {
            String inTopic = determineTopicFromMessageType(message, startTime, endTime, true);
            String outTopic = determineTopicFromMessageType(message, startTime, endTime, false);
            String road = getRsuPrimaryRoute(rsuIp);
            Map<String, MessageCount> rsuCountsMap = new HashMap<>();

            if (inTopic != null) {
                queryAndProcessTopic(rsuIp, inTopic, startTime, endTime, rsuCountsMap, road, CountType.ODE_INPUT);
            }

            if (outTopic != null) {
                queryAndProcessTopic(rsuIp, outTopic, startTime, endTime, rsuCountsMap, road, CountType.ODE_OUTPUT);
            }

            counts.addAll(rsuCountsMap.values());

            if (counts.isEmpty()) {
                counts.add(new MessageCount(message.toUpperCase(), rsuIp, 0L, 0L, road));
            }
        } catch (Exception e) {
            log.error("Error retrieving message counts from Prometheus for RSU {}: {}", rsuIp, e.getMessage());
            createDefaultEntry(counts, rsuIp, message);
        }

        return counts;
    }

    private void queryAndProcessTopic(String rsuIp, String topic, Long startTime, Long endTime,
            Map<String, MessageCount> rsuCountsMap, String road, CountType countType) {
        String response = prometheusService.getRsuMessageCounts(rsuIp, topic, startTime, endTime);
        processPrometheusResponseByTopic(response, topic, rsuCountsMap, rsuIp, road, countType);
    }

    private void createDefaultEntry(List<MessageCount> counts, String rsuIp, String message) {
        if (counts.isEmpty()) {
            try {
                String road = getRsuPrimaryRoute(rsuIp);
                counts.add(new MessageCount(message.toUpperCase(), rsuIp, 0L, 0L, road));
            } catch (Exception roadException) {
                log.error("Error getting road for RSU {}: {}", rsuIp, roadException.getMessage());
                counts.add(new MessageCount(message.toUpperCase(), rsuIp, 0L, 0L, "Unknown"));
            }
        }
    }

    private void queryAndProcessOrganizationTopic(String rsuIps, String topic, Long startTime, Long endTime,
            Map<String, MessageCount> rsuCountsMap, Map<String, String> rsuIpToRoadMap, CountType countType) {
        String response = prometheusService.getOrganizationRsuCountsByTopic(rsuIps, topic, startTime, endTime);
        processOrganizationResponseByTopic(response, topic, rsuCountsMap, rsuIpToRoadMap, countType);
    }

    private String extractMessageTypeFromTopic(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) {
            return null;
        }

        String messageType = topic.substring(TOPIC_PREFIX.length())
                .replace(RAW_ENCODED_INDICATOR, "")
                .replace(JSON_SUFFIX, "");

        return messageType.isEmpty() ? null : messageType.toUpperCase();
    }

    private String determineTopicFromMessageType(String messageType, Long startTime, Long endTime,
            boolean isRawEncoded) {
        try {
            String response = prometheusService.getAvailableTopicCounts(startTime, endTime);
            for (PrometheusResult result : prometheusResults(response)) {
                String topic = result.getMetricLabel(METRIC_LABEL_TOPIC);
                if (topicMatches(topic, messageType, isRawEncoded)) {
                    return topic;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error determining topic for message type {} (RawEncoded: {}): {}", messageType, isRawEncoded,
                    e.getMessage());
            return null;
        }
    }

    private boolean topicMatches(String topic, String messageType, boolean isRawEncoded) {
        if (topic == null) {
            return false;
        }
        return messageType.equalsIgnoreCase(extractMessageTypeFromTopic(topic))
                && topic.contains(RAW_ENCODED_INDICATOR) == isRawEncoded;
    }

    private List<PrometheusResult> prometheusResults(String response) throws JsonProcessingException {
        PrometheusResponse prometheusResponse = jsonMapper.readValue(response, PrometheusResponse.class);
        if (!prometheusResponse.isSuccess()) {
            return List.of();
        }
        return prometheusResponse.getResults();
    }

    private void processPrometheusResponseByTopic(String response, String topic,
            Map<String, MessageCount> rsuCountsMap, String rsuIp, String road, CountType countType) {
        try {
            double value = 0.0;
            for (PrometheusResult result : prometheusResults(response)) {
                if (topic.equals(result.getMetricLabel(METRIC_LABEL_TOPIC))) {
                    value = result.getInstantValue();
                    break;
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

    private void processOrganizationResponseByTopic(String response, String topic,
            Map<String, MessageCount> rsuCountsMaps, Map<String, String> rsuIpToRoadMap, CountType countType) {
        try {
            Map<String, Long> rsuCounts = new HashMap<>();
            for (PrometheusResult result : prometheusResults(response)) {
                if (topic.equals(result.getMetricLabel(METRIC_LABEL_TOPIC))) {
                    rsuCounts.put(result.getMetricLabel(METRIC_LABEL_RSU_IP), (long) result.getInstantValue());
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

    @Override
    public List<MessageCount> getRsuOrganizationMessageCounts(String organization, String messageType, Long startTime,
            Long endTime) {
        List<MessageCount> allCounts = new ArrayList<>();
        Map<String, String> rsuIpToRoadMap = new HashMap<>();

        try {
            rsuIpToRoadMap = getOrganizationRsuIps(organization);

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

    private String getRsuPrimaryRoute(String rsuIp) {
        try {
            Rsu rsu = rsuRepository.findByIpv4Address(InetAddress.getByName(rsuIp));
            if (rsu != null && rsu.getPrimaryRoute() != null) {
                return rsu.getPrimaryRoute();
            }
        } catch (UnknownHostException e) {
            log.warn("Invalid RSU IP address {}: {}", rsuIp, e.getMessage());
        }
        return "Unknown";
    }

    Map<String, String> getOrganizationRsuIps(String organization) {
        Map<String, String> rsuIpToRoadMap = new HashMap<>();
        List<Rsu> rsus = rsuRepository.findAllByOrganization(organization, null, Pageable.unpaged()).getContent();
        for (Rsu rsu : rsus) {
            if (rsu.getIpv4Address() != null) {
                String road = rsu.getPrimaryRoute() != null ? rsu.getPrimaryRoute() : "Unknown";
                rsuIpToRoadMap.put(rsu.getIpv4Address().getHostAddress(), road);
            }
        }
        return rsuIpToRoadMap;
    }

}

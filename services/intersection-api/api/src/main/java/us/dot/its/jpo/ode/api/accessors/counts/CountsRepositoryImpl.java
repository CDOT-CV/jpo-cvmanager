package us.dot.its.jpo.ode.api.accessors.counts;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

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
    public List<MessageCount> getRsuMessageCounts(String rsuIp, List<String> messages, Long startTime, Long endTime) {
        List<String> messageTypes = MessageTypeParams.parse(messages);
        if (messageTypes.isEmpty()) {
            return List.of();
        }

        String road = getRsuPrimaryRoute(rsuIp);
        Map<String, MessageCount> rsuCountsMap = new HashMap<>();

        try {
            String response = prometheusService.getRsuMessageCounts(rsuIp, startTime, endTime);
            applyTopicResults(prometheusResults(response), messageTypes::contains, rsuIp, road, rsuCountsMap, null);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving message counts from Prometheus for RSU {}: {}", rsuIp, e.getMessage());
        }

        List<MessageCount> counts = new ArrayList<>();
        for (String messageType : messageTypes) {
            MessageCount existing = rsuCountsMap.get(messageType);
            counts.add(existing != null ? existing : new MessageCount(messageType, rsuIp, 0L, 0L, road));
        }
        return counts;
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

            String requestedType = messageType == null ? "" : messageType.toUpperCase();
            Map<String, MessageCount> rsuCountsMap = new HashMap<>();

            try {
                String rsuIps = String.join("|", rsuIpToRoadMap.keySet());
                String response = prometheusService.getOrganizationRsuCounts(rsuIps,
                        topicRegexForMessageType(requestedType), startTime, endTime);
                applyTopicResults(prometheusResults(response), requestedType::equals, null, null, rsuCountsMap,
                        rsuIpToRoadMap);
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error querying Prometheus for organization {}: {}", organization, e.getMessage());
            }

            for (Map.Entry<String, String> entry : rsuIpToRoadMap.entrySet()) {
                String key = entry.getKey() + "_" + requestedType;
                rsuCountsMap.putIfAbsent(key,
                        new MessageCount(requestedType, entry.getKey(), 0L, 0L, entry.getValue()));
            }
            allCounts.addAll(rsuCountsMap.values());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving organization message counts for {}: {}", organization, e.getMessage());
        }

        return allCounts;
    }

    private void applyTopicResults(List<PrometheusResult> results, Predicate<String> messageTypeFilter,
            String fallbackRsuIp, String fallbackRoad, Map<String, MessageCount> rsuCountsMap,
            Map<String, String> rsuIpToRoadMap) {
        for (PrometheusResult result : results) {
            String topic = result.getMetricLabel(METRIC_LABEL_TOPIC);
            String messageType = extractMessageTypeFromTopic(topic);
            if (messageType == null || !messageTypeFilter.test(messageType)) {
                continue;
            }

            long value = (long) result.getInstantValue();
            if (value <= 0) {
                continue;
            }

            String rsuIp = result.getMetricLabel(METRIC_LABEL_RSU_IP);
            if (rsuIp == null || rsuIp.isBlank()) {
                rsuIp = fallbackRsuIp;
            }
            if (rsuIp == null) {
                continue;
            }

            String road = fallbackRoad;
            if (rsuIpToRoadMap != null) {
                road = rsuIpToRoadMap.get(rsuIp);
                if (road == null) {
                    continue;
                }
            }

            String key = rsuIpToRoadMap == null ? messageType : rsuIp + "_" + messageType;
            MessageCount counts = rsuCountsMap.get(key);
            if (counts == null) {
                counts = new MessageCount(messageType, rsuIp, 0L, 0L, road);
                rsuCountsMap.put(key, counts);
            }

            CountType countType = topic.contains(RAW_ENCODED_INDICATOR) ? CountType.ODE_INPUT : CountType.ODE_OUTPUT;
            if (countType == CountType.ODE_INPUT) {
                counts.setOdeInputCount(counts.getOdeInputCount() + value);
            } else {
                counts.setOdeOutputCount(counts.getOdeOutputCount() + value);
            }
        }
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

    /**
     * PromQL {@code topic=~} pattern for one message type. Matches both ODE naming
     * variants ({@code topic.OdeBsmJson} and {@code topic.OdeRawEncodedBSMJson}).
     */
    static String topicRegexForMessageType(String messageType) {
        if (messageType == null || messageType.isBlank()) {
            return null;
        }
        StringBuilder charClasses = new StringBuilder(messageType.length() * 4);
        for (int i = 0; i < messageType.length(); i++) {
            char c = messageType.charAt(i);
            if (Character.isLetter(c)) {
                charClasses.append('[')
                        .append(Character.toUpperCase(c))
                        .append(Character.toLowerCase(c))
                        .append(']');
            } else if ("\\.^$|?*+()[]{}".indexOf(c) >= 0) {
                charClasses.append('\\').append(c);
            } else {
                charClasses.append(c);
            }
        }
        return "topic\\.Ode.*" + charClasses + ".*Json";
    }

    private List<PrometheusResult> prometheusResults(String response) throws JsonProcessingException {
        PrometheusResponse prometheusResponse = jsonMapper.readValue(response, PrometheusResponse.class);
        if (!prometheusResponse.isSuccess()) {
            return List.of();
        }
        return prometheusResponse.getResults();
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

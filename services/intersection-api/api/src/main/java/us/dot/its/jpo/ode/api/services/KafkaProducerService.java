package us.dot.its.jpo.ode.api.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.geojsonconverter.partitioner.RsuIntersectionKey;
import us.dot.its.jpo.ode.api.models.snmp.RsuState;

@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(String topic, String key, String message) {
        kafkaTemplate.send(topic, key, message);
    }

    public void sendRsuStatus(String topic, RsuIntersectionKey key, RsuState message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            String jsonKey = objectMapper.writeValueAsString(key);
            kafkaTemplate.send(topic, jsonKey, jsonMessage);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize message" + message);
        }

    }
}
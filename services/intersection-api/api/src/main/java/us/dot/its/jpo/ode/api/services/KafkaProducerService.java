package us.dot.its.jpo.ode.api.services;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.geojsonconverter.partitioner.RsuIntersectionKey;
import us.dot.its.jpo.ode.api.models.snmp.RsuState;
import us.dot.its.jpo.ode.api.tasks.RsuMonitoringTask;

@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(String topic, String key, String message) {
        kafkaTemplate.send(topic, key, message);
        logger.info("Message sent to topic %s with key %s: %s%n", topic, key, message);
    }

    public void sendRsuStatus(String topic, RsuIntersectionKey key, RsuState message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            String jsonKey = objectMapper.writeValueAsString(key);
            kafkaTemplate.send(topic, jsonKey, jsonMessage);
            logger.info("Message sent to topic %s with key %s: %s%n", topic, key, message);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize message" + message);
        }

    }
}
package us.dot.its.jpo.ode.api.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.ConflictMonitorApiProperties;

@Configuration
@Slf4j
public class KafkaProducerConfig {

    ConflictMonitorApiProperties properties;

    @Autowired
    public KafkaProducerConfig(
            ConflictMonitorApiProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafkaBrokers());

        if (properties.getConfluentCloudEnabled()) {
            config.put("ssl.endpoint.identification.algorithm", "https");
            config.put("security.protocol", "SASL_SSL");
            config.put("sasl.mechanism", "PLAIN");

            if (properties.getConfluentKey() != null && properties.getConfluentSecret() != null) {
                String auth = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                        "username=\"" + properties.getConfluentKey() + "\" " +
                        "password=\"" + properties.getConfluentSecret() + "\";";
                config.put("sasl.jaas.config", auth);
            } else {
                log.error(
                        "Environment variables CONFLUENT_KEY and CONFLUENT_SECRET are not set. Set these in the .env file to use Confluent Cloud");
            }
        }
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
package us.dot.its.jpo.ode.api.decoderTests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.ode.api.asn1.TimDecoder;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
public class TimDecoderTests {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }


    private final TimDecoder timDecoder;

    private String odeTimDecodedXmlReference = "";
    private String odeTimDecodedJsonReference = "";

    ObjectMapper objectMapper;

    @Autowired
    public TimDecoderTests(TimDecoder timDecoder) {
        this.timDecoder = timDecoder;

        objectMapper = DateJsonMapper.getInstance();

        try {
            odeTimDecodedXmlReference = new String(
                    Files.readAllBytes(Paths.get("src/test/resources/xml/Ode.ReferenceTimXER.xml")));

            odeTimDecodedJsonReference = new String(
                    Files.readAllBytes(Paths
                            .get("src/test/resources/json/tim/Ode.ReferenceTimJson.json")));

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Test verifying the conversion from String XML data to OdeMessageFrame
     * Object
     */
    @Test
    public void testGetAsMessageFrame() {
        try {
            OdeMessageFrameData tim = timDecoder.convertXERToMessageFrame(odeTimDecodedXmlReference);

            tim.getMetadata().setOdeReceivedAt("2025-08-29T16:09:34.416Z");
            tim.getMetadata()
                    .setSerialId(tim.getMetadata().getSerialId().setStreamId("44a6d71c-8af1-4f45-848c-10bd7f919be8"));

            assertThatJson(odeTimDecodedJsonReference).isEqualTo(tim.toJson());
        } catch (JsonProcessingException e) {
            assertEquals(true, false);
        }
    }
}

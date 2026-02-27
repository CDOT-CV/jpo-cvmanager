package us.dot.its.jpo.ode.api.decoderTests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.Point;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.bsm.ProcessedBsm;
import us.dot.its.jpo.ode.api.asn1.BsmDecoder;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
public class BsmDecoderTests {

    private final BsmDecoder bsmDecoder;

    private String odeBsmDecodedXmlReference = "";
    private String odeBsmDecodedJsonReference = "";
    private String processedBsmReference = "";

    ObjectMapper objectMapper =  JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultPropertyInclusion(incl ->
                    incl.withValueInclusion(JsonInclude.Include.NON_NULL)
            )
            .build();;

    public BsmDecoderTests(BsmDecoder bsmDecoder) {
        this.bsmDecoder = bsmDecoder;

        try {
            odeBsmDecodedXmlReference = new String(
                    Files.readAllBytes(Path.of("src/test/resources/xml/Ode.ReferenceBsmXER.xml")));

            odeBsmDecodedJsonReference = new String(
                    Files.readAllBytes(Path
                            .of("src/test/resources/json/bsm/Ode.ReferenceBsmJson.json")));

            processedBsmReference = new String(
                    Files.readAllBytes(Path
                            .of("src/test/resources/json/bsm/GJC.ReferenceProcessedBsmJson.json")));
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
            OdeMessageFrameData bsm = bsmDecoder.convertXERToMessageFrame(odeBsmDecodedXmlReference);

            bsm.getMetadata().setOdeReceivedAt("2025-08-29T16:09:34.416Z");
            bsm.getMetadata()
                    .setSerialId(bsm.getMetadata().getSerialId().setStreamId("44a6d71c-8af1-4f45-848c-10bd7f919be8"));

            assertThatJson(odeBsmDecodedJsonReference).isEqualTo(bsm.toJson());
        } catch (JacksonException e) {
            assertEquals(true, false);
        }
    }

    /**
     * Test to verify Conversion from a OdeMessageFrame object to a ProcessedBSM
     * Object
     */
    @Test
    public void testConvertMessageFrameToProcessedBsm() {
        try {
            OdeMessageFrameData bsmMessageFrame = objectMapper.readValue(odeBsmDecodedJsonReference,
                    OdeMessageFrameData.class);

            ProcessedBsm<Point> bsm = bsmDecoder.convertMessageFrameToProcessedBsm(bsmMessageFrame);

            bsm.getProperties().setOdeReceivedAt("2025-08-29T16:09:34.416Z");

            assertThatJson(processedBsmReference).isEqualTo(bsm.toString());
        } catch (JacksonException e) {
            assertEquals(true, false);
        }
    }
}

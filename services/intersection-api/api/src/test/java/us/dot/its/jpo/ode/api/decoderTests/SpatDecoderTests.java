package us.dot.its.jpo.ode.api.decoderTests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
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
import us.dot.its.jpo.geojsonconverter.pojos.spat.ProcessedSpat;
import us.dot.its.jpo.ode.api.asn1.SpatDecoder;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;

import java.nio.file.Files;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
public class SpatDecoderTests {

    private final SpatDecoder spatDecoder;

    private String odeSpatDecodedXmlReference = "";
    private String odeSpatDecodedJsonReference = "";
    private String processedSpatReference = "";

    ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultPropertyInclusion(incl ->
                    incl.withValueInclusion(JsonInclude.Include.NON_NULL)
            )
            .build();;

    public SpatDecoderTests(SpatDecoder spatDecoder) {
        this.spatDecoder = spatDecoder;

        try {

            odeSpatDecodedXmlReference = new String(
                    Files.readAllBytes(Path.of("src/test/resources/xml/Ode.ReferenceSpatXER.xml")));

            odeSpatDecodedJsonReference = new String(
                    Files.readAllBytes(Path
                            .of("src/test/resources/json/spat/Ode.ReferenceSpatJson.json")));

            processedSpatReference = new String(
                    Files.readAllBytes(Path
                            .of("src/test/resources/json/spat/GJC.ReferenceProcessedSpatJson.json")));
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
            OdeMessageFrameData spat = spatDecoder.convertXERToMessageFrame(odeSpatDecodedXmlReference);

            spat.getMetadata().setOdeReceivedAt("2025-08-29T16:09:34.416Z");
            spat.getMetadata()
                    .setSerialId(spat.getMetadata().getSerialId().setStreamId("44a6d71c-8af1-4f45-848c-10bd7f919be8"));

            assertThatJson(odeSpatDecodedJsonReference).isEqualTo(spat.toJson());
        } catch (JacksonException e) {
            assertEquals(true, false);
        }
    }

    /**
     * Test to verify Conversion from a OdeMessageFrame object to a ProcessedSPAT
     * Object
     */
    @Test
    public void testConvertMessageFrameToProcessedSpat() {

        try {
            OdeMessageFrameData spatMessageFrame = objectMapper.readValue(odeSpatDecodedJsonReference,
                    OdeMessageFrameData.class);

            spatMessageFrame.getMetadata().setOdeReceivedAt("2025-08-29T16:09:34.416Z");

            ProcessedSpat spat = spatDecoder.convertMessageFrameToProcessedSpat(spatMessageFrame);

            spat.setOdeReceivedAt("2025-08-29T16:09:34.416Z");

            assertThatJson(processedSpatReference).isEqualTo(spat.toString());
        } catch (JacksonException e) {
            assertEquals(true, false);
        }
    }
}

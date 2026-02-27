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
import us.dot.its.jpo.ode.api.asn1.SrmDecoder;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
public class SrmDecoderTests {

    private final SrmDecoder srmDecoder;

    private String odeSrmDecodedXmlReference = "";
    private String odeSrmDecodedJsonReference = "";

    ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultPropertyInclusion(incl ->
                    incl.withValueInclusion(JsonInclude.Include.NON_NULL)
            )
            .build();;

    public SrmDecoderTests(SrmDecoder srmDecoder) {
        this.srmDecoder = srmDecoder;

        try {
            odeSrmDecodedXmlReference = new String(
                    Files.readAllBytes(Path.of("src/test/resources/xml/Ode.ReferenceSrmXER.xml")));

            odeSrmDecodedJsonReference = new String(
                    Files.readAllBytes(Path
                            .of("src/test/resources/json/srm/Ode.ReferenceSrmJson.json")))
                    .replaceAll("\n", "").replaceAll(" ", "");

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
            OdeMessageFrameData srm = srmDecoder.convertXERToMessageFrame(odeSrmDecodedXmlReference);

            srm.getMetadata().setOdeReceivedAt("2025-08-29T16:09:34.416Z");
            srm.getMetadata()
                    .setSerialId(srm.getMetadata().getSerialId().setStreamId("44a6d71c-8af1-4f45-848c-10bd7f919be8"));

            assertThatJson(odeSrmDecodedJsonReference).isEqualTo(srm.toJson());
        } catch (JacksonException e) {
            assertEquals(true, false);
        }
    }
}

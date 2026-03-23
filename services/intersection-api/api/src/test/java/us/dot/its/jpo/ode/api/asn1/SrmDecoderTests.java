package us.dot.its.jpo.ode.api.asn1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import j2735ffm.MessageFrameCodec;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

@SpringBootTest(properties = {"enable.asn1-decoder=true"})
public class SrmDecoderTests {

    private final SrmDecoder srmDecoder;

    private String odeSrmDecodedXmlReference = "";
    private String odeSrmDecodedJsonReference = "";

    ObjectMapper objectMapper;
    @MockitoBean
    @SuppressWarnings("unused") // needed to satisfy @ConditionalOnBean without loading the native library
    MessageFrameCodec messageFrameCodec;

    @Autowired
    public SrmDecoderTests(SrmDecoder srmDecoder) {
        this.srmDecoder = srmDecoder;

        objectMapper = DateJsonMapper.getInstance();

        try {
            odeSrmDecodedXmlReference = new String(
                    Files.readAllBytes(Paths.get("src/test/resources/xml/Ode.ReferenceSrmXER.xml")));

            odeSrmDecodedJsonReference = new String(
                    Files.readAllBytes(Paths
                            .get("src/test/resources/json/srm/Ode.ReferenceSrmJson.json")))
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
        } catch (JsonProcessingException e) {
            assertEquals(true, false);
        }
    }
}

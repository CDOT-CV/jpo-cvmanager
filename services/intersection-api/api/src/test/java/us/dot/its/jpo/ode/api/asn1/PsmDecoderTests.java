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
public class PsmDecoderTests {

    private final PsmDecoder psmDecoder;

    private String odePsmDecodedXmlReference = "";
    private String odePsmDecodedJsonReference = "";

    ObjectMapper objectMapper;
    @MockitoBean
    @SuppressWarnings("unused") // needed to satisfy @ConditionalOnBean without loading the native library
    MessageFrameCodec messageFrameCodec;

    @Autowired
    public PsmDecoderTests(PsmDecoder psmDecoder) {
        this.psmDecoder = psmDecoder;

        objectMapper = DateJsonMapper.getInstance();

        try {
            odePsmDecodedXmlReference = new String(
                    Files.readAllBytes(Paths.get("src/test/resources/xml/Ode.ReferencePsmXER.xml")));

            odePsmDecodedJsonReference = new String(
                    Files.readAllBytes(Paths
                            .get("src/test/resources/json/psm/Ode.ReferencePsmJson.json")));

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
            OdeMessageFrameData psm = psmDecoder.convertXERToMessageFrame(odePsmDecodedXmlReference);

            psm.getMetadata().setOdeReceivedAt("2025-08-29T16:09:34.416Z");
            psm.getMetadata()
                    .setSerialId(psm.getMetadata().getSerialId().setStreamId("44a6d71c-8af1-4f45-848c-10bd7f919be8"));

            assertThatJson(odePsmDecodedJsonReference).isEqualTo(psm.toJson());
        } catch (JsonProcessingException e) {
            assertEquals(true, false);
        }
    }
}

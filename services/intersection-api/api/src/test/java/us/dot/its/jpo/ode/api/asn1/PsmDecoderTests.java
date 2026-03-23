package us.dot.its.jpo.ode.api.asn1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import j2735ffm.MessageFrameCodec;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;

@ExtendWith(MockitoExtension.class)
public class PsmDecoderTests {

    @Mock
    private MessageFrameCodec messageFrameCodec;

    private PsmDecoder psmDecoder;

    private String odePsmDecodedXmlReference = "";
    private String odePsmDecodedJsonReference = "";

    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        psmDecoder = new PsmDecoder(messageFrameCodec);
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

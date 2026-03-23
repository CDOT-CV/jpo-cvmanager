package us.dot.its.jpo.ode.api.asn1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
import us.dot.its.jpo.geojsonconverter.pojos.spat.ProcessedSpat;
import us.dot.its.jpo.geojsonconverter.validator.JsonValidatorResult;
import us.dot.its.jpo.geojsonconverter.validator.SpatJsonValidator;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;

@ExtendWith(MockitoExtension.class)
public class SpatDecoderTests {

    @Mock
    private MessageFrameCodec messageFrameCodec;

    @Mock
    private SpatJsonValidator spatJsonValidator;

    private SpatDecoder spatDecoder;

    private String odeSpatDecodedXmlReference = "";
    private String odeSpatDecodedJsonReference = "";
    private String processedSpatReference = "";

    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        spatDecoder = new SpatDecoder(messageFrameCodec, spatJsonValidator);
        objectMapper = DateJsonMapper.getInstance();

        try {
            odeSpatDecodedXmlReference = new String(
                    Files.readAllBytes(Paths.get("src/test/resources/xml/Ode.ReferenceSpatXER.xml")));

            odeSpatDecodedJsonReference = new String(
                    Files.readAllBytes(Paths
                            .get("src/test/resources/json/spat/Ode.ReferenceSpatJson.json")));

            processedSpatReference = new String(
                    Files.readAllBytes(Paths
                            .get("src/test/resources/json/spat/GJC.ReferenceProcessedSpatJson.json")));
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
        } catch (JsonProcessingException e) {
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
            when(spatJsonValidator.validate(anyString())).thenReturn(new JsonValidatorResult());

            OdeMessageFrameData spatMessageFrame = objectMapper.readValue(odeSpatDecodedJsonReference,
                    OdeMessageFrameData.class);

            spatMessageFrame.getMetadata().setOdeReceivedAt("2025-08-29T16:09:34.416Z");

            ProcessedSpat spat = spatDecoder.convertMessageFrameToProcessedSpat(spatMessageFrame);

            spat.setOdeReceivedAt("2025-08-29T16:09:34.416Z");

            assertThatJson(processedSpatReference).isEqualTo(spat.toString());
        } catch (JsonProcessingException e) {
            assertEquals(true, false);
        }
    }
}

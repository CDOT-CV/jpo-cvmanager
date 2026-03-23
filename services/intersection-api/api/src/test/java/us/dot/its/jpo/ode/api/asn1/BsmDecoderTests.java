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
import us.dot.its.jpo.geojsonconverter.pojos.geojson.Point;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.bsm.ProcessedBsm;
import us.dot.its.jpo.geojsonconverter.validator.BsmJsonValidator;
import us.dot.its.jpo.geojsonconverter.validator.JsonValidatorResult;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;

@ExtendWith(MockitoExtension.class)
public class BsmDecoderTests {

    @Mock
    private MessageFrameCodec messageFrameCodec;

    @Mock
    private BsmJsonValidator bsmJsonValidator;

    private BsmDecoder bsmDecoder;

    private String odeBsmDecodedXmlReference = "";
    private String odeBsmDecodedJsonReference = "";
    private String processedBsmReference = "";

    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        bsmDecoder = new BsmDecoder(messageFrameCodec, bsmJsonValidator);
        objectMapper = DateJsonMapper.getInstance();

        try {
            odeBsmDecodedXmlReference = new String(
                    Files.readAllBytes(Paths.get("src/test/resources/xml/Ode.ReferenceBsmXER.xml")));

            odeBsmDecodedJsonReference = new String(
                    Files.readAllBytes(Paths
                            .get("src/test/resources/json/bsm/Ode.ReferenceBsmJson.json")));

            processedBsmReference = new String(
                    Files.readAllBytes(Paths
                            .get("src/test/resources/json/bsm/GJC.ReferenceProcessedBsmJson.json")));
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
        } catch (JsonProcessingException e) {
            assertEquals(true, false);
        }
    }

    /**
     * Test to verify Conversion from a OdeMessageFrame object to a ProcessedBSM
     * Object
     */
    @Test
    public void testConvertMessageFrameToProcessedBsm() {
        ObjectMapper objectMapper = DateJsonMapper.getInstance();

        try {
            when(bsmJsonValidator.validate(anyString())).thenReturn(new JsonValidatorResult());

            OdeMessageFrameData bsmMessageFrame = objectMapper.readValue(odeBsmDecodedJsonReference,
                    OdeMessageFrameData.class);

            ProcessedBsm<Point> bsm = bsmDecoder.convertMessageFrameToProcessedBsm(bsmMessageFrame);

            bsm.getProperties().setOdeReceivedAt("2025-08-29T16:09:34.416Z");

            assertThatJson(processedBsmReference).isEqualTo(bsm.toString());
        } catch (JsonProcessingException e) {
            assertEquals(true, false);
        }
    }
}

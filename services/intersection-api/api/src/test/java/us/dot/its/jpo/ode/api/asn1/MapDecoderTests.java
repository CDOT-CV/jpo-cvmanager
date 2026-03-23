package us.dot.its.jpo.ode.api.asn1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import j2735ffm.MessageFrameCodec;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.LineString;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.map.ProcessedMap;
import us.dot.its.jpo.geojsonconverter.validator.JsonValidatorResult;
import us.dot.its.jpo.geojsonconverter.validator.MapJsonValidator;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;

@ExtendWith(MockitoExtension.class)
public class MapDecoderTests {

    @Mock
    private MessageFrameCodec messageFrameCodec;

    @Mock
    private MapJsonValidator mapJsonValidator;

    private MapDecoder mapDecoder;

    private String odeMapDecodedXmlReference = "";
    private String odeMapDecodedJsonReference = "";
    private String processedMapReference = "";

    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mapDecoder = new MapDecoder(messageFrameCodec, mapJsonValidator);
        objectMapper = DateJsonMapper.getInstance();

        try {
            odeMapDecodedXmlReference = new String(
                    Files.readAllBytes(Paths.get("src/test/resources/xml/Ode.ReferenceMapXER.xml")));

            odeMapDecodedJsonReference = new String(
                    Files.readAllBytes(Paths
                            .get("src/test/resources/json/map/Ode.ReferenceMapJson.json")));

            processedMapReference = new String(
                    Files.readAllBytes(Paths
                            .get("src/test/resources/json/map/GJC.ReferenceProcessedMapJson.json")));
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
            OdeMessageFrameData spat = mapDecoder.convertXERToMessageFrame(odeMapDecodedXmlReference);

            spat.getMetadata().setOdeReceivedAt("2025-08-29T16:09:34.416Z");
            spat.getMetadata()
                    .setSerialId(spat.getMetadata().getSerialId().setStreamId("44a6d71c-8af1-4f45-848c-10bd7f919be8"));

            assertThatJson(odeMapDecodedJsonReference).isEqualTo(spat.toJson());
        } catch (JsonProcessingException e) {
            assertEquals(true, false);
        }
    }

    /**
     * Test to verify Conversion from a OdeMessageFrame object to a ProcessedMAP
     * Object
     */
    @Test
    public void testConvertMessageFrameToProcessedMap() {
        ObjectMapper objectMapper = DateJsonMapper.getInstance();

        try {
            when(mapJsonValidator.validate(anyString())).thenReturn(new JsonValidatorResult());

            OdeMessageFrameData mapMessageFrame = objectMapper.readValue(odeMapDecodedJsonReference,
                    OdeMessageFrameData.class);

            ProcessedMap<LineString> map = mapDecoder.convertMessageFrameToProcessedMap(mapMessageFrame);

            map.getProperties().setOdeReceivedAt(ZonedDateTime.parse("2025-08-29T16:09:34.416Z"));

            assertThatJson(processedMapReference).isEqualTo(map.toString());
        } catch (JsonProcessingException e) {
            assertEquals(true, false);
        }
    }
}

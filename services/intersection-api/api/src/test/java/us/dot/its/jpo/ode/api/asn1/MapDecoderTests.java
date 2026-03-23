package us.dot.its.jpo.ode.api.asn1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import j2735ffm.MessageFrameCodec;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.LineString;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.map.ProcessedMap;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

@SpringBootTest(properties = {"enable.asn1-decoder=true"})
public class MapDecoderTests {

    private final MapDecoder mapDecoder;

    private String odeMapDecodedXmlReference = "";
    private String odeMapDecodedJsonReference = "";
    private String processedMapReference = "";

    ObjectMapper objectMapper;
    @MockitoBean
    @SuppressWarnings("unused") // needed to satisfy @ConditionalOnBean without loading the native library
    MessageFrameCodec messageFrameCodec;

    @Autowired
    public MapDecoderTests(MapDecoder mapDecoder) {
        this.mapDecoder = mapDecoder;

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

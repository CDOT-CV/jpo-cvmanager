package us.dot.its.jpo.ode.mockdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;

import us.dot.its.jpo.geojsonconverter.pojos.geojson.Point;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.bsm.ProcessedBsm;
import us.dot.its.jpo.ode.model.OdeMessageFrameData;

import tools.jackson.core.type.TypeReference;

@Slf4j
public class MockBsmGenerator {
    static TypeReference<ProcessedBsm<Point>> processedBsmTypeReference = new TypeReference<>() {
    };

    public static List<OdeMessageFrameData> getJsonBsms() {
        ObjectMapper objectMapper = new ObjectMapper();

        ArrayList<OdeMessageFrameData> bsms = new ArrayList<>();

        try {
            String bsmString = new String(Files.readAllBytes(Path.of("src/main/resources/mockdata/bsm.json")));
            OdeMessageFrameData bsm = objectMapper.readValue(bsmString,
                    OdeMessageFrameData.class);
            bsms.add(bsm);
        } catch (DatabindException e) {
            log.error("JsonMappingException", e);
        } catch (JacksonException e) {
            log.error("JsonProcessingException", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return bsms;
    }

    public static List<ProcessedBsm<Point>> getProcessedBsms() {
        ObjectMapper objectMapper = new ObjectMapper();

        ArrayList<ProcessedBsm<Point>> bsms = new ArrayList<>();

        try {
            String processedBsmString = new String(
                    Files.readAllBytes(Path.of("src/main/resources/mockdata/processed_bsm.json")));
            ProcessedBsm<Point> bsm = objectMapper.readValue(processedBsmString,
                    processedBsmTypeReference);

            bsms.add(bsm);
        } catch (DatabindException e) {
            log.error("JsonMappingException", e);
        } catch (JacksonException e) {
            log.error("JsonProcessingException", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return bsms;
    }
}

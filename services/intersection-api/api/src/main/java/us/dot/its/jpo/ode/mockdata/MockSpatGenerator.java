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

import us.dot.its.jpo.asn.j2735.r2024.SPAT.SPAT;
import us.dot.its.jpo.geojsonconverter.pojos.spat.ProcessedSpat;

@Slf4j
public class MockSpatGenerator {

    public static List<ProcessedSpat> getProcessedSpats() {
        ObjectMapper objectMapper = new ObjectMapper();

        ArrayList<ProcessedSpat> spats = new ArrayList<>();

        try {
            String processedSpatString = new String(
                    Files.readAllBytes(Path.of("src/main/resources/mockdata/processed_spat.json")));
            ProcessedSpat spat = objectMapper.readValue(processedSpatString, ProcessedSpat.class);
            spats.add(spat);
        } catch (DatabindException e) {
            log.error("JsonMappingException", e);
        } catch (JacksonException e) {
            log.error("JsonProcessingException", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return spats;
    }

    public static List<SPAT> getJsonSpats() {
        ObjectMapper objectMapper = new ObjectMapper();

        ArrayList<SPAT> spats = new ArrayList<>();

        try {
            String spatString = new String(Files.readAllBytes(Path.of("src/main/resources/mockdata/spat.json")));
            SPAT spat = objectMapper.readValue(spatString, SPAT.class);
            spats.add(spat);
        } catch (DatabindException e) {
            log.error("JsonMappingException", e);
        } catch (JacksonException e) {
            log.error("JsonProcessingException", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return spats;
    }

}

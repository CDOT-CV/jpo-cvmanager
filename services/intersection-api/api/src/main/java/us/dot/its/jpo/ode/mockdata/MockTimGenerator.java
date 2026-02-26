package us.dot.its.jpo.ode.mockdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;

import tools.jackson.databind.DatabindException;

@Slf4j
public class MockTimGenerator {

    public static List<TravelerInformation> getJsonTims() {
        ObjectMapper objectMapper = new ObjectMapper();

        ArrayList<TravelerInformation> tims = new ArrayList<>();

        try {
            String timString = new String(Files.readAllBytes(Path.of("src/main/resources/mockdata/tim.json")));
            TravelerInformation tim = objectMapper.readValue(timString,
                    TravelerInformation.class);
            tims.add(tim);
        } catch (DatabindException e) {
            log.error("JsonMappingException", e);
        } catch (JacksonException e) {
            log.error("JsonProcessingException", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return tims;
    }

}

package us.dot.its.jpo.ode.mockdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

import us.dot.its.jpo.asn.j2735.r2024.SignalRequestMessage.SignalRequestMessage;

import tools.jackson.databind.DatabindException;

@Slf4j
public class MockSrmGenerator {

    public static List<SignalRequestMessage> getJsonSrms() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ArrayList<SignalRequestMessage> srms = new ArrayList<>();

        try {
            String srmString = new String(Files.readAllBytes(Path.of("src/main/resources/mockdata/srm.json")));
            SignalRequestMessage srm = objectMapper.readValue(srmString,
                    SignalRequestMessage.class);
            srms.add(srm);
        } catch (DatabindException e) {
            log.error("JsonMappingException", e);
        } catch (JacksonException e) {
            log.error("JsonProcessingException", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return srms;
    }

}

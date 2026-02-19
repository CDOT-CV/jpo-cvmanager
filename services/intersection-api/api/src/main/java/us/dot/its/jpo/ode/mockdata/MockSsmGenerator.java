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

import us.dot.its.jpo.asn.j2735.r2024.SignalStatusMessage.SignalStatusMessage;

@Slf4j
public class MockSsmGenerator {

    public static List<SignalStatusMessage> getJsonSsms() {
        ObjectMapper objectMapper = new ObjectMapper();

        ArrayList<SignalStatusMessage> ssms = new ArrayList<>();

        try {
            String ssmString = new String(Files.readAllBytes(Path.of("src/main/resources/mockdata/ssm.json")));
            SignalStatusMessage ssm = objectMapper.readValue(ssmString,
                    SignalStatusMessage.class);
            ssms.add(ssm);
        } catch (DatabindException e) {
            log.error("JsonMappingException", e);
        } catch (JacksonException e) {
            log.error("JsonProcessingException", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return ssms;
    }

}

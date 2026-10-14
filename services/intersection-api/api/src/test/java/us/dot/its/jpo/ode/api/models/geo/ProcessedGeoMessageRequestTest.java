package us.dot.its.jpo.ode.api.models.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ProcessedGeoMessageRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesMessageTypeCaseInsensitively() throws Exception {
        assertEquals(GeoMessageType.BSM, read("\" bSm \"").msgType());
        assertEquals(GeoMessageType.PSM, read("\"psm\"").msgType());
    }

    @Test
    void deserializesUnknownOrMissingMessageTypeAsNull() throws Exception {
        assertNull(read("\"TIM\"").msgType());
        assertNull(read("null").msgType());
        assertNull(mapper.readValue("{}", ProcessedGeoMessageRequest.class).msgType());
    }

    private ProcessedGeoMessageRequest read(String msgTypeJson) throws Exception {
        return mapper.readValue("{\"msg_type\": " + msgTypeJson + "}", ProcessedGeoMessageRequest.class);
    }
}

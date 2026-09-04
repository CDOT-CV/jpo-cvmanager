package us.dot.its.jpo.ode.api.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.ode.api.models.PrometheusResponse.PrometheusResult;

public class PrometheusResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserializesInstantQueryAndReadsLabelsAndValue() throws Exception {
        String json = """
                {
                    "status": "success",
                    "data": {
                        "resultType": "vector",
                        "result": [
                            {
                                "metric": {
                                    "topic": "topic.OdeBsmJson",
                                    "rsu_ip": "10.11.81.13"
                                },
                                "value": [1640995200, "150"]
                            }
                        ]
                    }
                }
                """;

        PrometheusResponse response = objectMapper.readValue(json, PrometheusResponse.class);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getResults().size());

        PrometheusResult result = response.getResults().get(0);
        assertEquals("topic.OdeBsmJson", result.getMetricLabel("topic"));
        assertEquals("10.11.81.13", result.getMetricLabel("rsu_ip"));
        assertEquals(150.0, result.getInstantValue());
    }

    @Test
    void testIsSuccessFalseWhenStatusIsError() throws Exception {
        String json = """
                {
                    "status": "error",
                    "errorType": "bad_data",
                    "error": "invalid query"
                }
                """;

        PrometheusResponse response = objectMapper.readValue(json, PrometheusResponse.class);

        assertFalse(response.isSuccess());
        assertTrue(response.getResults().isEmpty());
    }

    @Test
    void testGetInstantValueReturnsZeroWhenValueMissing() {
        PrometheusResult result = new PrometheusResult();
        assertEquals(0.0, result.getInstantValue());
    }
}

package us.dot.its.jpo.ode.api.controllers.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import us.dot.its.jpo.ode.api.accessors.counts.CountsRepository;
import us.dot.its.jpo.ode.api.models.MessageCount;

@ExtendWith(MockitoExtension.class)
public class CountsControllerTest {

    @Mock
    CountsRepository countsRepository;

    private CountsController controller;

    @BeforeEach
    void setUp() {
        controller = new CountsController(countsRepository);
    }

    @Test
    public void testGetRsuMessageCounts() {
        String rsuIp = "10.11.81.13";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;
        String message = "BSM";

        List<MessageCount> expectedCounts = new ArrayList<>();
        MessageCount count1 = new MessageCount();
        count1.setRsuIp(rsuIp);
        count1.setMessageType(message);
        count1.setOdeInputCount(50L);
        count1.setOdeOutputCount(150L);
        count1.setRoad("I-25");
        expectedCounts.add(count1);

        when(countsRepository.getRsuMessageCounts(rsuIp, List.of("BSM"), startTime, endTime))
                .thenReturn(expectedCounts);

        ResponseEntity<List<MessageCount>> result = controller.getRsuMessageCounts(rsuIp, List.of("BSM"), startTime,
                endTime);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getRsuIp()).isEqualTo(rsuIp);
        assertThat(result.getBody().get(0).getOdeInputCount()).isEqualTo(50L);
        assertThat(result.getBody().get(0).getOdeOutputCount()).isEqualTo(150L);
        assertThat(result.getBody().get(0).getRoad()).isEqualTo("I-25");
    }

    @Test
    public void testGetRsuMessageCountsWithMultipleTypes() {
        String rsuIp = "10.11.81.13";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        List<MessageCount> expectedCounts = new ArrayList<>();
        expectedCounts.add(new MessageCount("BSM", rsuIp, 50L, 150L, "I-25"));
        expectedCounts.add(new MessageCount("MAP", rsuIp, 10L, 10L, "I-25"));

        when(countsRepository.getRsuMessageCounts(rsuIp, List.of("BSM", "MAP"), startTime, endTime))
                .thenReturn(expectedCounts);

        ResponseEntity<List<MessageCount>> result = controller.getRsuMessageCounts(rsuIp, List.of("BSM,MAP"),
                startTime, endTime);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).getMessageType()).isEqualTo("BSM");
        assertThat(result.getBody().get(1).getMessageType()).isEqualTo("MAP");
    }

    @Test
    public void testGetOrganizationRsuMessageCounts() {
        String organization = "TestOrg";
        String message = "BSM";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        List<MessageCount> expectedCounts = new ArrayList<>();
        MessageCount count1 = new MessageCount("BSM", "10.11.81.13", 50L, 100L, "I-25");
        MessageCount count2 = new MessageCount("BSM", "10.11.81.14", 25L, 50L, "I-70");
        expectedCounts.add(count1);
        expectedCounts.add(count2);

        when(countsRepository.getRsuOrganizationMessageCounts(organization, message, startTime, endTime))
                .thenReturn(expectedCounts);

        ResponseEntity<List<MessageCount>> result = controller.getOrganizationRsuMessageCounts(organization, message,
                startTime, endTime);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).getRsuIp()).isEqualTo("10.11.81.13");
        assertThat(result.getBody().get(1).getRsuIp()).isEqualTo("10.11.81.14");
    }
}

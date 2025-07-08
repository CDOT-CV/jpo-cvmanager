package us.dot.its.jpo.ode.api.controllers.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import us.dot.its.jpo.ode.api.accessors.counts.CountsRepository;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.services.PermissionService;

@SpringBootTest
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
public class CountsControllerTest {

    private final CountsController controller;

    @MockBean
    CountsRepository countsRepository;

    @MockBean
    PermissionService permissionService;

    @Autowired
    public CountsControllerTest(CountsController controller) {
        this.controller = controller;
    }

    @Test
    public void testGetRsuMessageCounts() {
        // Given
        when(permissionService.hasRole("USER")).thenReturn(true);

        String rsuIp = "192.168.1.100";
        Long startTime = 1640995200000L; // 2022-01-01 00:00:00 UTC
        Long endTime = 1641081600000L; // 2022-01-02 00:00:00 UTC

        List<MessageCount> expectedCounts = new ArrayList<>();
        MessageCount bsmCount = new MessageCount();
        bsmCount.setMessageType("BSM");
        bsmCount.setRsuIp(rsuIp);
        bsmCount.setTimestamp(LocalDateTime.of(2022, 1, 1, 0, 0));
        bsmCount.setCount(150L);
        bsmCount.setSource("mongodb");
        bsmCount.setCountType("in");
        expectedCounts.add(bsmCount);

        when(countsRepository.getMessageCounts(rsuIp, startTime, endTime))
                .thenReturn(expectedCounts);

        // When
        ResponseEntity<List<MessageCount>> result = controller.getRsuMessageCounts(rsuIp, startTime, endTime);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(expectedCounts);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getMessageType()).isEqualTo("BSM");
        assertThat(result.getBody().get(0).getCount()).isEqualTo(150L);
    }

    @Test
    public void testGetRsuMessageCountsEmptyResult() {
        // Given
        when(permissionService.hasRole("USER")).thenReturn(true);

        String rsuIp = "192.168.1.101";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        when(countsRepository.getMessageCounts(rsuIp, startTime, endTime))
                .thenReturn(new ArrayList<>());

        // When
        ResponseEntity<List<MessageCount>> result = controller.getRsuMessageCounts(rsuIp, startTime, endTime);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }
}
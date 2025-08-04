package us.dot.its.jpo.ode.api.controllers.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
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
@AutoConfigureEmbeddedDatabase
@ActiveProfiles("test")
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
        String rsuIp = "10.11.81.13";
        Long startTime = 1640995200000L; // 2022-01-01T00:00:00Z
        Long endTime = 1641081600000L; // 2022-01-02T00:00:00Z
        String message = "BSM";

        List<MessageCount> expectedCounts = new ArrayList<>();
        MessageCount count1 = new MessageCount();
        count1.setRsuIp(rsuIp);
        count1.setMessageType(message);
        count1.setOdeInputCount(50L);
        count1.setOdeOutputCount(150L);
        count1.setRoad("I-25");
        expectedCounts.add(count1);

        when(permissionService.isSuperUser()).thenReturn(true);
        when(countsRepository.getRsuMessageCounts(rsuIp, message, startTime, endTime))
                .thenReturn(expectedCounts);

        // When
        ResponseEntity<List<MessageCount>> result = controller.getRsuMessageCounts(rsuIp, message, startTime,
                endTime);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getRsuIp()).isEqualTo(rsuIp);
        assertThat(result.getBody().get(0).getOdeInputCount()).isEqualTo(50L);
        assertThat(result.getBody().get(0).getOdeOutputCount()).isEqualTo(150L);
        assertThat(result.getBody().get(0).getRoad()).isEqualTo("I-25");
    }

    @Test
    public void testGetOrganizationRsuMessageCounts() {
        // Given
        String organization = "TestOrg";
        String message = "BSM";
        Long startTime = 1640995200000L; // 2022-01-01T00:00:00Z
        Long endTime = 1641081600000L; // 2022-01-02T00:00:00Z

        List<MessageCount> expectedCounts = new ArrayList<>();
        MessageCount count1 = new MessageCount();
        count1.setRsuIp("10.11.81.13");
        count1.setMessageType("BSM");
        count1.setOdeInputCount(50L);
        count1.setOdeOutputCount(150L);
        count1.setRoad("I-25");
        expectedCounts.add(count1);

        MessageCount count2 = new MessageCount();
        count2.setRsuIp("10.11.81.14");
        count2.setMessageType("BSM");
        count2.setOdeInputCount(25L);
        count2.setOdeOutputCount(75L);
        count2.setRoad("I-70");
        expectedCounts.add(count2);

        when(permissionService.isSuperUser()).thenReturn(true);
        when(countsRepository.getRsuOrganizationMessageCounts(organization, message, startTime, endTime))
                .thenReturn(expectedCounts);

        // When
        ResponseEntity<List<MessageCount>> result = controller.getOrganizationRsuMessageCounts(
                organization, message, startTime, endTime);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);

        // Verify first RSU
        assertThat(result.getBody().get(0).getRsuIp()).isEqualTo("10.11.81.13");
        assertThat(result.getBody().get(0).getOdeInputCount()).isEqualTo(50L);
        assertThat(result.getBody().get(0).getOdeOutputCount()).isEqualTo(150L);
        assertThat(result.getBody().get(0).getRoad()).isEqualTo("I-25");

        // Verify second RSU
        assertThat(result.getBody().get(1).getRsuIp()).isEqualTo("10.11.81.14");
        assertThat(result.getBody().get(1).getOdeInputCount()).isEqualTo(25L);
        assertThat(result.getBody().get(1).getOdeOutputCount()).isEqualTo(75L);
        assertThat(result.getBody().get(1).getRoad()).isEqualTo("I-70");
    }

    @Test
    public void testGetOrganizationRsuMessageCountsWithDefaultMessage() {
        // Given
        String organization = "TestOrg";
        Long startTime = 1640995200000L; // 2022-01-01T00:00:00Z
        Long endTime = 1641081600000L; // 2022-01-02T00:00:00Z

        List<MessageCount> expectedCounts = new ArrayList<>();
        MessageCount count1 = new MessageCount();
        count1.setRsuIp("10.11.81.13");
        count1.setMessageType("BSM");
        count1.setOdeInputCount(30L);
        count1.setOdeOutputCount(100L);
        count1.setRoad("I-25");
        expectedCounts.add(count1);

        when(permissionService.isSuperUser()).thenReturn(true);
        when(countsRepository.getRsuOrganizationMessageCounts(organization, "BSM", startTime, endTime))
                .thenReturn(expectedCounts);

        // When
        ResponseEntity<List<MessageCount>> result = controller.getOrganizationRsuMessageCounts(
                organization, "BSM", startTime, endTime);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getRsuIp()).isEqualTo("10.11.81.13");
        assertThat(result.getBody().get(0).getOdeInputCount()).isEqualTo(30L);
        assertThat(result.getBody().get(0).getOdeOutputCount()).isEqualTo(100L);
        assertThat(result.getBody().get(0).getRoad()).isEqualTo("I-25");
    }

    @Test
    public void testGetOrganizationRsuMessageCountsWithEmptyResult() {
        // Given
        String organization = "TestOrg";
        String message = "BSM";
        Long startTime = 1640995200000L; // 2022-01-01T00:00:00Z
        Long endTime = 1641081600000L; // 2022-01-02T00:00:00Z

        when(permissionService.isSuperUser()).thenReturn(true);
        when(countsRepository.getRsuOrganizationMessageCounts(organization, message, startTime, endTime))
                .thenReturn(new ArrayList<>());

        // When
        ResponseEntity<List<MessageCount>> result = controller.getOrganizationRsuMessageCounts(
                organization, message, startTime, endTime);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }
}
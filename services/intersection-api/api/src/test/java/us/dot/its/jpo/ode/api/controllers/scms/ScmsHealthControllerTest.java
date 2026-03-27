package us.dot.its.jpo.ode.api.controllers.scms;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import jakarta.transaction.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.ScmsHealth;
import us.dot.its.jpo.ode.api.services.ScmsHealthService;

import us.dot.its.jpo.ode.api.models.postgres.projections.ScmsHealthRsuProjection;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ScmsHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScmsHealthService scmsHealthService;

    @Test
    @Transactional
    void testGetScmsStatus_SUCCESS() throws Exception {
        // Arrange
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName("10.0.0.1"));
        ScmsHealth sh = new ScmsHealth();
        sh.setHealth(true);
        sh.setExpiration(Instant.now());

        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjection(rsu, sh);
        List<ScmsHealthRsuProjection> queryResults = List.of(projection);
        when(scmsHealthService.getScmsStatuses(anyString())).thenReturn(queryResults);

        // Act & Assert
        mockMvc.perform(get("/scms-status")
                        .header("Organization", "TestOrg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.['10.0.0.1'].health").value(true));

        verify(scmsHealthService).getScmsStatuses(anyString());
    }

    @Test
    void testGetScmsStatus_FAILURE_OrganizationHeaderMissing() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/scms-status"))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetScmsStatus_FAILURE_OrganizationNotFound() throws Exception {
        // Arrange
        when(scmsHealthService.getScmsStatuses(anyString())).thenThrow(new EntityNotFoundException("Organization not found"));

        // Act & Assert
        mockMvc.perform(get("/scms-status")
                        .header("Organization", "TestOrg"))
            .andExpect(status().isNotFound());
    }
}
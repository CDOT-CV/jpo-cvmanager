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
import us.dot.its.jpo.ode.api.models.postgres.tables.ScmsHealth;
import us.dot.its.jpo.ode.api.services.ScmsHealthService;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void testGetScmsStatus_SUCCESS() throws Exception {
        // Arrange
        ScmsHealth scmsHealth = new ScmsHealth();
        List<ScmsHealth> scmsHealthList = List.of(scmsHealth);
        when(scmsHealthService.getScmsStatuses(anyString())).thenReturn(scmsHealthList);

        // Act & Assert
        mockMvc.perform(get("/scms-status")
                        .header("Organization", "TestOrg"))
            .andExpect(status().isOk());

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
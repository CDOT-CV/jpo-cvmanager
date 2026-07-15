package us.dot.its.jpo.ode.api.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.WzdxService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "enable.api=true",
        "enable.wzdx-feed=true",
        "wzdx-feed.base-url=https://data.cotrip.org",
        "wzdx-feed.api-key=test-api-key" })
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WzdxFeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WzdxService wzdxService;

    @MockitoBean
    private PermissionService permissionService;

    private static final String FEED = "{\"type\":\"FeatureCollection\",\"features\":[]}";

    @Test
    @DisplayName("GET /wzdx-feed returns 403 when unauthenticated")
    void unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/wzdx-feed"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /wzdx-feed returns 200 with JSON body for an authenticated user")
    void authenticated_returns200() throws Exception {
        when(wzdxService.callWzdxApi()).thenReturn(FEED);

        mockMvc.perform(get("/wzdx-feed"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(FEED));
    }
}

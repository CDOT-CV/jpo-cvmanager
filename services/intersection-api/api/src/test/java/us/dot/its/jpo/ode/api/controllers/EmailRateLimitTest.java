package us.dot.its.jpo.ode.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giffing.bucket4j.spring.boot.starter.filter.servlet.ServletRateLimitFilter;

import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;
import us.dot.its.jpo.ode.api.models.emails.contents.SupportRequestEmailContents;
import us.dot.its.jpo.ode.api.services.EmailService;
import us.dot.its.jpo.ode.api.services.PermissionService;

/**
 * Verifies Bucket4j rate limiting on /emails/* endpoints:
 * - Per-user limit: EMAIL_RATE_LIMIT_PER_USER req/hr, keyed on JWT subject
 * - Global limit: EMAIL_RATE_LIMIT_PER_INSTANCE req/hr across all callers
 *
 * Limits are overridden to 3 per-user / 6 global for fast test execution.
 * The Caffeine cache is cleared before each test for a fresh bucket state.
 *
 * Uses /emails/support-requests (no @PreAuthorize) and a mocked JwtDecoder to
 * authenticate the Authorization header and extract the per-user key.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "enable.api=true",
        "enable.email=true",
        "bucket4j.enabled=true",
        "spring.cache.cache-names=email-rate-limit",
})
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmailRateLimitTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private List<MockMvcBuilderCustomizer> mockMvcBuilderCustomizers;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        // Bucket4j registers its filter while the web-server factory is customized,
        // which can happen after the auto-configured MockMvc has been built.
        assertThat(webApplicationContext.getBeansOfType(ServletRateLimitFilter.class)).hasSize(1);
        DefaultMockMvcBuilder builder = webAppContextSetup(webApplicationContext);
        mockMvcBuilderCustomizers.forEach(customizer -> customizer.customize(builder));
        mockMvc = builder.build();

        Cache cache = cacheManager.getCache("email-rate-limit");
        assertThat(cache).as("email rate-limit cache").isNotNull();
        cache.clear();
        when(emailService.sendSupportRequest(any()))
                .thenReturn(List.of(new EmailSendResponse(0, "OK")));
        // Default: decode any token and return a Jwt whose subject equals the token
        // value.
        // Individual tests that need custom subjects will re-stub this.
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token == null || token.isEmpty())
                return null;
            return Jwt.withTokenValue(token)
                    .headers(h -> h.put("alg", "none"))
                    .claims(c -> {
                        c.put("sub", token);
                        c.put("iat", Instant.now());
                        c.put("exp", Instant.now().plusSeconds(3600));
                    })
                    .build();
        });
    }

    // ── Per-user limit ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Requests up to the per-user limit (3) all succeed")
    void testRequestsBelowPerUserLimitSucceed() throws Exception {
        for (int i = 0; i < 3; i++) {
            postEmail("user-a").andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("The request immediately after the per-user limit (4th) returns 429")
    void testPerUserLimitExceededReturns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            postEmail("user-b").andExpect(status().isOk());
        }
        postEmail("user-b").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Exhausting one user's bucket does not affect a different user's bucket")
    void testDifferentUsersHaveSeparateBuckets() throws Exception {
        for (int i = 0; i < 3; i++) {
            postEmail("user-c").andExpect(status().isOk());
        }
        postEmail("user-c").andExpect(status().isTooManyRequests());

        // user-d still has a full bucket
        postEmail("user-d").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Different tokens with the same subject share the same per-user bucket")
    void testDifferentTokensSameSubjectShareBucket() throws Exception {
        // Arrange: make jwtDecoder return the same subject for two different token
        // values
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if ("tokenA".equals(token) || "tokenB".equals(token)) {
                return Jwt.withTokenValue(token)
                        .headers(h -> h.put("alg", "none"))
                        .claims(c -> {
                            c.put("sub", "shared-subject");
                            c.put("iat", Instant.now());
                            c.put("exp", Instant.now().plusSeconds(3600));
                        })
                        .build();
            }
            // Fall back to the default behavior for other tokens
            if (token == null || token.isEmpty())
                return null;
            return Jwt.withTokenValue(token)
                    .headers(h -> h.put("alg", "none"))
                    .claims(c -> {
                        c.put("sub", token);
                        c.put("iat", Instant.now());
                        c.put("exp", Instant.now().plusSeconds(3600));
                    })
                    .build();
        });

        // Act: consume the per-user bucket using tokenA
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/emails/support-requests")
                    .header("Authorization", "Bearer tokenA")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new SupportRequestEmailContents("a@b.com", "s", "m"))))
                    .andExpect(status().isOk());
        }

        // Assert: a request using tokenB (same subject) is rejected due to shared
        // bucket exhaustion
        mockMvc.perform(post("/emails/support-requests")
                .header("Authorization", "Bearer tokenB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SupportRequestEmailContents("a@b.com", "s", "m"))))
                .andExpect(status().isTooManyRequests());
    }

    // ── Global limit ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("The request immediately after the global limit (7th) returns 429 regardless of user")
    void testGlobalLimitExceededReturns429() throws Exception {
        // user-e consumes 3 global tokens
        for (int i = 0; i < 3; i++) {
            postEmail("user-e").andExpect(status().isOk());
        }
        // user-f consumes the remaining 3 global tokens (total = 6)
        for (int i = 0; i < 3; i++) {
            postEmail("user-f").andExpect(status().isOk());
        }

        // user-g has a fresh per-user bucket but the global bucket is empty
        postEmail("user-g").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("A brand-new user with no prior requests is blocked when the global limit is exhausted")
    void testGlobalLimitBlocksUserWithRemainingPersonalTokens() throws Exception {
        for (int i = 0; i < 3; i++) {
            postEmail("user-h").andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            postEmail("user-i").andExpect(status().isOk());
        }
        // user-j has never made a request (full per-user bucket), but global is empty
        postEmail("user-j").andExpect(status().isTooManyRequests());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * The mocked JwtDecoder authenticates the header and provides the subject
     * used by the Bucket4j cache-key expression.
     */
    private ResultActions postEmail(String userKey) throws Exception {
        SupportRequestEmailContents body = new SupportRequestEmailContents(
                "tester@example.com", "Test Subject", "Test message body");
        when(permissionService.isSuperUser()).thenReturn(true);

        return mockMvc.perform(post("/emails/support-requests")
                .header("Authorization", "Bearer " + userKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
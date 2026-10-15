package us.dot.its.jpo.ode.api.wzdx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class WzdxFeedPropertiesTest {

    // Import (rather than EnableConfigurationProperties(WzdxFeedProperties.class)) so the
    // class-level @ConditionalOnProperty is evaluated just as it is for component scanning
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    @Import(WzdxFeedProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("binds wzdx-feed.base-url and wzdx-feed.api-key when the feed is enabled")
    void bindsPropertiesWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "enable.api=true",
                        "enable.wzdx-feed=true",
                        "wzdx-feed.base-url=https://data.cotrip.org",
                        "wzdx-feed.api-key=test-api-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    WzdxFeedProperties properties = context.getBean(WzdxFeedProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("https://data.cotrip.org");
                    assertThat(properties.getApiKey()).isEqualTo("test-api-key");
                });
    }

    @Test
    @DisplayName("fails startup when the API key is blank")
    void blankApiKeyFailsValidation() {
        contextRunner
                .withPropertyValues(
                        "enable.api=true",
                        "enable.wzdx-feed=true",
                        "wzdx-feed.base-url=https://data.cotrip.org",
                        "wzdx-feed.api-key=")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("WZDX API Key cannot be blank"));
    }

    @Test
    @DisplayName("fails startup when the base URL is missing")
    void missingBaseUrlFailsValidation() {
        contextRunner
                .withPropertyValues(
                        "enable.api=true",
                        "enable.wzdx-feed=true",
                        "wzdx-feed.api-key=test-api-key")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("Base URL cannot be blank"));
    }

    @Test
    @DisplayName("is not created, and does not require WZDx settings, when the feed is disabled")
    void notCreatedWhenFeedDisabled() {
        contextRunner
                .withPropertyValues("enable.api=true", "enable.wzdx-feed=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(WzdxFeedProperties.class);
                });
    }

    @Test
    @DisplayName("does not expose the API key through toString")
    void toStringDoesNotExposeApiKey() {
        WzdxFeedProperties properties = new WzdxFeedProperties();
        properties.setBaseUrl("https://data.cotrip.org");
        properties.setApiKey("super-secret-key");

        assertThat(properties.toString()).doesNotContain("super-secret-key");
    }
}

package us.dot.its.jpo.ode.api.emails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import com.postmarkapp.postmark.client.ApiClient;

import us.dot.its.jpo.ode.api.emails.health.PostmarkHealthIndicator;
import us.dot.its.jpo.ode.api.emails.providers.EmailProviderPostmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

class EmailPropertiesTest {

    @Test
    void createsPostmarkClientWithResolvedJacksonVersion() {
        EmailProperties properties = new EmailProperties();
        EmailProperties.PostmarkProperties postmark = new EmailProperties.PostmarkProperties();
        postmark.setApiKey("test-token");
        properties.setPostmark(postmark);

        assertThatNoException().isThrownBy(properties::apiClient);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void postmarkProviderUsesNormalClientAndHealthIndicatorUsesDedicatedClient(boolean healthEnabled) {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertyBindingConfiguration.class, EmailProperties.class,
                        EmailProviderPostmark.class, PostmarkHealthIndicator.class)
                .withBean(UnsubscribeTokenGenerator.class, () -> mock(UnsubscribeTokenGenerator.class))
                .withPropertyValues("email.broker=POSTMARK", "email.postmark.api-key=test-token",
                        "management.health.postmark.enabled=" + healthEnabled)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ApiClient emailClient = context.getBean("apiClient", ApiClient.class);
                    ApiClient healthClient = context.getBean("postmarkHealthCheck", ApiClient.class);
                    assertThat(healthClient).isNotSameAs(emailClient);
                    assertThat(context.getBean(ApiClient.class)).isSameAs(emailClient);
                    assertThat(ReflectionTestUtils.getField(context.getBean(EmailProviderPostmark.class), "postmark"))
                            .isSameAs(emailClient);
                    if (healthEnabled) {
                        assertThat(ReflectionTestUtils.getField(context.getBean(PostmarkHealthIndicator.class), "postmark"))
                                .isSameAs(healthClient);
                    } else {
                        assertThat(context).doesNotHaveBean(PostmarkHealthIndicator.class);
                    }
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    static class PropertyBindingConfiguration {
    }
}

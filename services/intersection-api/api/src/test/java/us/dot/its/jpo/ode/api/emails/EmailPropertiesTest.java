package us.dot.its.jpo.ode.api.emails;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

class EmailPropertiesTest {

    @Test
    void createsPostmarkClientWithResolvedJacksonVersion() {
        EmailProperties properties = new EmailProperties();
        EmailProperties.PostmarkProperties postmark = new EmailProperties.PostmarkProperties();
        postmark.setApiKey("test-token");
        properties.setPostmark(postmark);

        assertThatNoException().isThrownBy(properties::apiClient);
    }
}

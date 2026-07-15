package us.dot.its.jpo.ode.api.wzdx;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WzdxFeedConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "wzdx-feed")
    public WzdxFeedProperties wzdxFeedProperties() {
        return new WzdxFeedProperties();
    }
}

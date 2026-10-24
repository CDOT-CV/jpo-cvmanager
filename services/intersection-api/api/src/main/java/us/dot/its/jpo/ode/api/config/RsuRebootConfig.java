package us.dot.its.jpo.ode.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import us.dot.its.jpo.ode.api.services.ssh.MinaSshRebootClient;
import us.dot.its.jpo.ode.api.services.ssh.SshRebootClient;

/** Owns the SSH client and a small executor with no queue of delayed reboot commands. */
@Configuration
@EnableConfigurationProperties(RsuRebootProperties.class)
public class RsuRebootConfig {
    @Bean(destroyMethod = "close")
    public SshRebootClient sshRebootClient(RsuRebootProperties properties) {
        return new MinaSshRebootClient(properties);
    }

    @Bean(defaultCandidate = false)
    public ThreadPoolTaskExecutor rsuRebootExecutor(RsuRebootProperties properties, SshRebootClient client) {
        // Explicit injection keeps this pool separate from Spring's application executor.
        // The client dependency also keeps SSH available until this executor shuts down.
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getConcurrency());
        executor.setMaxPoolSize(properties.getConcurrency());
        // Reject excess work immediately instead of rebooting devices long after a user requested it.
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("rsu-reboot-");

        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(5);

        return executor;
    }
}

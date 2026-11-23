package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.services.ssh.SshRebootClient;

/** Admits bounded background reboots after the controller has checked ownership and role. */
@Slf4j
@Service
public class RsuRebootService {
    private final RsuRepository repository;
    private final SshRebootClient client;
    private final TaskExecutor executor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public RsuRebootService(RsuRepository repository, SshRebootClient client,
            @Qualifier("rsuRebootExecutor") TaskExecutor executor) {
        this.repository = repository;
        this.client = client;
        this.executor = executor;
    }

    /** Returns an ID for logs once accepted; acceptance does not confirm device execution. */
    @Transactional(readOnly = true)
    public UUID requestReboot(InetAddress address) {
        String ip = address.getHostAddress();
        var rsu = repository.findByIpv4Address(address);
        if (rsu == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RSU not found");

        var model = rsu.getModel();
        // This SSH procedure is supported only on Commsignia devices. Validate
        // inventory before reserving capacity or allowing a worker to contact it.
        if (model == null || model.getManufacturer() == null
                || !"Commsignia".equalsIgnoreCase(model.getManufacturer().getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "SSH reboot is supported only for Commsignia RSUs");
        }

        var credentials = rsu.getCredential();
        if (credentials == null || credentials.getUsername() == null || credentials.getUsername().isBlank()
                || credentials.getPassword() == null || credentials.getPassword().isEmpty()
                || credentials.getPassword().contains("\n") || credentials.getPassword().contains("\r")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RSU SSH credentials are missing or invalid");
        }

        // Reserve the device atomically so overlapping HTTP requests cannot both dispatch a reboot.
        if (!inFlight.add(ip)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A reboot request is already running for this RSU");
        }

        UUID requestId = UUID.randomUUID();
        // Capture only immutable values; workers do not retain JPA entities or the request context.
        String username = credentials.getUsername();
        String password = credentials.getPassword();

        try {
            executor.execute(() -> execute(requestId, address, username, password));
        } catch (RejectedExecutionException e) {
            // No worker started; release the device so a later request can try again.
            inFlight.remove(ip);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Reboot capacity is busy; no command sent");
        }

        log.info("RSU reboot accepted [request={} target={}]", requestId, ip);
        return requestId;
    }

    private void execute(UUID requestId, InetAddress address, String username, String password) {
        long started = System.nanoTime();
        String ip = address.getHostAddress();

        try {
            var outcome = client.reboot(address, username, password);
            long elapsed = (System.nanoTime() - started) / 1_000_000;

            if (outcome == SshRebootClient.Outcome.COMMAND_ACCEPTED) {
                log.info("RSU reboot command accepted [request={} target={} elapsed={}ms]", requestId, ip, elapsed);
            } else if (outcome == SshRebootClient.Outcome.DISCONNECTED) {
                log.info("RSU reboot command sent; SSH disconnected; restart not verified [request={} target={} elapsed={}ms]",
                        requestId, ip, elapsed);
            } else {
                log.warn("RSU reboot ended [request={} target={} outcome={} elapsed={}ms]", requestId, ip, outcome, elapsed);
            }
        } catch (RuntimeException e) {
            // Exception messages may contain device output. Keep troubleshooting metadata only.
            log.error("RSU reboot ended [request={} target={} outcome=UNKNOWN exceptionType={}]",
                    requestId, ip, e.getClass().getSimpleName());
        } finally {
            inFlight.remove(ip);
        }
    }
}

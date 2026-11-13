package us.dot.its.jpo.ode.api.emails.health;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.exception.PostmarkException;

/**
 * Shares a single health-only Postmark request among concurrent callers, with
 * a five-second deadline measured from the start of the shared check.
 */
@Component
@ConditionalOnProperty(name = "management.health.postmark.enabled", havingValue = "true")
public class PostmarkHealthIndicator implements HealthIndicator, AutoCloseable {

    private static final long HEALTH_CHECK_TIMEOUT_SECONDS = 5;
    private final ApiClient postmark;
    // Protects inFlight, closed, and each check's worker and result transitions.
    private final Object lock = new Object();
    private Check inFlight;
    private boolean closed;
    // The bounded queue permits a new check while the preceding worker is finishing.
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "postmark-health-check");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Creates an indicator using a client dedicated to health checks.
     *
     * @param postmark the health-check Postmark client
     */
    public PostmarkHealthIndicator(@Qualifier("postmarkHealthCheck") ApiClient postmark) {
        this.postmark = postmark;
    }

    /**
     * Starts or joins a check and waits until its shared deadline. A caller
     * interrupted while waiting returns down without cancelling the shared check.
     *
     * @return the shared Postmark health result, or down on timeout or interruption
     */
    @Override
    public Health health() {
        Check check;
        synchronized (lock) {
            if (closed) {
                return Health.down().withDetail("error", "Postmark health check is shutting down").build();
            }
            if (inFlight == null) {
                check = new Check();
                inFlight = check;
                try {
                    executor.execute(() -> runCheck(check));
                } catch (RejectedExecutionException exception) {
                    inFlight = null;
                    return Health.down().withException(exception).build();
                }
            } else {
                check = inFlight;
            }
        }

        try {
            long remaining = check.deadlineNanos - System.nanoTime();
            return check.result.get(Math.max(0, remaining), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            synchronized (lock) {
                // Only the caller that establishes the timeout interrupts the worker.
                if (check.result.complete(timedOut()) && check.worker != null) {
                    check.worker.interrupt();
                }
                return check.result.join();
            }
        } catch (InterruptedException exception) {
            // One interrupted caller must not cancel the shared check for other callers.
            Thread.currentThread().interrupt();
            return Health.down().withException(exception).build();
        } catch (ExecutionException exception) {
            return Health.down().withException(exception.getCause()).build();
        }
    }

    /**
     * Runs the Postmark request and clears the in-flight check after it returns,
     * including when it continues running after the shared deadline.
     *
     * @param check the shared check to run
     */
    private void runCheck(Check check) {
        synchronized (lock) {
            check.worker = Thread.currentThread();
        }
        try {
            if (check.result.isDone()) {
                return;
            }
            postmark.getServer();
            complete(check, Health.up().build());
        } catch (PostmarkException | IOException | RuntimeException exception) {
            complete(check, Health.down().withException(exception).build());
        } finally {
            synchronized (lock) {
                // Keep a timed-out check in flight until the SDK call actually returns:
                // interruption may not stop it, and starting another could exhaust resources.
                check.worker = null;
                if (inFlight == check) {
                    inFlight = null;
                }
            }
        }
    }

    /**
     * Publishes the result unless the check has already completed, substituting
     * a timeout result if its deadline has elapsed.
     *
     * @param check the shared check to complete
     * @param health the result of the Postmark request
     */
    private void complete(Check check, Health health) {
        synchronized (lock) {
            // A late success must not override the shared deadline or a published timeout.
            check.result.complete(System.nanoTime() - check.deadlineNanos >= 0 ? timedOut() : health);
        }
    }

    /**
     * Creates the down result shared by callers when the deadline expires.
     *
     * @return a timeout health result
     */
    private static Health timedOut() {
        return Health.down().withDetail("error", "Postmark health check timed out after 5 seconds").build();
    }

    private static final class Check {
        // All callers share the first caller's deadline, including callers arriving later.
        private final long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(HEALTH_CHECK_TIMEOUT_SECONDS);
        private final CompletableFuture<Health> result = new CompletableFuture<>();
        private Thread worker;
    }

    /**
     * Stops accepting checks, releases waiting callers, and requests interruption
     * of the worker. An SDK call that ignores interruption may continue running.
     */
    @Override
    @PreDestroy
    public void close() {
        synchronized (lock) {
            closed = true;
            if (inFlight != null) {
                // Release waiting callers even if shutdown removes a queued check.
                inFlight.result.complete(
                        Health.down().withDetail("error", "Postmark health check is shutting down").build());
            }
            executor.shutdownNow();
        }
    }
}

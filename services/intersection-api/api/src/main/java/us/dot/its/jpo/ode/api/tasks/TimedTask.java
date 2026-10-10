package us.dot.its.jpo.ode.api.tasks;

import org.slf4j.Logger;

final class TimedTask {

    private TimedTask() {
    }

    static void run(Logger log, String taskName, Runnable task) {
        long startNanos = System.nanoTime();
        try {
            task.run();
            log.info("Completed {} in {} ms", taskName, elapsedMillis(startNanos));
        } catch (RuntimeException | Error e) {
            log.error("Failed {} after {} ms", taskName, elapsedMillis(startNanos), e);
            throw e;
        }
    }

    static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}

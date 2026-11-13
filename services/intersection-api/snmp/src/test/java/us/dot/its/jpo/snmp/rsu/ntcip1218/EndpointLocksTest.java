package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class EndpointLocksTest {
    @Test void entriesAreRemovedAfterEveryCallerReleasesThem() throws Exception {
        var locks = new EndpointLocks();
        for (int i = 0; i < 1000; i++) {
            try (var first = locks.acquire("endpoint-" + i)) {
                try (var nested = locks.acquire("endpoint-" + i)) {
                    assertEquals(1, locks.size());
                }
                assertEquals(1, locks.size());
            }
            assertEquals(0, locks.size());
        }
    }

    @Test void interruptedWaitersReleaseTheirReferenceWithoutRemovingTheOwnersLock() throws Exception {
        var locks = new EndpointLocks();
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        try (var owner = locks.acquire("same")) {
            var waiter = Thread.ofVirtual().start(() -> {
                started.countDown();
                try (var ignored = locks.acquire("same")) {
                    fail("Waiter acquired the owner's lock");
                } catch (InterruptedException expected) {
                    interrupted.countDown();
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            waiter.interrupt();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            waiter.join();
            assertEquals(1, locks.size());
            try (var unrelated = locks.acquire("other")) {
                assertEquals(2, locks.size());
            }
        }
        assertEquals(0, locks.size());
        try (var recovered = locks.acquire("same")) { assertEquals(1, locks.size()); }
        assertEquals(0, locks.size());
    }

    @Test void aWaitingCallerKeepsTheSameEndpointEntryUntilItFinishes() throws Exception {
        var locks = new EndpointLocks();
        var started = new CountDownLatch(1);
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Thread waiter;
        try (var owner = locks.acquire("same")) {
            waiter = Thread.ofVirtual().start(() -> {
                started.countDown();
                try (var ignored = locks.acquire("same")) {
                    acquired.countDown();
                    release.await();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertFalse(acquired.await(100, TimeUnit.MILLISECONDS));
        }
        try {
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            assertEquals(1, locks.size());
        } finally {
            release.countDown();
            waiter.join();
        }
        assertEquals(0, locks.size());
    }
}

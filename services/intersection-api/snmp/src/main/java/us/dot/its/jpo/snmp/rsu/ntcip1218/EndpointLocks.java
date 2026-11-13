package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Coordinates procedures by exact endpoint, retaining entries only for active or waiting callers. */
final class EndpointLocks {
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    Lease acquire(String endpoint) throws InterruptedException {
        var entry = entries.compute(endpoint, (key, existing) -> {
            var current = existing == null ? new Entry() : existing;
            current.users++;
            return current;
        });
        try {
            entry.lock.lockInterruptibly();
        } catch (InterruptedException failure) {
            release(endpoint, entry);
            throw failure;
        }
        return new Lease(endpoint, entry);
    }

    private void release(String endpoint, Entry entry) {
        // References include waiters. Removing a lock early would let a new caller bypass them.
        entries.compute(endpoint, (key, existing) -> --entry.users == 0 ? null : entry);
    }

    /** Number of endpoint entries currently retained, including callers waiting for a lock. */
    int size() { return entries.size(); }

    private static final class Entry {
        final ReentrantLock lock = new ReentrantLock(true);
        int users; // Accessed only inside compute() for this endpoint.
    }

    final class Lease implements AutoCloseable {
        private final String endpoint;
        private final Entry entry;

        private Lease(String endpoint, Entry entry) { this.endpoint = endpoint; this.entry = entry; }

        @Override public void close() {
            entry.lock.unlock();
            release(endpoint, entry);
        }
    }
}

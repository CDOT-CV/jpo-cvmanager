package us.dot.its.jpo.snmp.execution;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import us.dot.its.jpo.snmp.client.SnmpTarget;

/**
 * A reusable definition of ordered steps for one device. Construction performs no I/O.
 * Steps run serially; captured inputs and returned values must be immutable for safe reuse.
 *
 * @param name log-safe label, 1-80 letters, digits, underscores, dots or hyphens
 * @param target endpoint and credentials shared by every step
 * @param steps 1-1024 steps with unique names; the list is copied
 */
public record SnmpTask(String name, SnmpTarget target, List<SnmpStep<?>> steps) {
    /**
     * Validates the definition before it can be submitted.
     * @throws IllegalArgumentException if the task name, step count or step names are invalid
     */
    public SnmpTask {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Invalid task name");
        Objects.requireNonNull(target);
        steps = List.copyOf(steps);
        var names = new HashSet<String>();
        if (steps.isEmpty() || steps.size() > 1024 || steps.stream().anyMatch(step -> !names.add(step.name()))) {
            throw new IllegalArgumentException("Tasks require 1..1024 uniquely named steps");
        }
    }
}

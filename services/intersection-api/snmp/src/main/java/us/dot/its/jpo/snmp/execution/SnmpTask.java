package us.dot.its.jpo.snmp.execution;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import us.dot.its.jpo.snmp.client.SnmpTarget;

/** One device and a finite, predeclared sequence. Actions execute serially and must not spawn child calls. */
public record SnmpTask(String name, SnmpTarget target, List<SnmpStep<?>> steps) {
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

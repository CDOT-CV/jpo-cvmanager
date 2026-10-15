package us.dot.its.jpo.snmp.execution;

import java.util.Objects;

/** A declared step. VERIFY must return Boolean.TRUE; false is a verified mismatch. */
public record SnmpStep(String name, Kind kind, Action action) {
    public enum Kind { READ, WRITE, VERIFY, LOCAL }
    @FunctionalInterface public interface Action { Object execute(SnmpTaskContext context) throws Exception; }
    public SnmpStep {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Invalid step name");
        Objects.requireNonNull(kind); Objects.requireNonNull(action);
    }
    @Override public String toString() { return "SnmpStep[name=" + name + ", kind=" + kind + "]"; }
}

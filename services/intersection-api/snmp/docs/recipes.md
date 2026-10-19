# Adding a device recipe

A recipe builds a `SnmpTask` for one device. It gives callers a small, meaningful
operation while leaving connections, queueing and deadlines to the shared executor.
Forwarding is the first recipe; the pattern also supports status reads,
certificate checks and other device procedures.

## Choose the right layer

- Use `client` types for generic SNMP operations and values.
- Put OIDs, encodings and device rules with the relevant protocol helpers.
- Put the recipe beside those helpers, named for its feature, as
  `Ntcip1218ForwardingTasks` is today. A future standard-MIB recipe does not need
  to depend on NTCIP or forwarding.
- Keep authentication, inventory lookup, scheduling and database transactions in
  the consuming application.

There is no common recipe interface to implement: `SnmpTask` is the shared
contract. Add another recipe class when there is an actual use case. Do not add
empty feature packages, a registry or a base class for hypothetical recipes.

## Build steps, do not execute them

A small read recipe can be an ordinary factory method:

```java
public SnmpTask status(SnmpTarget target, List<SnmpOid> measurementOids) {
    var oids = List.copyOf(measurementOids);
    return new SnmpTask("device-status", target, List.of(
        new SnmpStep("read-measurements", SnmpStep.Kind.READ,
            ctx -> ctx.client().get(ctx.target(), oids))));
}
```

Constructing the task performs no device I/O. The caller submits it to an
executor and receives a handle. Capture immutable inputs; factories can be
shared, but per-execution mutable state belongs in the task context.

Always make SNMP calls through `ctx.client()` and use the supplied target.
Do not capture a different transport or spawn child threads from a step. That
would bypass tracking or the device’s execution budget. Use `ctx.value(...)` to
read a successful earlier step’s result instead of a shared mutable field.

## Declare what each step means

| Kind | Purpose |
| --- | --- |
| `READ` | Retrieve device data; never issue SET |
| `WRITE` | Apply a device change, possibly through a multi-call procedure |
| `VERIFY` | Explicitly confirm the preceding change; return `Boolean.TRUE` only on confirmation |
| `LOCAL` | Perform application work, such as saving the completed snapshot |

Names must be unique within a task. Prefer short labels callers can understand
in a result summary. A failed command stops later steps even if its callback
tries to catch the failure.

The executor records verification evidence; it cannot decide what proves a
feature’s correctness. The recipe must check the relevant device values. A
successful SET alone does not prove the requested configuration is active.

## How forwarding applies the pattern

Forwarding has four steps when a snapshot callback is supplied:

1. `configure-forwarding`: find the row, send the change, then verify that row
   using the NTCIP procedure. No matching row on deletion is a confirmed no-op.
2. `confirm-result`: record the procedure’s verification guarantee. This step
   makes no additional network request; the procedure has already verified it.
3. `read-current-forwarding`: read both complete forwarding tables.
4. The caller’s named `SnapshotAction`, such as `sync-database`.

Create and delete share this composition, result handling and snapshot callback.
Their device rules remain in `Ntcip1218Client`. Applications should use
`Ntcip1218ForwardingTasks.changed(result)` rather than depend on internal step
names. The complete snapshot is kept separate from verification so a later
persistence failure does not erase evidence of a verified device change.

`SnapshotAction` is forwarding-specific because its input is a forwarding table.
Do not make other recipes depend on it just to reuse a callback signature.
Use their own result types and add shared helpers only when actual repeated code
has the same behavior and lifecycle.

## Preserve reliable outcomes

Reads may retry within their configured budget. SET requests never automatically
retry. If a response is lost after a write, report the uncertainty and inspect
the device before issuing another write. Repeated create submissions are not
automatically deduplicated.

Tasks for one endpoint are serialized within one executor. Other processes and
external device tools are outside that boundary. Local callbacks must have
bounded work and cooperate with interruption; the library cannot forcibly undo
database commits or stop arbitrary application code.

Tests should cover the successful procedure, meaningful no-ops, missing or
invalid values, timeouts, cancellation and uncertain writes. Write recipes should
also cover verification failure and failure of any later application callback.
Use the existing disposable local agents; hardware is for additional validation,
not a requirement for the ordinary test suite.

# Adding a device recipe

A recipe builds a `SnmpTask` for one device. It does no I/O until submitted.

- Put recipes beside the protocol helpers they use, named for the feature.
- Use generic `client` types; keep SNMP4J types inside the adapter.
- Keep permissions, inventory lookup, polling schedules and database transactions in the application.
- Return `SnmpTask` directly. No recipe interface, base class or registry is needed.

## Example

```java
public SnmpTask status(SnmpTarget target, List<SnmpOid> measurementOids) {
    var oids = List.copyOf(measurementOids);
    return new SnmpTask("device-status", target, List.of(
        new SnmpStep("read-measurements", SnmpStep.Kind.READ,
            ctx -> ctx.client().get(ctx.target(), oids))));
}
```

- Capture immutable inputs. Keep per-run values in the task context.
- Use `ctx.client()` for every SNMP call, including calls inside device helpers.
- Use `ctx.value("step-name", Type.class)` to read an earlier step's result.
- Do not spawn child threads or use another client inside a step; that bypasses tracking and deadlines.

## Step kinds

| Kind     | Use                                                            |
| -------- | -------------------------------------------------------------- |
| `READ`   | Read device data; no SET allowed                               |
| `WRITE`  | Change device state                                            |
| `VERIFY` | Return `Boolean.TRUE` only after confirming the intended state |
| `LOCAL`  | Run application work, such as saving a snapshot                |

- Give steps unique, short names without secrets.
- A successful SET is not verification. Check device readback.
- A failed call stops later steps, even if the action catches its exception.
- SET is never retried automatically. Report uncertain outcomes and check the device before retrying.
- Local work must be bounded and respond to interruption. Cancellation cannot undo a write or database commit.

## Forwarding example

`Ntcip1218ForwardingTasks` builds these steps:

1. `configure-forwarding`: locate/allocate a row, change it, and verify readback.
2. `confirm-result`: record that verification; sends no extra request.
3. `read-current-forwarding`: read both complete tables.
4. Optional `SnapshotAction`: pass the snapshot to application code.

Use `changed(result)` to read the outcome. A later snapshot or callback failure
preserves the verified device change. `SnapshotAction` is specific to forwarding;
other recipes should use callbacks suited to their own data.

# JPO SNMP

Java SNMP library for Connected Vehicle devices.

- Requires Java 22; uses SNMP4J internally.
  - JDK 22 was chosen for compatibility with the Intersection API. This library can move to JDK 25 when the Intersection API does.
- Supports generic SNMPv3 operations and NTCIP 1218 RSU helpers.
- No Spring or database dependency. RSU 4.1 is not supported.
- Maven: `usdot.jpo.ode:jpo-snmp:1.0.0-SNAPSHOT`.

## Choose an operation

| Need                          | Use                             |
| ----------------------------- | ------------------------------- |
| Read known OIDs together      | `SnmpClient.get(target, oids)`  |
| Read a table                  | `SnmpClient.walk(target, root)` |
| Queue and track device work   | `SnmpTaskExecutor`              |
| Add/delete message forwarding | `Ntcip1218ForwardingTasks`      |

A **recipe** builds a `SnmpTask`: named steps for one device. The executor runs it.

## Targets and credentials

- `Ntcip1218Device(address, credentials)` uses port 161; pass a port for a custom endpoint.
- Generic calls use `SnmpTarget`. `device.target()` returns the underlying target.
- Construction does no I/O. `Ntcip1218Device.preflight` validates inventory profiles before a batch runs.
- Each target supplies its credentials. No username is assumed.
- SNMPv3 defaults to SHA/AES-128. SHA-256/SHA-512 and DES are also supported.
- An empty privacy password falls back to the authentication password.
- Addresses are literal IPv4; OIDs are numeric. Missing values stay explicit SNMP exception values.

Sessions are cached by the full credential profile, including both passwords and
algorithms. Identical profiles share a transport; different profiles have separate
security state. Each peer keeps its own engine state. No global USM is replaced.

- Only idle sessions can be evicted or rotated. Full caches return `BUSY` when no idle session is available.
- `close()` releases sockets, pending requests and timers.
- Public types do not expose SNMP4J types or credentials in their string representations.

## Configure Message Forwarding

Supports BSM, SPAT, MAP, SSM, SRM and TIM. The example below uses MAP;
change `MessageType` to select another message type and its port/table.

Load credentials and check permissions in your application first.

```java
var credentials = new SnmpCredentials(username, authenticationPassword, privacyPassword);
var device = new Ntcip1218Device("192.0.2.10", credentials);
var forwardingTasks = new Ntcip1218ForwardingTasks(Clock.systemUTC());

try (var client = new Snmp4jClient();
     var executor = new SnmpTaskExecutor(client, SnmpExecutorOptions.defaults())) {
    var task = forwardingTasks.create(device,
        new ForwardingRequest(MessageType.MAP, "198.51.100.3", true));
    var result = executor.submit(task, SnmpTaskOptions.defaults()).await();
    System.out.println(result.summary());
}
```

- Uses UDP and the standard MAP port. `true` includes IEEE 1609.2 security headers.
- Verifies only the changed row after the write. Schedules always use UTC.
- Use `createWithSnapshot` or `deleteWithSnapshot` to also read both forwarding tables.
  Pass a `SnapshotAction` when the application needs to save that snapshot.
- Each create adds a row. Delete with `forwardingTasks.delete(device, MessageType.MAP, "198.51.100.3")`.
- Deleting a missing entry succeeds without a change.

**Reuse** the client, executor and recipe factory. Close the executor
before the client on shutdown.

Imports come from `us.dot.its.jpo.snmp` packages `client`, `execution`, `snmp4j`
and `rsu.ntcip1218`, plus `java.time` and `java.util`.

## Read status values

Use a generic `SnmpTarget(address, credentials)` and group known measurement OIDs
in one GET:

```java
var measurements = new SnmpStep<>("measurements", SnmpStep.Kind.READ,
    ctx -> ctx.client().get(ctx.target(), measurementOids));
var task = new SnmpTask("device-status", target, List.of(measurements));
var result = executor.submit(task, taskOptions).await();
var values = result.value(measurements);
```

- Submit work for all devices before waiting, so different devices can run concurrently.
- Missing values remain explicit SNMP exception values; check them before using measurements.
- Your application owns polling schedules, permissions and storage.

## Results

- `result.summary()`: short message and each step's status.
- `result.status()`, `result.writeState()` and typed `result.value(step)`: use these for application logic.
- Verified device procedures return `SnmpWriteResult<T>`; a plain SET acknowledges receipt only.
- `Ntcip1218ForwardingTasks.changed(result)`: `true` for a verified change,
  `false` for a delete no-op, `null` if the procedure did not finish.
- A later database failure does not erase a verified device change.
- SET is never retried automatically. Check device state before retrying an uncertain write.

- Tasks for one IP/port run one at a time within an executor. Other devices can run in parallel.
- Separate executors or application replicas need their own coordination for writes.
- Walks use GETNEXT by default. Optional GETBULK can reduce round trips; benchmark it on your devices.

## Default limits

| Setting | Default | Configured by |
| --- | --- | --- |
| Concurrent operations | 24 | `Snmp4jOptions` |
| Cached credential profiles | 32 | `Snmp4jOptions` |
| Peers per session | 1,024 | `Snmp4jOptions` |
| Request timeout / read retries | 5 seconds / 1 | `SnmpRequestPolicy` or adapter defaults |
| Whole-operation deadline | 60 seconds | `Snmp4jOptions` |
| Adapter admission wait | 5 seconds | `Snmp4jOptions` |
| Walk limits | 16,384 bindings / 4 MiB OID/value payload | `Snmp4jOptions` |
| Running / queued tasks | 24 / 1,024 | `SnmpExecutorOptions` |
| Queue / execution timeout | 5 minutes / 60 seconds | `SnmpTaskOptions` |
| Recorded calls per task | 1,024 | Five-argument `SnmpTaskOptions` constructor |

Applications can override these defaults. Executor concurrency must fit
`client.concurrencyLimit()`, the smaller of the adapter's operation and session
limits. Sharing that client with direct calls can still produce `BUSY` results.

## Summary and logs

`result.summary()` includes task ID, target, status, message, current/failed step,
`elapsedMs`, and each declared step's name/status. It excludes command payloads.

```text
INFO SNMP create-message-forwarding.BSM.RECEIVE - SUCCEEDED: Change verified. [task=<id> target=udp:192.0.2.10:161 elapsed=900ms] [queue=100ms run=800ms requests=12 steps=configure-forwarding=200ms,read-current-forwarding=550ms,sync-database=50ms]
```

- `elapsed` includes queue wait, all SNMP work and callbacks. Step timings cover only steps that started.
- `requests` counts adapter submissions, not every network packet.
- SLF4J logs use the application's provider. The library ships no production logging configuration.
- DEBUG: reads, offline timeouts, queue rejection/expiry and cancellation.
- INFO: successful writes. WARN: uncertain writes, incomplete/failed verification and unexpected failures.
- TRACE: request/response events, selected rows and engine recovery. Nested calls share correlation IDs without repeating outcome warnings.
- Credentials, SNMP values and raw exceptions are excluded from library logs. Logs cannot prove device state after a lost reply.

For the Intersection API, set `logging.level.us.dot.its.jpo.snmp` to `DEBUG` or `TRACE`.

See [adding recipes](docs/recipes.md) for new device procedures.

## Build and test

From the repository root, with Java 22 and configured Maven settings:

```powershell
mvn -f services/intersection-api/snmp/pom.xml clean verify
```

- Use `install` for a separate local application build.
- Tests use local SNMPv3 agents; no physical device is required.
- Docker interoperability tests use a Net-SNMP agent.
- CI includes SonarCloud analysis and coverage.

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
| Read known OIDs together      | [`SnmpClient.get(target, oids)`](src/main/java/us/dot/its/jpo/snmp/client/SnmpClient.java)  |
| Read a table                  | [`SnmpClient.walk(target, root)`](src/main/java/us/dot/its/jpo/snmp/client/SnmpClient.java) |
| Queue and track device work   | [`SnmpTaskExecutor`](src/main/java/us/dot/its/jpo/snmp/execution/SnmpTaskExecutor.java)              |
| Add/delete message forwarding | [`Ntcip1218ForwardingTasks`](src/main/java/us/dot/its/jpo/snmp/rsu/ntcip1218/Ntcip1218ForwardingTasks.java)      |

A **recipe** builds a [`SnmpTask`](src/main/java/us/dot/its/jpo/snmp/execution/SnmpTask.java): named steps for one device. The executor runs it.

## Targets and credentials

- [`Ntcip1218Device(address, credentials)`](src/main/java/us/dot/its/jpo/snmp/rsu/ntcip1218/Ntcip1218Device.java) uses port 161; pass a port for a custom endpoint.
- Generic calls use [`SnmpTarget`](src/main/java/us/dot/its/jpo/snmp/client/SnmpTarget.java). `device.target()` returns the underlying target.
- Construction does no I/O. `Ntcip1218Device.preflight` validates inventory profiles before a batch runs.
- Each target supplies its credentials. No username is assumed.
- SNMPv3 defaults to SHA/AES-128. SHA-256/SHA-512 and DES are also supported.
- An empty privacy password falls back to the authentication password.
- Addresses are literal IPv4; OIDs are numeric. Missing values stay explicit SNMP exception values.

Sessions are cached by the full credential profile, including both passwords and
algorithms. Identical profiles share a transport; different profiles have separate
security state. Each peer keeps its own engine state. No global USM is replaced.

- Idle profiles and peers can be evicted; active requests keep their state. Full caches return `BUSY` only when no idle entry can make room.
- `close()` releases sockets, pending requests and timers.
- Public types do not expose SNMP4J types or credentials in their string representations.

## Configure Message Forwarding

Supports BSM, SPAT, MAP, SSM, SRM and TIM. The example below uses MAP;
change [`MessageType`](src/main/java/us/dot/its/jpo/snmp/rsu/ntcip1218/MessageType.java) to select another message type and its port/table.

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

- Uses UDP and the jpo-ode standard MAP port. `true` includes IEEE 1609.2 security headers.
- Verifies only the changed row after the write. Schedules always use UTC.
- Use `createWithSnapshot` or `deleteWithSnapshot` to also read both forwarding tables.
  Pass a [`SnapshotAction`](src/main/java/us/dot/its/jpo/snmp/rsu/ntcip1218/Ntcip1218ForwardingTasks.java) when the application needs to save that snapshot. This can be used for synchronizing the results with PostgreSQL.
- Each create adds a row. Delete with `forwardingTasks.delete(device, MessageType.MAP, "198.51.100.3")`.
- Use the table/index overload of `deleteWithSnapshot` to remove a manually configured row by its actual table/index,
  checking the displayed destination and port.
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
- Verified device procedures return [`SnmpWriteResult<T>`](src/main/java/us/dot/its/jpo/snmp/client/SnmpWriteResult.java); a plain SET acknowledges receipt only.
- `Ntcip1218ForwardingTasks.changed(result)`: `true` for a verified change,
  `false` for a delete no-op, `null` if the procedure did not finish.
- A later database failure does not erase a verified device change.
- SET is never retried automatically. Check device state before retrying an uncertain write.

- Tasks for one IP/port run one at a time within an executor. Other devices can run in parallel.
- Separate executors or application replicas need their own coordination for writes.
- Walks use GETNEXT by default. Optional GETBULK can reduce round trips; benchmark it on your devices.

## Default limits

| Setting                        | Default                                   | Configured by                               |
| ------------------------------ | ----------------------------------------- | ------------------------------------------- |
| Concurrent operations          | 24                                        | [`Snmp4jOptions`](src/main/java/us/dot/its/jpo/snmp/snmp4j/Snmp4jOptions.java)                             |
| Cached credential profiles     | 32                                        | [`Snmp4jOptions`](src/main/java/us/dot/its/jpo/snmp/snmp4j/Snmp4jOptions.java)                             |
| Peers per session              | 1,024                                     | [`Snmp4jOptions`](src/main/java/us/dot/its/jpo/snmp/snmp4j/Snmp4jOptions.java)                             |
| Request timeout per attempt / read retries | 5 seconds / 1                             | [`SnmpRequestPolicy`](src/main/java/us/dot/its/jpo/snmp/client/SnmpRequestPolicy.java) or adapter defaults     |
| Whole-operation deadline       | 60 seconds                                | [`Snmp4jOptions`](src/main/java/us/dot/its/jpo/snmp/snmp4j/Snmp4jOptions.java)                             |
| Adapter admission wait         | 5 seconds                                 | [`Snmp4jOptions`](src/main/java/us/dot/its/jpo/snmp/snmp4j/Snmp4jOptions.java)                             |
| Walk limits                    | 16,384 bindings / 4 MiB OID/value payload | [`Snmp4jOptions`](src/main/java/us/dot/its/jpo/snmp/snmp4j/Snmp4jOptions.java)                             |
| Running / queued tasks         | 24 / 1,024                                | [`SnmpExecutorOptions`](src/main/java/us/dot/its/jpo/snmp/execution/SnmpExecutorOptions.java)                       |
| Queue / execution timeout      | 5 minutes / 60 seconds                    | [`SnmpTaskOptions`](src/main/java/us/dot/its/jpo/snmp/execution/SnmpTaskOptions.java)                           |
| Recorded calls per task        | 1,024                                     | Five-argument [`SnmpTaskOptions`](src/main/java/us/dot/its/jpo/snmp/execution/SnmpTaskOptions.java) constructor |

- Request timeouts apply to each attempt, not the whole task. Reads allow one retry by default; SET has none.
- Five seconds is a starting point. Tune `SnmpRequestPolicy` (or `Snmp4jOptions` for direct calls) for your network, along with task/operation deadlines. Longer timeouts also hold capacity longer for offline devices.

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

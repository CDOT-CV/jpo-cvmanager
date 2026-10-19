# JPO SNMP

Java SNMP library for Connected Vehicle devices.

- Requires Java 22; uses SNMP4J internally.
  - JDK 22 was chosen for compatibility with the Intersection API. This library can move to JDK 25 when the Intersection API does.
- Supports generic SNMPv3 operations with numeric OIDs and IPv4 targets.
- No Spring or database dependency.
- Maven: `usdot.jpo.ode:jpo-snmp:1.0.0-SNAPSHOT`.

## Read device values

Load credentials and check permissions in your application first.

```java
var credentials = new SnmpCredentials(username, authenticationPassword, privacyPassword);
var target = new SnmpTarget("192.0.2.10", credentials);
var uptime = new SnmpStep<>("uptime", SnmpStep.Kind.READ,
    ctx -> ctx.client().get(ctx.target(), List.of(new SnmpOid("1.3.6.1.2.1.1.3.0"))));

try (var client = new Snmp4jClient();
     var executor = new SnmpTaskExecutor(client, SnmpExecutorOptions.defaults())) {
    var task = new SnmpTask("device-status", target, List.of(uptime));
    var result = executor.submit(task, SnmpTaskOptions.defaults()).await();
    if (result.status() == SnmpTaskResult.Status.SUCCEEDED) {
        var values = result.value(uptime);
    }
    System.out.println(result.summary());
}
```

- Group known OIDs in one GET. Use `walk(target, root)` for tables.
- Check missing SNMP values before using measurements.
- Submit tasks for all devices before waiting, so different devices can run concurrently.
- Reuse the client and executor in a server. Close the executor before the client.
- Imports come from `client`, `execution`, and `snmp4j` under `us.dot.its.jpo.snmp`, plus `java.util`.

## Targets and credentials

- `SnmpTarget(address, credentials)` uses port 161; pass a port for a custom endpoint.
- Each target supplies its credentials. No username is assumed.
- SNMPv3 defaults to SHA/AES-128; SHA-256/SHA-512 and DES are also supported.
- An empty privacy password falls back to the authentication password.
- Sessions use the full credential profile. Different passwords never share security state.
- Idle sessions can be evicted; active sessions keep their state. Full caches may return `BUSY`.

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

- Executor concurrency must fit `client.concurrencyLimit()`.
- Tasks for one IP/port run one at a time within an executor. Separate executors need their own write coordination.
- Walks use GETNEXT by default. Set `maxBulkRepetitions` to 1-64 for GETBULK; benchmark it on your devices.
- Your application owns polling schedules, permissions, storage and result retention.

## Summary and logs

- `result.summary()` includes task ID, target, status, message, timings and each step's name/status.
- Use typed status fields and `result.value(step)` for application logic.
- Verified procedures return `SnmpWriteResult<T>`. A plain SET acknowledges receipt only.
- SET is never retried automatically. Check device state before retrying an uncertain write.
- `cancel()` requests interruption. `await(Duration)` limits only the caller's wait.

Logs use the application's SLF4J provider and exclude credentials and SNMP values.

- DEBUG: reads, offline timeouts, queue rejection/expiry and cancellation.
- INFO: successful writes. WARN: uncertain writes, incomplete verification and unexpected failures.
- TRACE: request/response events and engine recovery.
- Task logs separate queue/run time and step timings. Request counts exclude internal discovery packets and retransmissions.

See [adding recipes](docs/recipes.md) for device procedures.

## Build and test

From the repository root, with Java 22 and configured Maven settings:

```powershell
mvn -f services/intersection-api/snmp/pom.xml clean verify
```

- Use `install` for a separate local application build.
- Tests use local SNMPv3 agents; no physical device is required.
- CI includes SonarCloud analysis and coverage.
- Run independent Net-SNMP interoperability tests with:

```powershell
docker build -f services/intersection-api/snmp/Dockerfile.integration -t jpo-snmp-integration services
docker run --rm jpo-snmp-integration
```

# JPO SNMP

A reusable Java library for talking to connected-vehicle devices over SNMP.
It handles device communication, timeouts and execution tracking so applications
can focus on what they want the device to do.

The library uses SNMP4J internally and requires Java 22. It has no Spring or
database dependency. Generic SNMP operations work with numeric OIDs; the NTCIP
1218 helpers add RSU-specific behavior. RSU 4.1 is not supported.

## Where to start

| Your goal | Use |
| --- | --- |
| Read known status values together | `SnmpClient.get(target, oids)` |
| Read a device table | `SnmpClient.walk(target, root)` |
| Run tracked work on many devices | `SnmpTaskExecutor` |
| Add or delete RSU message forwarding | `Ntcip1218ForwardingTasks` |
| Build another device procedure | Compose named steps into a `SnmpTask` |

Think of a **recipe** as a method that builds a task. A task describes one device's
work; the executor runs it. Recipes do not own connections or start background jobs.

## Configure MAP forwarding

The application first loads the device's credentials and checks permission.
The following example assumes `username`, `authenticationPassword` and
`privacyPassword` came from that lookup:

```java
var credentials = new SnmpCredentials(username, authenticationPassword, privacyPassword);
var device = new Ntcip1218Device("192.0.2.10", credentials);
var recipes = new Ntcip1218ForwardingTasks(Clock.systemUTC(), ZoneId.of("UTC"));

try (var client = new Snmp4jClient();
     var executor = new SnmpTaskExecutor(client, SnmpExecutorOptions.defaults())) {
    var task = recipes.create(device,
        new ForwardingRequest(MessageType.MAP, "10.235.1.3", true));
    var result = executor.submit(task, SnmpTaskOptions.defaults()).await();
    System.out.println(result.summary());
}
```

This configures UDP forwarding to the standard MAP port, verifies the changed
row, then reads both forwarding tables. The final `true` includes IEEE 1609.2
security headers in forwarded messages. Forwarding start and stop timestamps
always use UTC, as required by NTCIP 1218. The constructor's timezone argument
is retained for compatibility and does not change these timestamps.

Delete uses `recipes.delete(device, MessageType.MAP, "10.235.1.3")`. No matching
entry is a successful no-op. Repeating a create allocates another entry; it is
not an update operation.

In a server, create the client, executor and recipe factory **once**, reuse them,
and close the executor before the client on shutdown. The example's resource
scope illustrates their ownership, not a requirement to recreate them per request.
Imports are from `client`, `execution`, `snmp4j` and `rsu.ntcip1218` under
`us.dot.its.jpo.snmp`, plus `java.time` and `java.util` as needed.

## Read status values

For measurements such as temperature and uptime, request the known OIDs together
rather than walking an entire table:

```java
var task = new SnmpTask("device-status", target, List.of(
    new SnmpStep("measurements", SnmpStep.Kind.READ,
        ctx -> ctx.client().get(ctx.target(), measurementOids))));
var handle = executor.submit(task, taskOptions);
var result = handle.await();
```

`target` is a generic `SnmpTarget(address, credentials)`; it does not require
NTCIP. `measurementOids` is the list of OIDs appropriate to the device. Submit
work for all devices before waiting for results so the executor can run different
devices concurrently. Missing SNMP values remain explicit exception values;
a completed request does not mean every requested measurement exists.

Polling schedules, freshness rules and storage belong to the application.
The RSU status monitor is a future consumer, not part of this library.

## Understand the result

`result.summary()` gives a short message and the status of each named step.
Use `result.status()`, `result.writeState()` and the step results for application
logic. A task can fail after the device change was already verified, for example
if a later database callback fails.

For forwarding, `Ntcip1218ForwardingTasks.changed(result)` returns:

- `true`: a change was confirmed.
- `false`: deletion found no matching entry.
- `null`: the configuration procedure did not complete.

An uncertain write must be checked on the device before retrying. The library
never automatically retries SET requests. A timeout or cancellation is not proof
that a write did not happen.

## Why it is organized this way

| Package | Responsibility |
| --- | --- |
| `client` | Device targets, credentials, typed values and generic operations |
| `snmp4j` | Connections, security isolation and response validation |
| `execution` | Queues, deadlines, cancellation and task/step results |
| `rsu.ntcip1218` | NTCIP 1218 values, procedures and forwarding recipes |
| `internal` | Shared logging and safe result messages |

Future recipes should live alongside the device/protocol helpers they use.
They return the same `SnmpTask` type and use `ctx.client()` for every SNMP call.
This preserves deadlines and tracking without coupling the executor to a feature.
There is no recipe base class or registry to extend.

Forwarding's optional `SnapshotAction` lets the application save a complete
snapshot after successful device verification. It contains no PostgreSQL code.
Other recipes can expose callbacks suited to their own results when needed.
See [adding recipes](docs/recipes.md) for the pattern and important safeguards.

## Limits and deployment choices

Defaults allow 24 running tasks, 1,024 queued tasks, a five-minute queue wait
and a 60-second execution budget. Request timeouts default to five seconds with
one read retry. The Intersection API supplies its own queue and policy settings.

Tasks for the same IP/port run one at a time within an executor. Offline devices
consume their timeout budget; other eligible devices can use the remaining slots.
Multiple application replicas need their own coordination for device writes.

Table walks use GETNEXT by default. Optional GETBULK can reduce round trips on
higher-latency networks, but some RSUs process it more slowly. Benchmark before
enabling it. Complete snapshots still require valid, complete rows. Forwarding
verification uses a targeted GET of the changed row instead of another full walk.

The [technical reference](docs/reference.md) covers policy settings, bulk page
sizes, shutdown behavior and the distinction between waiting and cancellation.

## Build and test

From the repository root, using Java 22 and your configured Maven settings:

```powershell
mvn -f services/intersection-api/snmp/pom.xml clean verify
```

Use `install` instead of `verify` to make the library available to a separate
local application build. Its Maven coordinates are
`usdot.jpo.ode:jpo-snmp:1.0.0-SNAPSHOT`. The Intersection API reactor and Docker
build include it automatically.

Normal tests use disposable local SNMPv3 agents and need no physical devices.
An additional Net-SNMP interoperability suite runs in Docker; instructions are in
the [technical reference](docs/reference.md#tests-and-hardware-validation).
Repository CI includes the library in its SonarCloud analysis.

The library uses your application's SLF4J configuration. Routine reads and offline
timeouts log at DEBUG; successful writes at INFO; uncertain writes and unexpected
failures at WARN. Credentials and SNMP values are excluded from library logs.
Task logs separate queue wait from execution and include step timings. Forwarding
task names identify the message type and receive/transmit table. The reported
total duration includes complete snapshot reads and any application callback;
it is not just the time spent sending and verifying a change.

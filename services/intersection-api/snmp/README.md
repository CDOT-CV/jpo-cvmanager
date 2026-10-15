# JPO SNMP library

Standalone Java 22 Maven library with SNMP4J 3.9.3 as its only production backend.
No Spring, HTTP, database, shell commands, credential files, or runtime MIB parser.
`SnmpClient` exposes typed GET, GETNEXT, WALK and ordered multi-binding SET.
`Ntcip1218Client` supplies NTCIP 1218 forwarding, location, status, firmware-version
reads, and certificate-hour reads. RSU 4.1 is intentionally unsupported.

## Build and consume

From the repository root:

```powershell
mvn -f services/intersection-api/snmp/pom.xml clean verify
mvn -s services/intersection-api/api/settings.xml -f services/intersection-api/pom.xml -pl api -am verify
```

The second command builds the library before the Intersection API, using its
existing Maven package settings. The aggregator is not a shared parent. For an
API-only build or IDE run, first run `mvn -f services/intersection-api/snmp/pom.xml install`.
The API Docker build installs the library automatically. No command endpoints
or status-monitor implementation are changed by this library dependency.

## Usage and ownership

```java
try (var transport = new Snmp4jClient(Snmp4jOptions.defaults())) {
    // The consuming application retrieves these credentials from PostgreSQL.
    var credentials = new SnmpCredentials(username, authenticationPassword, privacyPassword);
    var device = new Ntcip1218Device("192.0.2.10", credentials);
    var rsu = new Ntcip1218Client(transport, Clock.systemUTC(), ZoneId.of("America/Denver"));

    var configuration = rsu.readForwarding(device);
    var row = rsu.addForwarding(device,
        new ForwardingRequest(MessageType.BSM, "198.51.100.20", true));
    boolean removed = rsu.deleteForwarding(device, MessageType.BSM, "198.51.100.20");
}
```

In a server, share one transport and RSU client across requests and close the
transport on application shutdown, rather than opening one for each request.
Authentication, selected-organization authorization, credential lookup, periodic
scheduling, durable jobs, and PostgreSQL reconciliation belong to the consuming
application. The library contains no request security context. Preflight the
entire SNMP batch with `Ntcip1218Device.preflight` before executing any member.
Unsupported/missing inventory profiles fail batch preflight without I/O.
`Ntcip1218Device(target)` represents an already selected NTCIP 1218 device; the
standard is fixed by its type, not a configurable profile field.
Construct directly with `(address, credentials)` (SNMP port 161) or
`(address, port, credentials)` for a custom SNMP port. Generic operations still
use `SnmpTarget`; `device.target()` exposes the same target when needed. SSH operations
have separate capability checks and are not subject to this SNMP profile gate.

Credentials are supplied per target. No username is assumed or supplied by the
library. A missing/empty privacy password falls back to the authentication
password for compatibility. SNMPv3 authPriv defaults to SHA/AES-128; explicit
SHA-256/SHA-512 and DES are also supported. Literal IPv4 addresses and numeric
OIDs match the current API requirements. Generic values preserve octets, integer
types, unsigned counters, OIDs, IP addresses, NULL and exception values. Public
interfaces and NTCIP helpers do not expose SNMP4J types. No-such-object/instance
never becomes a measurement such as 128/129; scalar helpers reject those values.

## Security isolation and session reuse

Sessions are cached by a private fingerprint of the complete supplied credential
profile: username, authentication/privacy passwords, and algorithms. Different
usernames or passwords produce independent sessions. Identical credential
profiles can reuse a transport across devices, with peer-specific engine state.
Each session owns its protocol registry, security-model registry, USM, counters,
message dispatcher, socket and timer. No global registry is replaced or populated.
Profiles are never indexed by username alone. Credentials are excluded from
public credential representations and adapter exception messages.

The LRU cache evicts only idle sessions. When a profile reaches its distinct-peer
limit, it rotates only when idle; otherwise admission fails explicitly with BUSY.
Active sessions never lose security state to eviction. Rotation bounds engine,
localized-user and timing caches without mutating another active session.
All sockets, pending requests and timers are released by `close()`.

## Deadlines, limits and errors

Defaults: 24 concurrent operations, 32 cached credential profiles, 1,024 distinct
peer endpoints per session, 5-second request timeout, one read protocol retry,
60-second whole-operation deadline, 5-second admission timeout, 16,384 walk
bindings and 4 MiB of walk OID/value payload. `SnmpTaskExecutor` supplies optional
bounded queueing above the adapter. Direct calls retain admission limits. Walks currently
use GETNEXT to match Python behavior and avoid unvalidated GETBULK assumptions.
The deadline covers admission and the complete walk, not each individual step.

The synchronous API waits for actual response completion. Internally requests
are asynchronous and explicitly canceled after every completion, timeout or
interruption; request handles are not keyed by mutable PDUs. Diagnostics report
completed/failed operations, active operations, pending wire requests and cached
sessions. Successful protocol reads containing no-such-instance count as completed
operations, not successful application measurements.

SET has zero automatic protocol retries. A lost reply, interrupted write, failed
verification, failed commit/undo, or malformed reply marks the outcome unknown;
inspect device state before retrying. Forwarding adds are new allocations, so
blindly repeating an add can create duplicates. Engine discovery/synchronization
reports may be handled by SNMP4J before an agent can accept a request. For a
previously known peer that times out, reads may perform one fresh endpoint engine
discovery and retry within the original deadline; SET never takes this recovery
path. A normal agent reboot and a replacement with a changed engine ID are covered
by real UDP tests. Permanent wrong credentials may manifest as timeouts if the
agent does not send an actionable report.

`SnmpException.Kind` distinguishes timeout, authentication/report rejection,
agent rejection, malformed responses, resource limits, cancellation and failed
verification. Agent error status/index are available as optional integers; raw
library exceptions and credential-bearing target strings are never attached.
Walks reject non-increasing OIDs and do not return partial success after failure.

`ForwardingRequest.includeSecurity` controls whether forwarded messages
include IEEE 1609.2 security headers (`true`) or omit them (`false`). The same
setting is exposed by `ForwardingRow.includeSecurity()`. It does not
select UDP/TCP or change SNMPv3 authentication/privacy.

Forwarding snapshots require complete typed rows. Add/delete verify readback;
allocation retains max+1 with the 127 compatibility cap. Delete removes the
highest matching message/destination row; no match returns false without SET.
Date encoding uses configured device-zone local time with minute precision;
Java normalizes leap-day plus ten years. The RSU helper uses local lock stripes
around discovery/allocation/write/verification. Cross-instance and external-device
writers require application coordination. No exactly-once guarantee is made.

## Generic task execution

`SnmpTaskExecutor` accepts a finite sequence of named steps for one target. It
borrows a `SnmpClient` and owns its bounded queue, virtual-thread workers and
deadline timer. It contains no RSU inventory, database access or periodic polling
schedule. Submit tasks for all devices, then await or inspect their handles;
normal queueing does not consume the adapter's admission timeout.

```java
var taskOptions = new SnmpTaskOptions(
    Duration.ofMinutes(4),                 // maximum queue wait
    Duration.ofSeconds(20),                // entire device task after dispatch
    new SnmpRequestPolicy(Duration.ofSeconds(2), 1, false),
    0);                                   // optional adapter-submission cap; 0 disables

try (var transport = new Snmp4jClient();
     var executor = new SnmpTaskExecutor(transport, SnmpExecutorOptions.defaults())) {
    var task = new SnmpTask("status-snapshot", target, List.of(
        new SnmpStep("measurements", SnmpStep.Kind.READ,
            ctx -> ctx.client().get(ctx.target(), measurementOids))));
    var handle = executor.submit(task, taskOptions);
    SnmpTaskResult progress = handle.snapshot();
    SnmpTaskResult completed = handle.await(Duration.ofSeconds(30));
}
```

Task/step names are short symbolic labels, not descriptions containing secrets.
An executor dispatches at most one task per endpoint (IP/port), independent of
username. It selects the oldest eligible queued task, so an endpoint already
running does not block other devices. Defaults are 24 dispatched tasks and 1,024
waiting tasks. Tasks on the same device execute in order; periodic-poll
coalescing belongs to the service. Direct client calls, other executors, other
processes and external device writers are outside this coordination boundary.

Concurrency must not exceed `client.concurrencyLimit()`, which for SNMP4J is the
smaller of its operation and credential-session limits. Raise those limits
together for larger deployments. Sharing that same client with unrelated direct
calls can still cause transport BUSY results. For independently configurable
workloads, use separately owned clients/executors with deliberate total limits.

Queue expiry and queue rejection are terminal local outcomes, distinct from
device TIMEOUT. The execution deadline starts when dispatched and includes
session acquisition, all steps, all calls, read retries, WALK exchanges and
engine rediscovery. Each call is additionally bounded by the adapter's own
operation deadline. `ctx.remainingTime()` and `ctx.checkActive()` expose the
remaining task budget. Cancellation/budget checks run before every new step and
adapter submission. A submission racing a stop request may already be entering
SNMP4J and must be treated as potentially dispatched. A failed command stops the task even if its action catches
the exception; it cannot be hidden or blindly replayed inside that step.

`SnmpRequestPolicy` controls request timeout, read retries and whether a known
peer's timeout gets one fresh engine-discovery retry. Recovery is coordinated
per endpoint within each credential session; concurrent recovery shares a
generation rather than repeatedly invalidating the same discovery state.
Policies are scoped to each call and do not mutate shared transport defaults.
Disabling immediate rediscovery avoids an extra read cycle on timeout; it marks
that peer's cached discovery state stale so the next read can discover an agent
replacement. A stale cache cannot permanently pin monitoring to the old engine.
SET always has zero automatic retries and never takes read rediscovery recovery.
`maxRequests` counts adapter submissions, including WALK steps and discovery
recovery attempts. SNMP4J protocol retransmissions and discovery/report packets
are not separate submissions; this is not a literal packet-count limit.

Task history independently caps generic calls at `maxRecordedCommands` (default
1,024; configurable using the five-argument `SnmpTaskOptions` constructor).
Reaching it fails with OUTPUT_LIMIT before another call. WALK's existing byte
and binding limits still apply. Retained response sizes and arbitrary local
action outputs also consume memory: choose suitable history/request limits and
keep local return values small and immutable. There is no global completed-task
history cache; consumers control the retention/persistence of handles/results.

Use `ctx.client()` for every call, including the client supplied to an
`Ntcip1218Client` created inside an action. The task client enforces its original
target/credentials and is valid only on its running worker. Do not capture an
unscoped client or spawn concurrent child calls. SET is allowed only in WRITE
steps. READ/WRITE/LOCAL actions return their result; VERIFY actions return
Boolean.TRUE only after checking their intended state (false produces
VERIFICATION_FAILED). Earlier values are available through
`ctx.value("step-name", SomeClass.class)`.

## Execution tracking and cancellation

`handle.snapshot()` includes the task ID, endpoint, status, timestamps, monotonic
queue/execution durations, stop-request flags, every declared step and each
underlying generic call. Steps never entered stay NOT_STARTED. Completed steps
and call responses remain available after later failure. Calls retain requested
OIDs/SET bindings, confirmed response bindings, submission counts, safe failure
categories and agent error status/index. No credentials or exception causes are
included. Result `toString()` implementations omit payloads; consumers must also
avoid indiscriminately logging or publishing result values.

| Result evidence | Task write state |
| --- | --- |
| No SET entered the adapter | NOT_SUBMITTED |
| SET explicitly rejected, no acknowledged write | REJECTED |
| SET reply lost/malformed, or cancellation during SET | UNKNOWN |
| SET acknowledged, no successful subsequent declared verification | ACKNOWLEDGED_UNVERIFIED |
| A subsequent VERIFY step confirmed the caller's intended state | VERIFIED |
| A verification check completed and reported a mismatch | VERIFICATION_FAILED |

Verification semantics belong to the procedure: the executor cannot infer that
an arbitrary successful GET verifies a prior write. A whole NTCIP add/delete
can be a WRITE step, with its underlying WALK/SET/WALK calls tracked. Its own
readback still runs; a subsequent VERIFY step can explicitly check the returned
verified row/boolean if the task needs a VERIFIED summary. Otherwise that
summary conservatively remains ACKNOWLEDGED_UNVERIFIED. Always inspect the
step and command results alongside the task summary.

`handle.cancel()` requests interruption and stops further commands.
`handle.await()` returns the final result, including CANCELLED; it does not
discard completed evidence by throwing Future cancellation. A timeout in
`await(Duration)` only ends the caller's wait and does not cancel the task.
Cancellation or deadline expiry cannot undo an already dispatched SET.

Local Java actions must cooperate with interruption or call `checkActive()`.
If one ignores interruption, its snapshot remains RUNNING with a stop flag until
it actually exits; its device and concurrency slot remain occupied. The executor
does not fabricate completion or start overlapping work. `close()` cancels queued
tasks, requests running-task cancellation and waits up to its shutdown timeout
(default five seconds). Diagnostics reveal work still running afterward. Close
the borrowed client separately, after shutting down its executor.

Task diagnostics include queue/running/device counts and dispatched completion,
success/failure/timeout, rejection, expiry and cancellation totals. Completed
counts dispatched tasks; cancelled totals also include queued cancellations.
The consuming service owns five-minute cadence, jitter, repeated-offline
backoff, cross-instance ownership, PostgreSQL persistence and alerts. Neither
monitoring nor command endpoints are migrated by adding this execution layer.

## Compact results

Use `result.summary()` for a minimal application response, and keep the full
`SnmpTaskResult` for troubleshooting and reconciliation. Both are immutable views;
the full command/step history and existing status/write-state enums are preserved.
The summary includes minimal step entries and excludes payloads and repeated confirmation flags:

```json
{
  "taskId": "<task-id>",
  "target": "udp:192.0.2.10:161",
  "status": "TIMED_OUT",
  "message": "Change acknowledged; verification timed out.",
  "step": "verify-row",
  "elapsedMs": 2030,
  "steps": [
    { "name": "read-existing", "status": "SUCCEEDED" },
    { "name": "create-row", "status": "SUCCEEDED" },
    { "name": "verify-row", "status": "READ_TIMED_OUT" },
    { "name": "later", "status": "NOT_STARTED" }
  ]
}
```

This is the shape of `handle.await().summary()`, not an implemented HTTP endpoint.
`elapsedMs` includes queue wait and execution. `step` identifies running/failed
work and is null when none is relevant. `steps` lists every declared step in order,
including steps not started, with only its name and status. The list is immutable;
command details and values remain in the full result. Messages distinguish an unconfirmed write,
acknowledged change, incomplete verification and confirmed mismatch in plain
language. Running work with a pending stop remains explicitly running. Use typed
status/write-state fields for program logic; message text is for presentation.
`SnmpTaskResult.toString()` also uses the compact summary without printing values
or expanding the step list, keeping normal log lines short.

## Logging

The library uses SLF4J 2, matching the Intersection API. It ships no production
logging backend or output configuration; standalone consumers supply their own
SLF4J provider. Logback is a test-only dependency. Loggers are under
`us.dot.its.jpo.snmp`. The library does not enable SNMP4J packet logging.

Normal logs use one human-readable task summary, matching `result.summary()`:

```text
WARN SNMP configure-forwarding - TIMED_OUT: Change acknowledged; verification timed out. [task=<id> target=udp:192.0.2.10:161 step=verify-row elapsed=2030ms]
```

Routine successful reads, normal offline-device timeouts, queue rejection/expiry
and cancellation remain DEBUG so fleet polling stays quiet at INFO. Successful
write tasks log at INFO. Uncertain writes, failed/incomplete verification after
an acknowledged write, invalid responses and non-routine failures log at WARN.
Nested operations do not repeat INFO/WARN messages: the outer task/procedure
provides the actionable summary. Standalone client/procedure calls still log
their own terminal outcome, using the same readable wording.

DEBUG shows individual operation starts/outcomes. TRACE adds steps, selected rows,
request submission/response and engine recovery. Diagnostic lines include an
operation ID, optional parent ID, target and elapsed time; failures add the step,
last confirmed step and agent error/index when available. Optional/empty fields
are omitted. Task IDs serve as root operation IDs; nested synchronous calls link
through their parent. Scope cleanup prevents stale correlation on reused threads.
Arbitrarily spawned child threads do not inherit that execution context.

In the Intersection API, use `logging.level.us.dot.its.jpo.snmp=DEBUG` for call
outcomes or `TRACE` for detailed request diagnostics. Request submission records
entering SNMP4J, not proof of delivery; protocol retransmissions are not separate
adapter submissions. Raw exception text, stack traces, credentials, fingerprints
and SNMP values are excluded at every level. Logs do not establish device state
after a lost reply or replace persisted job results.

## Tests and hardware validation

CI includes this module in the repository-wide SonarCloud analysis. Maven `verify`
generates coverage at `target/site/jacoco/jacoco.xml`; CI supplies this report and
compiled production/test classes to the shared scan.

Normal `verify` requires only Java/Maven and localhost UDP sockets. Disposable
SNMPv3 agents test encoded/encrypted traffic, mixed usernames and passwords,
100 concurrent calls, session reuse/limits, actual completion, agent errors,
missing/invalid values, timeouts, cancellation, engine changes, lost SET replies,
and NTCIP forwarding lifecycle. Twelve golden NTCIP request cases retain the
frozen Python message mapping, order, security settings and date/IP encodings.
MIB catalog tests read `../../resources/mibs/NTCIP1218-v01.txt`; no duplicated MIB
sources are maintained and no MIB files are required in the API runtime.

For interoperability with an independent native Net-SNMP agent:

```powershell
docker build -f services/intersection-api/snmp/Dockerfile.integration -t jpo-snmp-integration services
docker run --rm jpo-snmp-integration
```

This test-only image runs Net-SNMP `snmpd` on container loopback with disposable
credentials, exercising the Java adapter and NTCIP scalar reads. It enables
`JPO_SNMP_INTEGRATION=true`; the independent-agent test is otherwise skipped.
Net-SNMP is a validation tool, not a production fallback. The CI library job runs
both suites. Neither suite requires physical RSUs or contacts external devices.

Before migrating endpoints, validate deployed device models/versions and network
conditions using locally connected devices and measure realistic batch throughput.
The localhost tests establish protocol and lifecycle behavior, not CDOT network
performance or complete vendor compliance. Fleet-wide scheduled polling should
remain a separate service that can consume this library later.

Reference: [SNMP4J API documentation](https://www.agentpp.com/doc/snmp4j/org.snmp4j/org/snmp4j/Snmp.html).

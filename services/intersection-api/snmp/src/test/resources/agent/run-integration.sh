#!/bin/sh
set -eu
snmpd -f -Lo -C -c /workspace/intersection-api/snmp/src/test/resources/agent/snmpd.conf > /tmp/jpo-snmp-agent.log 2>&1 &
agent_pid=$!
trap 'kill "$agent_pid" 2>/dev/null || true' EXIT INT TERM
# SNMPv3 initialization must complete before the Java test starts.
attempt=0
until snmpget -v 3 -l authPriv -u fixture-user -a SHA -A fixture-auth-pass -x AES -X fixture-privacy-pass -t 1 -r 0 -m '' udp:127.0.0.1:1161 .1.3.6.1.2.1.1.1.0 >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 10 ]; then cat /tmp/jpo-snmp-agent.log; exit 1; fi
    sleep 1
done
JPO_SNMP_INTEGRATION=true mvn -f /workspace/intersection-api/snmp/pom.xml verify

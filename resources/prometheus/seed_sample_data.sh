#!/bin/sh
# Seeds Prometheus TSDB with synthetic kafka_produced_rsu_messages_total samples
# covering the last week for sample-data RSU IPs (see R__sample_data.sql).
# Used by the prometheus-sample-data service in docker-compose-intersection.yml.
#
# Sample data is static once imported. By default this script regenerates when the
# previous seed is older than MAX_SEED_AGE_SECONDS so the window stays near "now"
# (UI default range is the last 24h). Set FORCE_RESEED=true to always regenerate.
set -eu

MARKER="/prometheus/.cvmanager-sample-seeded"
OUT="/tmp/cvmanager_rsu_counts.om"
BLOCKS_DIR="/prometheus"

DAYS="${SAMPLE_DAYS:-7}"
# Must be strictly less than the Intersection API query step (120s) so
# increase(metric[120s]) sees ≥2 samples.
STEP="${SAMPLE_STEP_SECONDS:-30}"
FORCE_RESEED="${FORCE_RESEED:-false}"
# End this many seconds BEFORE now so backfill does not overlap the Prometheus head
# (~3h). Future/padded samples break live scrapes ("out of bounds") and overlapping
# head blocks. See Prometheus backfill docs.
HEAD_GAP_SECONDS="${HEAD_GAP_SECONDS:-10800}"
# Re-seed when marker is older than this (seconds), unless FORCE_RESEED=true.
# Default 1h keeps the trailing day populated for local use.
MAX_SEED_AGE_SECONDS="${MAX_SEED_AGE_SECONDS:-3600}"

if [ -f "$MARKER" ] && [ "$FORCE_RESEED" != "true" ]; then
  marker_mtime="$(date -r "$MARKER" +%s 2>/dev/null || echo 0)"
  now_ts="$(date +%s)"
  marker_age=$((now_ts - marker_mtime))
  if [ "$marker_mtime" -gt 0 ] && [ "$marker_age" -lt "$MAX_SEED_AGE_SECONDS" ]; then
    echo "Prometheus sample data is fresh (${marker_age}s old < ${MAX_SEED_AGE_SECONDS}s); skipping seed."
    echo "Set FORCE_RESEED=true to regenerate, or remove $MARKER."
    exit 0
  fi
  echo "Prometheus sample data is stale or missing age info (age=${marker_age}s); regenerating."
fi

echo "Clearing previous sample marker and TSDB contents under $BLOCKS_DIR before import..."
rm -f "$MARKER"
find "$BLOCKS_DIR" -mindepth 1 -maxdepth 1 ! -name "." -exec rm -rf {} + 2>/dev/null || true

NOW_TS="$(date +%s)"
END_TS=$((NOW_TS - HEAD_GAP_SECONDS))
START_TS=$((NOW_TS - DAYS * 24 * 3600))
if [ "$END_TS" -le "$START_TS" ]; then
  echo "Invalid seed window: END_TS ($END_TS) <= START_TS ($START_TS). Check HEAD_GAP_SECONDS/SAMPLE_DAYS."
  exit 1
fi

echo "Generating OpenMetrics samples from $START_TS to $END_TS (now=$NOW_TS, head_gap=${HEAD_GAP_SECONDS}s, step=${STEP}s, days=${DAYS})..."

# awk generates monotonically increasing counters per (rsu_ip, topic).
# Topic names match production OpenMetrics (prometheus_out.om) so CountsRepository
# can resolve BSM/MAP/SPAT/TIM/SRM/SSM input+output topics.
awk -v start="$START_TS" -v end="$END_TS" -v step="$STEP" '
BEGIN {
  n_rsu = split("10.0.0.11 10.0.0.12 10.0.0.13 10.0.0.14 10.0.0.15 10.0.0.16 10.0.0.17", rsus, " ")
  n_topic = split("topic.OdeRawEncodedBSMJson|topic.OdeBsmJson|topic.OdeRawEncodedMAPJson|topic.OdeMapJson|topic.OdeRawEncodedSPATJson|topic.OdeSpatJson|topic.OdeRawEncodedTIMJson|topic.OdeTimJson|topic.OdeRawEncodedSRMJson|topic.OdeSrmJson|topic.OdeRawEncodedSSMJson|topic.OdeSsmJson", topics, "|")

  print "# HELP kafka_produced_rsu_messages_total Total RSU messages produced to Kafka by topic"
  print "# TYPE kafka_produced_rsu_messages_total counter"

  for (ri = 1; ri <= n_rsu; ri++) {
    rsu = rsus[ri]
    for (ti = 1; ti <= n_topic; ti++) {
      topic = topics[ti]
      base = base_rate(topic, ri)
      counter = 0
      for (t = start; t <= end; t += step) {
        hour = int((t % 86400) / 3600)
        tod = (hour >= 6 && hour < 20) ? 1.0 : 0.35
        jitter = 1 + (((ri * 17 + ti * 13 + int(t / step)) % 7) - 3) * 0.05
        incr = int(base * tod * jitter + 0.5)
        if (incr < 1) incr = 1
        counter += incr
        printf "kafka_produced_rsu_messages_total{environment=\"local-dev\",job=\"ode-scraper\",instance=\"ode-scraper:8080\",host=\"ode-scraper\",app=\"jpoode-ode\",enabled=\"true\",rsu_ip=\"%s\",topic=\"%s\"} %d %d\n", rsu, topic, counter, t
      }
    }
  }
  print "# EOF"
}

function base_rate(topic, rsu_idx,   rate) {
  if (index(topic, "BSM") || index(topic, "Bsm")) rate = 180
  else if (index(topic, "SPAT") || index(topic, "Spat")) rate = 40
  else if (index(topic, "MAP") || index(topic, "Map")) rate = 8
  else if (index(topic, "TIM") || index(topic, "Tim")) rate = 3
  else if (index(topic, "SRM") || index(topic, "Srm")) rate = 2
  else if (index(topic, "SSM") || index(topic, "Ssm")) rate = 2
  else rate = 5
  return rate + (rsu_idx - 1) * 2
}
' > "$OUT"

LINE_COUNT="$(wc -l < "$OUT" | tr -d ' ')"
echo "Wrote $LINE_COUNT OpenMetrics lines to $OUT"
echo "Importing into Prometheus TSDB at $BLOCKS_DIR via promtool..."

promtool tsdb create-blocks-from openmetrics "$OUT" "$BLOCKS_DIR"

touch "$MARKER"
echo "Seed complete. Marker written to $MARKER (data through $END_TS, $((HEAD_GAP_SECONDS / 60))m before now)"
rm -f "$OUT"

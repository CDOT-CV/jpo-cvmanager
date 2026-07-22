#!/bin/sh
# Seeds Prometheus TSDB with synthetic kafka_produced_rsu_messages_total samples
# covering the last week for sample-data RSU IPs (see R__sample_data.sql).
# Used by the prometheus-sample-data service in docker-compose-intersection.yml.
set -eu

MARKER="/prometheus/.cvmanager-sample-seeded"
OUT="/tmp/cvmanager_rsu_counts.om"
BLOCKS_DIR="/prometheus"

DAYS="${SAMPLE_DAYS:-7}"
STEP="${SAMPLE_STEP_SECONDS:-300}"
FORCE_RESEED="${FORCE_RESEED:-false}"

if [ -f "$MARKER" ] && [ "$FORCE_RESEED" != "true" ]; then
  echo "Prometheus sample data already present ($MARKER); skipping seed."
  echo "Set FORCE_RESEED=true (or PROMETHEUS_FORCE_RESEED=true) and remove the marker to regenerate."
  exit 0
fi

if [ "$FORCE_RESEED" = "true" ]; then
  echo "FORCE_RESEED=true: removing previous sample marker and TSDB contents under $BLOCKS_DIR"
  rm -f "$MARKER"
  find "$BLOCKS_DIR" -mindepth 1 -maxdepth 1 ! -name "." -exec rm -rf {} + 2>/dev/null || true
fi

END_TS="$(date +%s)"
START_TS=$((END_TS - DAYS * 24 * 3600))

echo "Generating OpenMetrics samples from $START_TS to $END_TS (step=${STEP}s, days=${DAYS})..."

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
echo "Seed complete. Marker written to $MARKER"
rm -f "$OUT"

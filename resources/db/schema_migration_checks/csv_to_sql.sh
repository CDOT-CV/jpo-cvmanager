#!/usr/bin/env sh

set -eu

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "Usage: ./csv_to_sql.sh <input.csv> [output.sql]" >&2
  exit 1
fi

INPUT_CSV="$1"
OUTPUT_SQL="${2:-${INPUT_CSV%.csv}.sql}"

awk '
NR == 1 { next } # skip header: "create_table_ddl"
{
  line = $0
  sub(/\r$/, "", line)

  if (!in_record) {
    sub(/^"/, "", line)
    in_record = 1
  }

  end_record = 0
  if (line ~ /"$/) {
    sub(/"$/, "", line)
    end_record = 1
  }

  gsub(/""/, "\"", line)
  print line

  if (end_record) {
    print ""
    in_record = 0
  }
}
' "$INPUT_CSV" > "$OUTPUT_SQL"

echo "Wrote SQL to $OUTPUT_SQL"

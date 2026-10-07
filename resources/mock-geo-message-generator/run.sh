#!/bin/sh
set -eu

if [ "${MOCK_GEO_MESSAGES_ENABLED:-false}" != "true" ]; then
  echo "Mock geo message generation is disabled; skipping."
  exit 0
fi

if [ -z "${MONGO_DB_URI:-}" ]; then
  echo "MOCK_GEO_MESSAGES_ENABLED is true but MONGO_DB_URI is empty." >&2
  exit 1
fi

exec mongosh "$MONGO_DB_URI" --quiet \
  --eval "var seedDatabaseName = '${MONGO_DB_NAME:-CV}'; var seedBsmCollection = '${MONGO_PROCESSED_BSM_COLLECTION_NAME:-processed_bsm}'; var seedPsmCollection = '${MONGO_PROCESSED_PSM_COLLECTION_NAME:-processed_psm}';" \
  --file /seed.js
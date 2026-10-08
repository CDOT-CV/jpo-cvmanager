#!/bin/sh
set -eu

if [ -z "${MONGO_DB_URI:-}" ]; then
  echo "MONGO_DB_URI is empty, skipping geo-message seeding." >&2
  exit 1
fi

exec mongosh "$MONGO_DB_URI" --quiet \
  --eval "var seedDatabaseName = '${MONGO_DB_NAME:-CV}'; var seedBsmCollection = '${MONGO_PROCESSED_BSM_COLLECTION_NAME:-processed_bsm}'; var seedPsmCollection = '${MONGO_PROCESSED_PSM_COLLECTION_NAME:-processed_psm}';" \
  --file /seed.js
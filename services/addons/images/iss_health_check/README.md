# ISS SCMS Health Checker

Checks ISS Green Hills SCMS certificate status for registered RSUs every six hours and writes the results to `scms_health`. Each run acquires a fresh API token, stores it, and revokes the previous token.

## Token storage

Tokens are encrypted with Fernet in PostgreSQL (`public.iss_keys`). The encryption key comes from the `iss-health-check-secrets` Kubernetes Secret; the service does not use GCP Secret Manager. See [ADR-0002](../../../../docs/adr/0002-iss-token-storage.md) for the storage comparison and recommendation.

A PostgreSQL session lock prevents overlapping refreshes and device requests, with a 60-second lock timeout. Use a direct connection or session pooling, not transaction pooling.

Generate an encryption key with the service's Python dependencies installed:

```sh
python3 -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"
```

For key rotation, set `ISS_TOKEN_ENCRYPTION_KEY` to `new-key,old-key` in the Secret and restart the pod. After a successful token refresh, remove the old key and restart again. If the key is lost, clear `iss_keys` and supply a valid `ISS_API_KEY` to recover.

## Configuration

Requires the CV Manager PostgreSQL database and an ISS Green Hills service agreement with API access. Create the Secret shown in the [deployment manifest](../../../../resources/kubernetes/iss-health-check.yaml) in the deployment's namespace.

| Variable | Purpose |
| --- | --- |
| `ISS_API_KEY` | Valid initial API token, required when `public.iss_keys` is empty. Its Secret entry can be removed after a successful refresh. |
| `ISS_API_KEY_NAME` | Prefix for generated token names. |
| `ISS_PROJECT_ID` | ISS project containing the RSUs. |
| `ISS_SCMS_TOKEN_REST_ENDPOINT` | Token endpoint, e.g. `https://scms-api-domain/api/v3/token`. |
| `ISS_SCMS_VEHICLE_REST_ENDPOINT` | Device endpoint, e.g. `https://scms-api-domain/api/v3/devices`. |
| `ISS_TOKEN_ENCRYPTION_KEY` | Fernet key from the Kubernetes Secret; rotation accepts comma-separated keys, newest first. |
| `PG_DB_HOST` | PostgreSQL hostname and port. |
| `PG_DB_NAME`, `PG_DB_USER`, `PG_DB_PASS` | Database name and credentials. |
| `LOGGING_LEVEL` | Optional; defaults to `INFO`. |

## Upgrading

1. Stop the existing deployment: `kubectl scale deployment iss-health-check --replicas=0`.
2. For GCP storage, copy the current secret's JSON `token` field into the Kubernetes Secret's `ISS_API_KEY`. It must still be valid at the first run, and `public.iss_keys` must be empty for it to be used.
3. Add `ISS_TOKEN_ENCRYPTION_KEY` and `PG_DB_PASS` to the Kubernetes Secret and run Flyway migrations. For PostgreSQL storage, check the previous `ISS_KEY_TABLE_NAME`: tokens already in `public.iss_keys` are migrated automatically on the first successful refresh. If it points elsewhere, transfer the latest row's `common_name` and `token` into `public.iss_keys`, replacing its existing row if present, before deploying; custom tables are no longer read or migrated.
4. Deploy the updated image with one replica. The transferred token must still be valid at the first run.
5. Confirm a successful health check before deleting the old GCP secret and removing its Secret Manager permissions, or removing token rows left in a custom PostgreSQL table.

`STORAGE_TYPE`, `PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS`, and `ISS_KEY_TABLE_NAME` are no longer used by this service. Other services still use Google credentials; retain their configuration and shared credential Secrets.

## Local tests

Run `python -m pytest addons/tests/iss_health_check` from `services`. Set `ISS_TEST_DATABASE_URL` to a local PostgreSQL SQLAlchemy URL (`postgresql+pg8000://...`) to include migration and concurrency tests. These tests create and drop temporary databases and require `CREATE DATABASE` permission.

## GKE verification

Target GKE verification is still required. Confirm a successful refresh and `scms_health` update, encrypted token storage, and a second successful run after restarting the pod. Verify the deployment works without Secret Manager permissions. Record the image, migration version, and results without logging token values.

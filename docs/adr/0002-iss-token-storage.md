# ADR-0002: Encrypted PostgreSQL Storage for ISS SCMS API Tokens

**Date**: 2026-10-02  
**Status**: Accepted  
**Deciders**: CDOT CV Platform team

---

## Context

The `iss_health_check` addon authenticates to the ISS SCMS API with short-lived tokens. Each run uses the stored token to request a new one, saves it, and revokes the old one, so the stored value changes every run.

This token is stored in GCP Secret Manager, which adds a billed secret version on every run and ties the service to GCP. We need a storage option that works in any Kubernetes environment and keeps the token encrypted at rest.

## Decision

Store the token in PostgreSQL (`public.iss_keys`), encrypted with Fernet using a key held in a Kubernetes Secret (`ISS_TOKEN_ENCRYPTION_KEY`).

- **Security:** The token is encrypted by the application, so it doesn't rely on Cloud KMS. The pod only reads its Secret and never needs write access to cluster Secrets.
- **Simplicity:** The service already reads and writes PostgreSQL, and a `postgres` storage option already existed. No new permissions or client libraries are needed.
- **Portability:** Works anywhere PostgreSQL runs, including docker-compose.

## Alternatives Considered

- **Kubernetes Secret updated by the service:** Requires RBAC write access to Secrets and the `kubernetes` client, drifts from deployed manifests, and needs Cloud KMS on GKE for application-layer encryption.
- **Keep GCP Secret Manager:** Remains tied to GCP.

## Consequences

- GCP Secret Manager and the `google-cloud-secret-manager` dependency are removed.
- Operators manage the encryption key. If it's lost, `ISS_API_KEY` must be re-seeded with a valid token.
- Deployments using `STORAGE_TYPE=gcp` must create the key Secret and seed `ISS_API_KEY` from Secret Manager before upgrading.

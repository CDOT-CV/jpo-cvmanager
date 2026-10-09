# ADR-0002: Encrypted PostgreSQL Storage for ISS SCMS API Tokens

**Date**: 2026-10-02  
**Status**: Accepted  
**Deciders**: CDOT CV Platform team

---

## Context

The `iss_health_check` addon authenticates to the ISS SCMS API with short-lived tokens. Each run uses the stored token to request a new one, saves it, and revokes the old one, so the stored value changes every run.

The original implementation stored this token in GCP Secret Manager, which added a billed secret version on every run and tied the service to GCP. We need a storage option that works in any Kubernetes environment and keeps the token encrypted at rest.

## Decision

Store the token in PostgreSQL (`public.iss_keys`), encrypted with Fernet using a key held in a Kubernetes Secret (`ISS_TOKEN_ENCRYPTION_KEY`).

- **Security:** The token is encrypted by the application, so it doesn't rely on Cloud KMS. The pod only reads its Secret and never needs write access to cluster Secrets.
- **Simplicity:** Reuses the service's existing PostgreSQL integration and adds the cryptography library.
- **Portability:** Works anywhere PostgreSQL runs, including docker-compose.

## Alternatives Considered

- **Kubernetes Secret updated by the service:** Portable and avoids database encryption code, but requires Kubernetes API calls and permission to update the token Secret on every refresh. Secret protection depends on cluster encryption-at-rest and RBAC configuration.
- **Keep GCP Secret Manager:** Remains tied to GCP.

## Consequences

- GCP Secret Manager and the `google-cloud-secret-manager` dependency are removed.
- Operators manage the encryption key. If it's lost, `ISS_API_KEY` must be re-seeded with a valid token.
- The database retains one current token. A session lock serializes refresh and use, and the new token is committed before the previous token is revoked.
- See the [service README](../../services/addons/images/iss_health_check/README.md) for configuration, migration, and verification.

Both approaches require restricting access to Kubernetes Secrets and verifying the cluster's encryption-at-rest configuration. PostgreSQL encryption protects database copies independently of that configuration, but adds responsibility for key backup and rotation.

References: [Kubernetes Secret security](https://kubernetes.io/docs/concepts/security/secrets-good-practices/) and [Fernet encryption and rotation](https://cryptography.io/en/stable/fernet/).

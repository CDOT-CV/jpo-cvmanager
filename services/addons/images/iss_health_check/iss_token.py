from contextlib import contextmanager
import logging
import uuid

import requests
from sqlalchemy import text
from cryptography.fernet import Fernet, InvalidToken, MultiFernet

import common.pgquery as pgquery
import iss_health_check_environment

logger = logging.getLogger(__name__)

REQUEST_TIMEOUT_SECONDS = 30

# Recognize existing Fernet values during the plaintext upgrade.
FERNET_TOKEN_PREFIX = "gAAAAA"


def get_cipher():
    """Encrypt with the first key; decrypt with any configured key."""
    return MultiFernet(
        [Fernet(key) for key in iss_health_check_environment.ISS_TOKEN_ENCRYPTION_KEYS]
    )


def encrypt_token(token):
    return get_cipher().encrypt(token.encode()).decode()


def decrypt_token(encrypted_token):
    return get_cipher().decrypt(encrypted_token.encode()).decode()


def get_latest_token(connection):
    """Read the current token, allowing plaintext from before the migration."""
    query = (
        "SELECT iss_key_id, common_name, token "
        "FROM public.iss_keys "
        "ORDER BY iss_key_id DESC LIMIT 1"
    )
    data = connection.execute(text(query)).fetchall()
    if not data:
        return None

    iss_key_id, common_name, stored_token = data[0]
    if not stored_token.startswith(FERNET_TOKEN_PREFIX):
        logger.info(f"Migrating unencrypted token: {common_name}")
        token = stored_token
    else:
        try:
            token = decrypt_token(stored_token)
        except InvalidToken:
            raise RuntimeError(
                "Unable to decrypt the stored ISS token. Verify ISS_TOKEN_ENCRYPTION_KEY "
                "matches the key used to store it."
            ) from None

    logger.debug(f"Received token: {common_name} with id {iss_key_id}")
    return {"id": iss_key_id, "name": common_name, "token": token}


def store_token(connection, common_name, token):
    connection.execute(
        text(
            "INSERT INTO public.iss_keys (common_name, token) "
            "VALUES (:common_name, :token) "
            "ON CONFLICT ((true)) DO UPDATE "
            "SET common_name = EXCLUDED.common_name, token = EXCLUDED.token"
        ),
        {"common_name": common_name, "token": encrypt_token(token)},
    )
    connection.commit()


@contextmanager
def token_for_check():
    """Hold the refresh lock until the caller finishes using the token."""
    engine = pgquery.init_connection_engine()
    try:
        with engine.connect() as connection:
            connection.execute(text("SET lock_timeout = '60s'"))
            connection.execute(text("SELECT pg_advisory_lock(73021, 1)"))
            connection.commit()
            yield get_token(connection)
    finally:
        # This dedicated pool is closed so the session lock cannot leak to another job.
        engine.dispose()


def get_token(connection):
    """Generate a new ISS SCMS API token, store it, and revoke the previous one"""
    stored = get_latest_token(connection)
    if stored:
        token = stored["token"]
    elif iss_health_check_environment.ISS_API_KEY:
        logger.debug("No stored token found, using ISS_API_KEY")
        token = iss_health_check_environment.ISS_API_KEY
    else:
        raise RuntimeError(
            "No stored ISS token found and ISS_API_KEY is not set. "
            "Set ISS_API_KEY to a valid ISS SCMS API key."
        )

    iss_base = iss_health_check_environment.ISS_SCMS_TOKEN_REST_ENDPOINT
    iss_headers = {"x-api-key": token}

    new_friendly_name = (
        f"{iss_health_check_environment.ISS_API_KEY_NAME}_{str(uuid.uuid4())}"
    )
    iss_post_body = {"friendlyName": new_friendly_name, "expireDays": 1}

    logger.debug("POST: " + iss_base)
    response = requests.post(
        iss_base,
        json=iss_post_body,
        headers=iss_headers,
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    # The response body may contain a token, so it is never logged
    if not response.ok:
        raise RuntimeError(
            f"Failed to create a new ISS SCMS API token. Status: {response.status_code}"
        )
    try:
        new_token = response.json()["Item"]
    except requests.JSONDecodeError:
        raise RuntimeError(
            "Unexpected response from ISS SCMS API: response is not valid JSON"
        ) from None
    except (KeyError, TypeError):
        raise RuntimeError(
            "Unexpected response from ISS SCMS API: missing 'Item' field"
        ) from None
    if not isinstance(new_token, str) or not new_token:
        raise RuntimeError("Unexpected response from ISS SCMS API: invalid token")
    logger.debug(f"Received new token: {new_friendly_name}")

    # Store the new token before revoking the old one so a failed write
    # does not leave the service without a valid token
    store_token(connection, new_friendly_name, new_token)

    if stored:
        iss_delete_body = {"friendlyName": stored["name"]}
        try:
            delete_response = requests.delete(
                iss_base,
                json=iss_delete_body,
                headers=iss_headers,
                timeout=REQUEST_TIMEOUT_SECONDS,
            )
            if not delete_response.ok:
                logger.warning(
                    "Failed to revoke previous ISS token: HTTP %s",
                    delete_response.status_code,
                )
        except requests.RequestException:
            logger.warning(
                "Failed to revoke previous ISS token; using the persisted token"
            )

    return new_token

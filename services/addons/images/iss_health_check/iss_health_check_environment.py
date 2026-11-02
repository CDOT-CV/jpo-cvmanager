from cryptography.fernet import Fernet

from common.common_environment import get_env_var


def parse_encryption_keys(value: str) -> list[str]:
    """Split a comma-separated list of Fernet keys and validate each one"""
    keys = [key.strip() for key in value.split(",") if key.strip()]
    if not keys:
        raise ValueError("ISS_TOKEN_ENCRYPTION_KEY must contain at least one key")
    for key in keys:
        try:
            Fernet(key)
        except ValueError:
            raise ValueError(
                "ISS_TOKEN_ENCRYPTION_KEY contains an invalid key. Keys must be "
                "32 url-safe base64-encoded bytes."
            ) from None
    return keys


ISS_API_KEY = get_env_var("ISS_API_KEY", secret=True)
ISS_API_KEY_NAME = get_env_var("ISS_API_KEY_NAME", error=True)
ISS_SCMS_TOKEN_REST_ENDPOINT = get_env_var("ISS_SCMS_TOKEN_REST_ENDPOINT", error=True)
ISS_SCMS_VEHICLE_REST_ENDPOINT = get_env_var(
    "ISS_SCMS_VEHICLE_REST_ENDPOINT", error=True
)
ISS_PROJECT_ID = get_env_var("ISS_PROJECT_ID", error=True)
ISS_TOKEN_ENCRYPTION_KEYS = parse_encryption_keys(
    get_env_var("ISS_TOKEN_ENCRYPTION_KEY", error=True, secret=True)
)

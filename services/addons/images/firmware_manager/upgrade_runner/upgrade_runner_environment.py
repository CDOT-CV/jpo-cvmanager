from common.common_environment import get_env_var

BLOB_STORAGE_PROVIDER = get_env_var("BLOB_STORAGE_PROVIDER", "DOCKER", warn=False)
UPGRADE_SCHEDULER_ENDPOINT = get_env_var("UPGRADE_SCHEDULER_ENDPOINT", "127.0.0.1")
IAPI_ENDPOINT = get_env_var("IAPI_ENDPOINT", error=True)
KC_USERNAME = get_env_var("KC_USERNAME", error=True)
KC_PASSWORD = get_env_var("KC_PASSWORD", error=True)

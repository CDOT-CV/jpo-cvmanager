from common.common_environment import get_env_var

DEPLOYMENT_TITLE = get_env_var("DEPLOYMENT_TITLE", "Example Deployment", warn=True)
MONGO_DB_URI = get_env_var("MONGO_DB_URI", "mongodb://localhost:27017")
MONGO_DB_NAME = get_env_var("MONGO_DB_NAME", "CV")
IAPI_ENDPOINT = get_env_var("IAPI_ENDPOINT", error=True)
KC_USERNAME = get_env_var("KC_USERNAME", error=True)
KC_PASSWORD = get_env_var("KC_PASSWORD", error=True)

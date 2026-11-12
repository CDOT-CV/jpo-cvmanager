from unittest.mock import MagicMock, call, patch

import pytest
from cryptography.fernet import Fernet

from addons.images.iss_health_check import iss_token

TEST_KEY = Fernet.generate_key().decode()
OLD_KEY = Fernet.generate_key().decode()
TOKEN_ENDPOINT = "https://api.dm.iss-scms.com/api/test-token"
TIMEOUT = iss_token.REQUEST_TIMEOUT_SECONDS


@pytest.fixture
def connection():
    return MagicMock()


def test_decrypt_with_rotated_key():
    with patch("iss_health_check_environment.ISS_TOKEN_ENCRYPTION_KEYS", [OLD_KEY]):
        encrypted = iss_token.encrypt_token("test-token")

    with patch(
        "iss_health_check_environment.ISS_TOKEN_ENCRYPTION_KEYS", [TEST_KEY, OLD_KEY]
    ):
        assert iss_token.decrypt_token(encrypted) == "test-token"
        refreshed = iss_token.encrypt_token("next-token")
        assert Fernet(TEST_KEY).decrypt(refreshed.encode()) == b"next-token"


def test_get_latest_token_empty(connection):
    connection.execute.return_value.fetchall.return_value = []
    assert iss_token.get_latest_token(connection) is None


@patch("iss_health_check_environment.ISS_TOKEN_ENCRYPTION_KEYS", [TEST_KEY])
def test_get_latest_token(connection):
    encrypted = Fernet(TEST_KEY).encrypt(b"test-token").decode()
    connection.execute.return_value.fetchall.return_value = [
        (1, "test-common-name", encrypted)
    ]

    actual_value = iss_token.get_latest_token(connection)

    assert actual_value == {"id": 1, "name": "test-common-name", "token": "test-token"}


def test_get_latest_token_unencrypted(connection):
    connection.execute.return_value.fetchall.return_value = [
        (1, "test-common-name", "plaintext-token")
    ]

    actual_value = iss_token.get_latest_token(connection)

    assert actual_value == {
        "id": 1,
        "name": "test-common-name",
        "token": "plaintext-token",
    }


@patch("iss_health_check_environment.ISS_TOKEN_ENCRYPTION_KEYS", [TEST_KEY])
def test_get_latest_token_wrong_key(connection):
    encrypted = Fernet(OLD_KEY).encrypt(b"test-token").decode()
    connection.execute.return_value.fetchall.return_value = [
        (1, "test-common-name", encrypted)
    ]

    with pytest.raises(RuntimeError, match="ISS_TOKEN_ENCRYPTION_KEY"):
        iss_token.get_latest_token(connection)


@patch("iss_health_check_environment.ISS_TOKEN_ENCRYPTION_KEYS", [TEST_KEY])
def test_store_token(connection):
    iss_token.store_token(connection, "test-common-name", "test-token")

    query = str(connection.execute.call_args.args[0])
    params = connection.execute.call_args.args[1]
    assert query == (
        "INSERT INTO public.iss_keys (common_name, token) "
        "VALUES (:common_name, :token) "
        "ON CONFLICT ((true)) DO UPDATE "
        "SET common_name = EXCLUDED.common_name, token = EXCLUDED.token"
    )
    assert params["common_name"] == "test-common-name"
    assert params["token"] != "test-token"
    assert Fernet(TEST_KEY).decrypt(params["token"].encode()) == b"test-token"


def mock_response(ok=True, json_value=None, status_code=200, text=""):
    response = MagicMock()
    response.ok = ok
    response.status_code = status_code
    response.text = text
    response.json.return_value = json_value
    return response


@patch("iss_health_check_environment.ISS_API_KEY", "test-api-key")
@patch("iss_health_check_environment.ISS_SCMS_TOKEN_REST_ENDPOINT", TOKEN_ENDPOINT)
@patch("iss_health_check_environment.ISS_API_KEY_NAME", "test-api-key-name")
@patch("addons.images.iss_health_check.iss_token.requests.delete")
@patch("addons.images.iss_health_check.iss_token.requests.post")
@patch("addons.images.iss_health_check.iss_token.uuid")
@patch("addons.images.iss_health_check.iss_token.store_token")
@patch("addons.images.iss_health_check.iss_token.get_latest_token")
def test_get_token_no_stored_token(
    mock_get_latest_token,
    mock_store_token,
    mock_uuid,
    mock_post,
    mock_delete,
    connection,
):
    mock_get_latest_token.return_value = None
    mock_uuid.uuid4.return_value = 12345
    mock_post.return_value = mock_response(json_value={"Item": "new-iss-token"})

    result = iss_token.get_token(connection)

    mock_post.assert_called_with(
        TOKEN_ENDPOINT,
        json={"friendlyName": "test-api-key-name_12345", "expireDays": 1},
        headers={"x-api-key": "test-api-key"},
        timeout=TIMEOUT,
    )
    mock_store_token.assert_called_with(
        connection, "test-api-key-name_12345", "new-iss-token"
    )
    mock_delete.assert_not_called()
    assert result == "new-iss-token"


@patch("iss_health_check_environment.ISS_SCMS_TOKEN_REST_ENDPOINT", TOKEN_ENDPOINT)
@patch("iss_health_check_environment.ISS_API_KEY_NAME", "test-api-key-name")
@patch("addons.images.iss_health_check.iss_token.requests.delete")
@patch("addons.images.iss_health_check.iss_token.requests.post")
@patch("addons.images.iss_health_check.iss_token.uuid")
@patch("addons.images.iss_health_check.iss_token.store_token")
@patch("addons.images.iss_health_check.iss_token.get_latest_token")
def test_get_token_stored_token(
    mock_get_latest_token,
    mock_store_token,
    mock_uuid,
    mock_post,
    mock_delete,
    connection,
):
    mock_get_latest_token.return_value = {
        "id": 1,
        "name": "test-api-key-name_01234",
        "token": "old-token",
    }
    mock_uuid.uuid4.return_value = 12345
    mock_post.return_value = mock_response(json_value={"Item": "new-iss-token"})
    mock_delete.return_value = mock_response()

    manager = MagicMock()
    manager.attach_mock(mock_store_token, "store_token")
    manager.attach_mock(mock_delete, "iss_delete")

    result = iss_token.get_token(connection)

    expected_headers = {"x-api-key": "old-token"}
    mock_post.assert_called_with(
        TOKEN_ENDPOINT,
        json={"friendlyName": "test-api-key-name_12345", "expireDays": 1},
        headers=expected_headers,
        timeout=TIMEOUT,
    )
    assert manager.mock_calls == [
        call.store_token(connection, "test-api-key-name_12345", "new-iss-token"),
        call.iss_delete(
            TOKEN_ENDPOINT,
            json={"friendlyName": "test-api-key-name_01234"},
            headers=expected_headers,
            timeout=TIMEOUT,
        ),
    ]
    assert result == "new-iss-token"


@patch("iss_health_check_environment.ISS_API_KEY", "test-api-key")
@patch("iss_health_check_environment.ISS_SCMS_TOKEN_REST_ENDPOINT", TOKEN_ENDPOINT)
@patch("iss_health_check_environment.ISS_API_KEY_NAME", "test-api-key-name")
@patch("addons.images.iss_health_check.iss_token.requests.post")
@patch("addons.images.iss_health_check.iss_token.store_token")
@patch("addons.images.iss_health_check.iss_token.get_latest_token")
def test_get_token_request_failed(
    mock_get_latest_token, mock_store_token, mock_post, connection
):
    mock_get_latest_token.return_value = None
    mock_post.return_value = mock_response(
        ok=False, status_code=401, text="secret-response-body"
    )

    with pytest.raises(RuntimeError, match="401") as error:
        iss_token.get_token(connection)

    assert "secret-response-body" not in str(error.value)

    mock_store_token.assert_not_called()


@patch("iss_health_check_environment.ISS_API_KEY", "test-api-key")
@patch("iss_health_check_environment.ISS_SCMS_TOKEN_REST_ENDPOINT", TOKEN_ENDPOINT)
@patch("iss_health_check_environment.ISS_API_KEY_NAME", "test-api-key-name")
@patch("addons.images.iss_health_check.iss_token.requests.post")
@patch("addons.images.iss_health_check.iss_token.store_token")
@patch("addons.images.iss_health_check.iss_token.get_latest_token")
def test_get_token_unexpected_response(
    mock_get_latest_token, mock_store_token, mock_post, connection
):
    mock_get_latest_token.return_value = None
    mock_post.return_value = mock_response(
        json_value={"item": "new-iss-token"}, text='{"item": "new-iss-token"}'
    )

    with pytest.raises(RuntimeError, match="missing 'Item' field") as error:
        iss_token.get_token(connection)

    assert "new-iss-token" not in str(error.value)

    mock_store_token.assert_not_called()


@patch("iss_health_check_environment.ISS_API_KEY", None)
@patch("addons.images.iss_health_check.iss_token.requests.post")
@patch("addons.images.iss_health_check.iss_token.get_latest_token")
def test_get_token_no_stored_token_or_api_key(
    mock_get_latest_token, mock_post, connection
):
    mock_get_latest_token.return_value = None

    with pytest.raises(RuntimeError, match="ISS_API_KEY is not set"):
        iss_token.get_token(connection)

    mock_post.assert_not_called()


@pytest.mark.parametrize("failure", ["write", "commit"])
def test_failed_persistence_does_not_revoke_previous_token(connection, failure):
    connection.execute.return_value.fetchall.return_value = [(1, "old", "old-token")]
    if failure == "write":
        connection.execute.side_effect = [
            connection.execute.return_value,
            RuntimeError("database write failed"),
        ]
    else:
        connection.commit.side_effect = RuntimeError("database commit failed")
    with patch.object(
        iss_token.requests,
        "post",
        return_value=mock_response(json_value={"Item": "new-token"}),
    ), patch.object(iss_token.requests, "delete") as revoke:
        with pytest.raises(RuntimeError, match="database"):
            iss_token.get_token(connection)
        revoke.assert_not_called()


@pytest.mark.parametrize("failure", ["timeout", "http"])
def test_revocation_failure_keeps_new_token_usable(connection, failure):
    connection.execute.return_value.fetchall.return_value = [(1, "old", "old-token")]
    with patch.object(
        iss_token.requests,
        "post",
        return_value=mock_response(json_value={"Item": "new-token"}),
    ), patch.object(iss_token.requests, "delete") as revoke:
        if failure == "timeout":
            revoke.side_effect = iss_token.requests.Timeout()
        else:
            revoke.return_value = mock_response(ok=False, status_code=500)
        assert iss_token.get_token(connection) == "new-token"
        connection.commit.assert_called_once()


@pytest.mark.parametrize("item", [None, "", 123, {}])
def test_invalid_token_is_not_persisted(connection, item):
    connection.execute.return_value.fetchall.return_value = []
    with patch.object(
        iss_token.requests,
        "post",
        return_value=mock_response(json_value={"Item": item}),
    ):
        with pytest.raises(RuntimeError, match="invalid token"):
            iss_token.get_token(connection)
        connection.commit.assert_not_called()


def test_invalid_json_is_not_persisted_or_logged(connection, caplog):
    connection.execute.return_value.fetchall.return_value = []
    response = mock_response()
    response.json.side_effect = iss_token.requests.JSONDecodeError(
        "invalid", "secret-value", 0
    )
    with patch.object(iss_token.requests, "post", return_value=response):
        with pytest.raises(RuntimeError, match="not valid JSON") as error:
            iss_token.get_token(connection)
    assert "secret-value" not in str(error.value) + caplog.text
    connection.commit.assert_not_called()

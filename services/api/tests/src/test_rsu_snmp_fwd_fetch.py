from unittest.mock import patch, MagicMock
import pytest
from flask import Flask, request
from rsu_snmp_fwd_fetch import RsuSnmpFwdFetch
from common.auth_tools import PermissionResult, EnvironWithOrg, ORG_ROLE_LITERAL, ENVIRON_USER_KEY, UserInfo

@pytest.fixture
def app():
    app = Flask(__name__)
    return app

@pytest.fixture
def permission_result():
    mock_user_info = MagicMock(spec=UserInfo)
    mock_user_info.super_user = False
    mock_user_info.organizations = {"TestOrg": "admin"}
    
    mock_user = MagicMock(spec=EnvironWithOrg)
    mock_user.organization = "TestOrg"
    mock_user.user_info = mock_user_info
    mock_user.role = ORG_ROLE_LITERAL.USER
    
    return PermissionResult(allowed=True, qualified_orgs=["TestOrg"], message=None, user=mock_user)

# #################################### Testing Requests ###########################################

def test_options_request():
    resource = RsuSnmpFwdFetch()
    (body, code, headers) = resource.options()
    assert body == ""
    assert code == 204
    assert headers["Access-Control-Allow-Methods"] == "GET"

@patch("rsu_snmp_fwd_fetch.fetch_rsu_info")
@patch("rsu_snmp_fwd_fetch.UpdatePostgresRsuMessageForward")
@patch("rsu_snmp_fwd_fetch.rsu_message_forward_helpers")
def test_get_request_success(mock_helpers, mock_update_pg, mock_fetch_info, app, permission_result):
    with app.test_request_context(query_string={"rsu_ip": "10.0.0.1"}):
        request.environ[ENVIRON_USER_KEY] = permission_result.user
        rsu_info = {
            "rsu_id": 1,
            "snmp_username": "user",
            "snmp_password": "pw",
            "snmp_encrypt_pw": "enc",
            "snmp_version": "v3"
        }
        mock_fetch_info.return_value = rsu_info
        
        mock_updater = MagicMock()
        mock_update_pg.return_value = mock_updater
        mock_updater.get_snmp_configs.return_value = {1: "some_configs"}
        
        mock_helpers.format_snmp_msgfwd_configs.return_value = {"formatted": "data"}
        
        resource = RsuSnmpFwdFetch()
        (data, code, headers) = resource.get()
        
        assert code == 200
        assert data == {"formatted": "data"}
        mock_fetch_info.assert_called_once_with("10.0.0.1", "TestOrg")
        mock_updater.get_snmp_configs.assert_called_once()
        args, _ = mock_updater.get_snmp_configs.call_args
        assert args[0][0]["ipv4_address"] == "10.0.0.1"

def test_get_request_invalid_schema(app, permission_result):
    with app.test_request_context(query_string={"rsu_ip": "invalid-ip"}):
        request.environ[ENVIRON_USER_KEY] = permission_result.user
        resource = RsuSnmpFwdFetch()
        with pytest.raises(Exception) as excinfo:
            resource.get()
        assert "400" in str(excinfo.value)

@patch("rsu_snmp_fwd_fetch.fetch_rsu_info")
def test_get_request_rsu_not_found(mock_fetch_info, app, permission_result):
    with app.test_request_context(query_string={"rsu_ip": "10.0.0.1"}):
        request.environ[ENVIRON_USER_KEY] = permission_result.user
        mock_fetch_info.return_value = None
        
        resource = RsuSnmpFwdFetch()
        with pytest.raises(Exception) as excinfo:
            resource.get()
        assert "404" in str(excinfo.value)

@patch("rsu_snmp_fwd_fetch.fetch_rsu_info")
def test_get_request_missing_required_fields(mock_fetch_info, app, permission_result):
    with app.test_request_context(query_string={"rsu_ip": "10.0.0.1"}):
        request.environ[ENVIRON_USER_KEY] = permission_result.user
        # Missing snmp_version
        rsu_info = {
            "rsu_id": 1,
            "snmp_username": "user",
            "snmp_password": "pw",
            "snmp_encrypt_pw": "enc"
        }
        mock_fetch_info.return_value = rsu_info
        
        resource = RsuSnmpFwdFetch()
        (data, code) = resource.get()
        
        assert code == 500
        assert data["message"] == "RSU info missing required fields for SNMP config fetch"

@patch("rsu_snmp_fwd_fetch.fetch_rsu_info")
@patch("rsu_snmp_fwd_fetch.UpdatePostgresRsuMessageForward")
@patch("rsu_snmp_fwd_fetch.rsu_message_forward_helpers")
def test_get_request_missing_snmp_encrypt_pw_field(mock_helpers, mock_update_pg, mock_fetch_info, app, permission_result):
    with app.test_request_context(query_string={"rsu_ip": "10.0.0.1"}):
        request.environ[ENVIRON_USER_KEY] = permission_result.user
        rsu_info = {
            "rsu_id": 1,
            "snmp_username": "user",
            "snmp_password": "pw",
            "snmp_version": "v3"
        }
        mock_fetch_info.return_value = rsu_info

        mock_updater = MagicMock()
        mock_update_pg.return_value = mock_updater
        mock_updater.get_snmp_configs.return_value = {1: "some_configs"}

        mock_helpers.format_snmp_msgfwd_configs.return_value = {"formatted": "data"}

        resource = RsuSnmpFwdFetch()
        (data, code, headers) = resource.get()

        assert code == 200
        assert data == {"formatted": "data"}
        mock_fetch_info.assert_called_once_with("10.0.0.1", "TestOrg")
        mock_updater.get_snmp_configs.assert_called_once()
        args, _ = mock_updater.get_snmp_configs.call_args
        assert args[0][0]["ipv4_address"] == "10.0.0.1"

@patch("rsu_snmp_fwd_fetch.fetch_rsu_info")
@patch("rsu_snmp_fwd_fetch.UpdatePostgresRsuMessageForward")
def test_get_request_unable_to_retrieve(mock_update_pg, mock_fetch_info, app, permission_result):
    with app.test_request_context(query_string={"rsu_ip": "10.0.0.1"}):
        request.environ[ENVIRON_USER_KEY] = permission_result.user
        rsu_info = {
            "rsu_id": 1,
            "snmp_username": "user",
            "snmp_password": "pw",
            "snmp_encrypt_pw": "enc",
            "snmp_version": "v3"
        }
        mock_fetch_info.return_value = rsu_info
        
        mock_updater = MagicMock()
        mock_update_pg.return_value = mock_updater
        mock_updater.get_snmp_configs.return_value = {1: "Unable to retrieve latest SNMP config"}
        
        resource = RsuSnmpFwdFetch()
        (data, code) = resource.get()
        
        assert code == 500
        assert data["message"] == "Unable to retrieve latest SNMP config from RSU"

@patch("rsu_snmp_fwd_fetch.fetch_rsu_info")
@patch("rsu_snmp_fwd_fetch.UpdatePostgresRsuMessageForward")
def test_get_request_unsupported_version(mock_update_pg, mock_fetch_info, app, permission_result):
    with app.test_request_context(query_string={"rsu_ip": "10.0.0.1"}):
        request.environ[ENVIRON_USER_KEY] = permission_result.user
        rsu_info = {
            "rsu_id": 1,
            "snmp_username": "user",
            "snmp_password": "pw",
            "snmp_encrypt_pw": "enc",
            "snmp_version": "v3"
        }
        mock_fetch_info.return_value = rsu_info
        
        mock_updater = MagicMock()
        mock_update_pg.return_value = mock_updater
        mock_updater.get_snmp_configs.return_value = {1: "Unsupported SNMP version"}
        
        resource = RsuSnmpFwdFetch()
        (data, code) = resource.get()
        
        assert code == 400
        assert data["message"] == "Unsupported SNMP version for direct fetch"

@patch("rsu_snmp_fwd_fetch.fetch_rsu_info")
@patch("rsu_snmp_fwd_fetch.UpdatePostgresRsuMessageForward")
def test_get_request_exception(mock_update_pg, mock_fetch_info, app, permission_result):
    with app.test_request_context(query_string={"rsu_ip": "10.0.0.1"}):
        request.environ[ENVIRON_USER_KEY] = permission_result.user
        rsu_info = {
            "rsu_id": 1,
            "snmp_username": "user",
            "snmp_password": "pw",
            "snmp_encrypt_pw": "enc",
            "snmp_version": "v3"
        }
        mock_fetch_info.return_value = rsu_info
        
        mock_updater = MagicMock()
        mock_update_pg.return_value = mock_updater
        mock_updater.get_snmp_configs.side_effect = Exception("Test Exception")
        
        resource = RsuSnmpFwdFetch()
        (data, code) = resource.get()
        
        assert code == 500
        assert data["message"] == "An internal error occurred while fetching SNMP configs."

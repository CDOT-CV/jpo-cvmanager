import pytest
import datetime
from unittest.mock import Mock, patch
from email_api import EmailApi, KeycloakToken


@pytest.fixture
def email_api():
    """Fixture to create an EmailApi instance for testing."""
    return EmailApi(
        iapi_base_url="http://localhost:8089",
        kc_client_id="test-client",
        kc_client_secret="test-secret"
    )


@pytest.fixture
def mock_token():
    """Fixture for a mock Keycloak token."""
    return KeycloakToken(
        access_token="mock_access_token",
        expires_in=300000,  # 5 minutes in milliseconds
        refresh_expires_in=1800000,
        refresh_token="mock_refresh_token",
        token_type="Bearer",
        id_token="mock_id_token",
        not_before_policy="0",
        session_state="mock_session",
        scope="openid profile email"
    )


class TestGenKeycloakToken:
    """Tests for gen_keycloak_token method."""
    
    @patch('email_api.requests.post')
    def test_gen_keycloak_token_success(self, mock_post, email_api, mock_token):
        """Test successful token generation."""
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = mock_token
        mock_post.return_value = mock_response
        
        status_code, token = email_api.gen_keycloak_token()
        
        assert status_code == 200
        assert token["access_token"] == "mock_access_token"
        mock_post.assert_called_once_with(
            "http://localhost:8089/auth/token-service-account",
            data={
                "client_id": "test-client",
                "client_secret": "test-secret"
            }
        )
    
    @patch('email_api.requests.post')
    def test_gen_keycloak_token_failure(self, mock_post, email_api):
        """Test failed token generation."""
        mock_response = Mock()
        mock_response.status_code = 401
        mock_response.text = "Unauthorized"
        mock_response.json.return_value = {"error": "invalid_client"}
        mock_post.return_value = mock_response
        
        status_code, response = email_api.gen_keycloak_token()
        
        assert status_code == 401
        assert "error" in response
    
    @patch('email_api.requests.post')
    def test_gen_keycloak_token_network_error(self, mock_post, email_api):
        """Test network error during token generation."""
        mock_post.side_effect = Exception("Connection error")
        
        with pytest.raises(Exception, match="Connection error"):
            email_api.gen_keycloak_token()


class TestIsCurrentTokenValid:
    """Tests for is_current_token_valid method."""
    
    def test_no_token_returns_false(self, email_api):
        """Test that method returns False when no token exists."""
        assert email_api.is_current_token_valid() is False
    
    def test_expired_token_returns_false(self, email_api, mock_token):
        """Test that method returns False for expired token."""
        email_api.token = mock_token
        email_api.token_expiration_date = datetime.datetime.now() - datetime.timedelta(minutes=1)
        
        assert email_api.is_current_token_valid() is False
    
    def test_valid_token_returns_true(self, email_api, mock_token):
        """Test that method returns True for valid token."""
        email_api.token = mock_token
        email_api.token_expiration_date = datetime.datetime.now() + datetime.timedelta(minutes=5)
        
        assert email_api.is_current_token_valid() is True


class TestGetKcToken:
    """Tests for get_kc_token method."""
    
    def test_returns_existing_valid_token(self, email_api, mock_token):
        """Test that existing valid token is returned without new request."""
        email_api.token = mock_token
        email_api.token_expiration_date = datetime.datetime.now() + datetime.timedelta(minutes=5)
        
        token = email_api.get_kc_token()
        
        assert token == mock_token
    
    @patch('email_api.requests.post')
    def test_generates_new_token_when_none_exists(self, mock_post, email_api, mock_token):
        """Test that new token is generated when none exists."""
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = mock_token
        mock_post.return_value = mock_response
        
        token = email_api.get_kc_token()
        
        assert token == mock_token
        assert email_api.token == mock_token
        assert email_api.token_expiration_date > datetime.datetime.now()
    
    @patch('email_api.requests.post')
    def test_returns_none_on_token_generation_failure(self, mock_post, email_api):
        """Test that None is returned when token generation fails."""
        mock_response = Mock()
        mock_response.status_code = 500
        mock_response.text = "Internal Server Error"
        mock_response.json.return_value = {"error": "server_error"}
        mock_post.return_value = mock_response
        
        token = email_api.get_kc_token()
        
        assert token is None


class TestSendMessageCounts:
    """Tests for send_message_counts method."""
    
    @patch('email_api.requests.post')
    def test_send_message_counts_success(self, mock_post, email_api, mock_token):
        """Test successful message counts email send."""
        email_api.token = mock_token
        email_api.token_expiration_date = datetime.datetime.now() + datetime.timedelta(minutes=5)
        
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {"status": "sent"}
        mock_post.return_value = mock_response
        
        start_date = datetime.datetime(2025, 1, 1)
        end_date = datetime.datetime(2025, 1, 2)
        
        status_code, response = email_api.send_message_counts(
            org_name="Test Org",
            deployment_title="Test Deployment",
            start_date=start_date,
            end_date=end_date,
            message_type_list=["BSM", "TIM"],
            counts=[{"rsu": "192.168.1.1", "count": 100}]
        )
        
        assert status_code == 200
        assert response["status"] == "sent"
        mock_post.assert_called_once()
        call_args = mock_post.call_args
        assert call_args[1]["headers"]["Authorization"] == "bearer mock_access_token"
    
    @patch('email_api.requests.post')
    def test_send_message_counts_failure(self, mock_post, email_api, mock_token):
        """Test failed message counts email send."""
        email_api.token = mock_token
        email_api.token_expiration_date = datetime.datetime.now() + datetime.timedelta(minutes=5)
        
        mock_response = Mock()
        mock_response.status_code = 500
        mock_response.text = "Internal Server Error"
        mock_response.json.return_value = {"error": "failed to send"}
        mock_post.return_value = mock_response
        
        status_code, response = email_api.send_message_counts(
            org_name="Test Org",
            deployment_title="Test Deployment",
            start_date=datetime.datetime.now(),
            end_date=datetime.datetime.now(),
            message_type_list=["BSM"],
            counts=[]
        )
        
        assert status_code == 500
        assert "error" in response


class TestSendFirmwareUpgradeFailure:
    """Tests for send_firmware_upgrade_failure method."""
    
    @patch('email_api.requests.post')
    def test_send_firmware_upgrade_failure_success(self, mock_post, email_api, mock_token):
        """Test successful firmware upgrade failure email send."""
        email_api.token = mock_token
        email_api.token_expiration_date = datetime.datetime.now() + datetime.timedelta(minutes=5)
        
        mock_response = Mock()
        mock_response.status_code = 201
        mock_response.json.return_value = {"message": "email sent"}
        mock_post.return_value = mock_response
        
        status_code, response = email_api.send_firmware_upgrade_failure(
            rsu_ip="192.168.1.100",
            error_message="SNMP timeout",
            failure_type="ConnectionError",
            stack_trace="Traceback..."
        )
        
        assert status_code == 201
        assert response["message"] == "email sent"
    
    @patch('email_api.requests.post')
    def test_send_firmware_upgrade_failure_invalid_rsu(self, mock_post, email_api, mock_token):
        """Test firmware upgrade failure email with invalid RSU."""
        email_api.token = mock_token
        email_api.token_expiration_date = datetime.datetime.now() + datetime.timedelta(minutes=5)
        
        mock_response = Mock()
        mock_response.status_code = 400
        mock_response.text = "Invalid RSU IP"
        mock_response.json.return_value = {"error": "validation_error"}
        mock_post.return_value = mock_response
        
        status_code, response = email_api.send_firmware_upgrade_failure(
            rsu_ip="invalid_ip",
            error_message="Error",
            failure_type="Error",
            stack_trace=""
        )
        
        assert status_code == 400


class TestSendRsuErrorSummary:
    """Tests for send_rsu_error_summary method."""
    
    @patch('email_api.requests.post')
    def test_send_rsu_error_summary_success(self, mock_post, email_api, mock_token):
        """Test successful RSU error summary email send."""
        email_api.token = mock_token
        email_api.token_expiration_date = datetime.datetime.now() + datetime.timedelta(minutes=5)
        
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {"recipients_count": 2}
        mock_post.return_value = mock_response
        
        status_code, response = email_api.send_rsu_error_summary(
            recipients=["admin@example.com", "ops@example.com"],
            subject="RSU Errors",
            message="Multiple RSUs offline"
        )
        
        assert status_code == 200
        assert response["recipients_count"] == 2
    
    @patch('email_api.requests.post')
    def test_send_rsu_error_summary_no_recipients(self, mock_post, email_api, mock_token):
        """Test RSU error summary with empty recipients list."""
        email_api.token = mock_token
        email_api.token_expiration_date = datetime.datetime.now() + datetime.timedelta(minutes=5)
        
        mock_response = Mock()
        mock_response.status_code = 400
        mock_response.text = "No recipients"
        mock_response.json.return_value = {"error": "no_recipients"}
        mock_post.return_value = mock_response
        
        status_code, response = email_api.send_rsu_error_summary(
            recipients=[],
            subject="Test",
            message="Test"
        )
        
        assert status_code == 400


class TestSendApiErrorEmail:
    """Tests for send_api_error_email method."""
    
    @patch('email_api.requests.post')
    def test_send_api_error_email_success(self, mock_post, email_api, mock_token):
        """Test successful API error email send."""
        email_api.token = mock_token
        email_api.token_expiration_date = datetime.datetime.now() + datetime.timedelta(minutes=5)
        
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {"status": "sent"}
        mock_post.return_value = mock_response
        
        status_code, response = email_api.send_api_error_email(
            error_message="Database connection failed",
            stack_trace="Traceback (most recent call last)...",
            timestamp="2025-01-05T12:00:00Z",
            logs_link="https://logs.example.com"
        )
        
        assert status_code == 200
        assert response["status"] == "sent"
    
    @patch('email_api.requests.post')
    def test_send_api_error_email_with_all_fields(self, mock_post, email_api, mock_token):
        """Test API error email with all fields populated."""
        email_api.token = mock_token
        email_api.token_expiration_date = datetime.datetime.now() + datetime.timedelta(minutes=5)
        
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {"message_id": "12345"}
        mock_post.return_value = mock_response
        
        status_code, response = email_api.send_api_error_email(
            error_message="ValueError: Invalid latitude",
            stack_trace="Traceback...\nValueError: Invalid latitude",
            timestamp="2025-01-05T14:32:18.456Z",
            logs_link="https://cvmanager.example.com/logs?level=error"
        )
        
        assert status_code == 200
        call_args = mock_post.call_args
        json_data = call_args[1]["json"]
        assert "error_message" in json_data
        assert "stack_trace" in json_data
        assert "timestamp" in json_data
        assert "logs_link" in json_data
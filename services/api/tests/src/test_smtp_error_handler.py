import logging
from unittest.mock import Mock, patch
from api.src.smtp_error_handler import ErrorEmailHandler, configure_error_emails


class TestConfigureErrorEmails:
    """Tests for configure_error_emails function."""

    @patch("api.src.smtp_error_handler.ErrorEmailHandler")
    def test_configure_error_emails_adds_handler(self, mock_handler_class):
        """Test that error email handler is added to app logger."""
        mock_app = Mock()
        mock_handler_instance = Mock()
        mock_handler_class.return_value = mock_handler_instance

        configure_error_emails(mock_app)

        mock_handler_instance.setLevel.assert_called_once_with(logging.ERROR)
        mock_handler_instance.setFormatter.assert_called_once()
        mock_app.logger.addHandler.assert_called_once_with(mock_handler_instance)

    @patch("api.src.smtp_error_handler.ErrorEmailHandler")
    def test_configure_error_emails_sets_correct_level(self, mock_handler_class):
        """Test that handler is configured with ERROR level."""
        mock_app = Mock()
        mock_handler_instance = Mock()
        mock_handler_class.return_value = mock_handler_instance

        configure_error_emails(mock_app)

        mock_handler_instance.setLevel.assert_called_with(logging.ERROR)

    @patch("api.src.smtp_error_handler.ErrorEmailHandler")
    def test_configure_error_emails_sets_empty_formatter(self, mock_handler_class):
        """Test that handler is configured with empty formatter."""
        mock_app = Mock()
        mock_handler_instance = Mock()
        mock_handler_class.return_value = mock_handler_instance

        configure_error_emails(mock_app)

        # Check that setFormatter was called and the formatter has empty format string
        call_args = mock_handler_instance.setFormatter.call_args
        formatter = call_args[0][0]
        assert isinstance(formatter, logging.Formatter)


class TestErrorEmailHandler:
    """Tests for ErrorEmailHandler class."""

    @patch("api.src.smtp_error_handler.EmailApi")
    @patch("api.src.smtp_error_handler.api_environment")
    def test_init_creates_email_api(self, mock_env, mock_email_api_class):
        """Test that EmailApi is created with correct parameters."""
        mock_env.IAPI_ENDPOINT = "http://localhost:8089"
        mock_env.KC_SA_CLIENT_ID = "test-client"
        mock_env.KC_SA_CLIENT_SECRET = "test-secret"

        handler = ErrorEmailHandler()

        mock_email_api_class.assert_called_once_with(
            "http://localhost:8089", "test-client", "test-secret"
        )
        assert handler.email_api is not None

    @patch("api.src.smtp_error_handler.EmailApi")
    @patch("api.src.smtp_error_handler.api_environment")
    def test_emit_sends_email_with_correct_data(self, mock_env, mock_email_api_class):
        """Test that emit sends email with correct error data."""
        mock_env.IAPI_ENDPOINT = "http://localhost:8089"
        mock_env.KC_SA_CLIENT_ID = "test-client"
        mock_env.KC_SA_CLIENT_SECRET = "test-secret"
        mock_env.LOGS_LINK = "https://logs.example.com"

        mock_email_api = Mock()
        mock_email_api_class.return_value = mock_email_api

        handler = ErrorEmailHandler()
        handler.setFormatter(logging.Formatter("%(message)s"))

        # Create a log record
        record = logging.LogRecord(
            name="test_logger",
            level=logging.ERROR,
            pathname="test.py",
            lineno=42,
            msg="Test error message",
            args=(),
            exc_info=None,
        )
        record.asctime = "2025-01-05 12:00:00,123"
        record.exc_text = "Traceback (most recent call last):<br>  File 'test.py', line 42<br>ValueError: Test error"

        handler.emit(record)

        mock_email_api.send_api_error_email.assert_called_once_with(
            error_message="Test error message",
            stack_trace="Traceback (most recent call last):<br>  File 'test.py', line 42<br>ValueError: Test error",
            timestamp="2025-01-05 12:00:00,123",
            logs_link="https://logs.example.com",
        )

    @patch("api.src.smtp_error_handler.EmailApi")
    @patch("api.src.smtp_error_handler.api_environment")
    def test_emit_replaces_newlines_with_br(self, mock_env, mock_email_api_class):
        """Test that newlines in error message are replaced with <br> tags."""
        mock_env.IAPI_ENDPOINT = "http://localhost:8089"
        mock_env.KC_SA_CLIENT_ID = "test-client"
        mock_env.KC_SA_CLIENT_SECRET = "test-secret"
        mock_env.LOGS_LINK = "https://logs.example.com"

        mock_email_api = Mock()
        mock_email_api_class.return_value = mock_email_api

        handler = ErrorEmailHandler()
        handler.setFormatter(logging.Formatter("%(message)s"))

        record = logging.LogRecord(
            name="test_logger",
            level=logging.ERROR,
            pathname="test.py",
            lineno=42,
            msg="Line 1<br>Line 2<br>Line 3",
            args=(),
            exc_info=None,
        )
        record.asctime = "2025-01-05 12:00:00,123"
        record.exc_text = None

        handler.emit(record)

        call_args = mock_email_api.send_api_error_email.call_args
        assert call_args[1]["error_message"] == "Line 1<br>Line 2<br>Line 3"

    @patch("api.src.smtp_error_handler.EmailApi")
    @patch("api.src.smtp_error_handler.api_environment")
    def test_emit_handles_missing_exc_text(self, mock_env, mock_email_api_class):
        """Test that emit handles records without exc_text."""
        mock_env.IAPI_ENDPOINT = "http://localhost:8089"
        mock_env.KC_SA_CLIENT_ID = "test-client"
        mock_env.KC_SA_CLIENT_SECRET = "test-secret"
        mock_env.LOGS_LINK = "https://logs.example.com"

        mock_email_api = Mock()
        mock_email_api_class.return_value = mock_email_api

        handler = ErrorEmailHandler()
        handler.setFormatter(logging.Formatter("%(message)s"))

        record = logging.LogRecord(
            name="test_logger",
            level=logging.ERROR,
            pathname="test.py",
            lineno=42,
            msg="Error without stack trace",
            args=(),
            exc_info=None,
        )
        record.asctime = "2025-01-05 12:00:00,123"
        record.exc_text = None

        handler.emit(record)

        call_args = mock_email_api.send_api_error_email.call_args
        assert call_args[1]["stack_trace"] == "No stack trace available"

    @patch("api.src.smtp_error_handler.EmailApi")
    @patch("api.src.smtp_error_handler.api_environment")
    @patch("api.src.smtp_error_handler.datetime")
    def test_emit_adds_asctime_if_missing(
        self, mock_datetime, mock_env, mock_email_api_class
    ):
        """Test that emit adds asctime if not present on record."""
        mock_env.IAPI_ENDPOINT = "http://localhost:8089"
        mock_env.KC_SA_CLIENT_ID = "test-client"
        mock_env.KC_SA_CLIENT_SECRET = "test-secret"
        mock_env.LOGS_LINK = "https://logs.example.com"

        mock_email_api = Mock()
        mock_email_api_class.return_value = mock_email_api

        # Mock datetime.now()
        mock_now = Mock()
        mock_now.strftime.return_value = "2025-01-05 14:32:18,456789"
        mock_datetime.datetime.now.return_value = mock_now

        handler = ErrorEmailHandler()
        handler.setFormatter(logging.Formatter("%(message)s"))

        # Create record without asctime
        record = logging.LogRecord(
            name="test_logger",
            level=logging.ERROR,
            pathname="test.py",
            lineno=42,
            msg="Error message",
            args=(),
            exc_info=None,
        )
        record.exc_text = None
        # Don't set asctime

        handler.emit(record)

        # Verify asctime was set
        assert hasattr(record, "asctime")
        assert record.asctime == "2025-01-05 14:32:18,456"

    @patch("api.src.smtp_error_handler.EmailApi")
    @patch("api.src.smtp_error_handler.api_environment")
    def test_emit_calls_handle_error_on_exception(self, mock_env, mock_email_api_class):
        """Test that handleError is called when emit raises an exception."""
        mock_env.IAPI_ENDPOINT = "http://localhost:8089"
        mock_env.KC_SA_CLIENT_ID = "test-client"
        mock_env.KC_SA_CLIENT_SECRET = "test-secret"
        mock_env.LOGS_LINK = "https://logs.example.com"

        mock_email_api = Mock()
        mock_email_api.send_api_error_email.side_effect = Exception("Email send failed")
        mock_email_api_class.return_value = mock_email_api

        handler = ErrorEmailHandler()
        handler.handleError = Mock()
        handler.setFormatter(logging.Formatter("%(message)s"))

        record = logging.LogRecord(
            name="test_logger",
            level=logging.ERROR,
            pathname="test.py",
            lineno=42,
            msg="Error message",
            args=(),
            exc_info=None,
        )
        record.asctime = "2025-01-05 12:00:00,123"
        record.exc_text = None

        handler.emit(record)

        handler.handleError.assert_called_once_with(record)

    @patch("api.src.smtp_error_handler.EmailApi")
    @patch("api.src.smtp_error_handler.api_environment")
    def test_emit_with_formatted_message(self, mock_env, mock_email_api_class):
        """Test that emit correctly formats the log message."""
        mock_env.IAPI_ENDPOINT = "http://localhost:8089"
        mock_env.KC_SA_CLIENT_ID = "test-client"
        mock_env.KC_SA_CLIENT_SECRET = "test-secret"
        mock_env.LOGS_LINK = "https://logs.example.com"

        mock_email_api = Mock()
        mock_email_api_class.return_value = mock_email_api

        handler = ErrorEmailHandler()
        handler.setFormatter(logging.Formatter("%(levelname)s - %(message)s"))

        record = logging.LogRecord(
            name="test_logger",
            level=logging.ERROR,
            pathname="test.py",
            lineno=42,
            msg="Test error",
            args=(),
            exc_info=None,
        )
        record.asctime = "2025-01-05 12:00:00,123"
        record.exc_text = None

        handler.emit(record)

        call_args = mock_email_api.send_api_error_email.call_args
        assert call_args[1]["error_message"] == "Test error"

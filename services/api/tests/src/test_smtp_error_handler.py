from collections import namedtuple
import logging
from unittest.mock import patch, MagicMock, mock_open
from api.src.smtp_error_handler import ErrorEmailHandler
import api.src.smtp_error_handler as smtp_error_handler


def test_get_environment_name_success():
    expected = "test"
    actual = smtp_error_handler.get_environment_name("test:1234")

    assert actual == expected


def test_get_environment_name_fail():
    expected = "True"
    actual = smtp_error_handler.get_environment_name(True)

    assert actual == str(expected)


###################################### Testing Functions ##########################################
@patch(
    "api_environment.CSM_EMAILS_TO_SEND_TO",
    ["test@gmail.com", "test2@gmail.com"],
)
def test_get_subscribed_users_success():
    expected = ["test@gmail.com", "test2@gmail.com"]
    actual = smtp_error_handler.get_subscribed_users()
    assert actual == expected


IAPI_ENDPOINT = "localhost:8089"
KC_SA_CLIENT_ID = "sa_cvmanager_python_api"
KC_SA_CLIENT_SECRET = "sa-python-api-secret-key"
LOGS_LINK = "http://logs_link.com"


def test_configure_error_emails():
    app = MagicMock()
    app.logger = MagicMock()
    app.logger.addHandler = MagicMock()
    smtp_error_handler.configure_error_emails(app)
    app.logger.addHandler.assert_called_once()


@patch("api_environment.LOGS_LINK", LOGS_LINK)
@patch("api_environment.IAPI_ENDPOINT", IAPI_ENDPOINT)
@patch("api_environment.KC_SA_CLIENT_ID", KC_SA_CLIENT_ID)
@patch("api_environment.KC_SA_CLIENT_SECRET", KC_SA_CLIENT_SECRET)
@patch("builtins.open", new_callable=mock_open, read_data="data")
@patch("api.src.smtp_error_handler.smtplib")
def test_send(mock_smtplib, mock_file):
    # prepare
    emailHandler = ErrorEmailHandler()

    emailHandler.email_api = MagicMock()
    emailHandler.email_api.send_api_error_email = MagicMock()
    emailHandler.email_api.send_api_error_email.return_value = None

    Record = namedtuple("Record", ["asctime"])
    record = Record("2023-09-15 00:00:00,000000")
    emailHandler.format = lambda x: str(x)

    logging.error("mock_smtplib", mock_smtplib)

    # execute
    emailHandler.emit(record)

    # assert
    emailHandler.email_api.send_api_error_email.assert_called_once_with(
        str(record).replace("\n", "<br>"),
        "No stack trace available",
        "2023-09-15 00:00:00,000000",
        LOGS_LINK,
    )

import logging
import datetime
from typing import TypedDict
import requests


class KeycloakToken(TypedDict):
    access_token: str
    expires_in: int
    refresh_expires_in: int
    refresh_token: str
    token_type: str
    id_token: str
    not_before_policy: str
    session_state: str
    scope: str


class EmailApi:
    def __init__(self, iapi_base_url, iapi_username, iapi_password):
        """
        Initialize the EmailApi with the base URL, username, and password.

        Args:
            iapi_base_url (str): The base URL for the email API.
            iapi_username (str): The username for authentication.
            iapi_password (str): The password for authentication.
        """
        self.iapi_endpoint = iapi_base_url
        self.username = iapi_username
        self.password = iapi_password
        self.token: KeycloakToken | None = None
        self.token_expiration_date: datetime.datetime

    def gen_keycloak_token(self) -> tuple[int, KeycloakToken]:
        """
        Request a new Keycloak token from the authentication endpoint.

        Returns:
            tuple[int, KeycloakToken]: The HTTP status code and the token dictionary.
        """
        response = requests.post(f"{self.iapi_endpoint}/auth/token")
        if response.status_code != 200:
            logging.error(
                f"Failed to generate Keycloak token: {response.status_code} - {response.text}"
            )
        return response.status_code, response.json()

    def is_current_token_valid(self) -> bool:
        """
        Check if the current Keycloak token is valid (not expired).

        Returns:
            bool: True if the token exists and is not expired, False otherwise.
        """
        return (
            self.token is not None
            and datetime.datetime.now() < self.token_expiration_date
        )

    def get_kc_token(self) -> KeycloakToken | None:
        """
        Get a valid Keycloak token, regenerating it if necessary.

        Returns:
            KeycloakToken | None: The valid token dictionary, or None if unable to obtain one.
        """
        if self.is_current_token_valid():
            return self.token

        # TODO: Implement token refresh logic if refresh_token still valid
        status_code, token = self.gen_keycloak_token()
        if status_code == 200:
            self.token = token
            self.token_expiration_date = datetime.datetime.now() + datetime.timedelta(
                milliseconds=token["expires_in"]
            )
        else:
            logging.error("Failed to obtain initial Keycloak token.")
            return None
        return self.token

    def send_message_counts(
        self,
        org_name: str,
        deployment_title: str,
        start_date: datetime.datetime,
        end_date: datetime.datetime,
        message_type_list: list[str],
        counts: list[dict],
    ) -> tuple[int, str]:
        """
        Send a message counts email via the API.

        Args:
            org_name (str): Organization name.
            deployment_title (str): Deployment title.
            primary_route (str): Primary route.
            start_date (datetime.datetime): Start date.
            end_date (datetime.datetime): End date.
            message_type_list (list[str]): List of message types.
            counts (list[dict]): List of count dictionaries.

        Returns:
            tuple[int, str]: The HTTP status code and the response JSON.
        """
        token = self.get_kc_token()
        response = requests.post(
            f"{self.iapi_endpoint}/emails/send-message-counts",
            headers={"Authorization": f"bearer {token}"},
            json={
                "org_name": org_name,
                "deployment_title": deployment_title,
                "start_date": start_date,
                "end_date": end_date,
                "message_type_list": message_type_list,
                "counts": counts,
            },
        )
        if not (200 <= response.status_code < 300):
            logging.error(
                f"Failed to send message counts email: {response.status_code} - {response.text}"
            )
        return response.status_code, response.json()

    def send_firmware_upgrade_failure(
        self, rsu_ip: str, error_message: str, failure_type: str, stack_trace: str
    ):
        """
        Send a firmware upgrade failure email via the API.

        Args:
            rsu_ip (str): RSU IP address.
            error_message (str): Error message.
            failure_type (str): Type of failure.
            stack_trace (str): Stack trace.

        Returns:
            tuple[int, str]: The HTTP status code and the response JSON.
        """
        token = self.get_kc_token()
        response = requests.post(
            f"{self.iapi_endpoint}/emails/send-firmware-upgrade-failure",
            headers={"Authorization": f"bearer {token}"},
            json={
                "rsu_ip": rsu_ip,
                "error_message": error_message,
                "failure_type": failure_type,
                "stack_trace": stack_trace,
            },
        )
        if not (200 <= response.status_code < 300):
            logging.error(
                f"Failed to send firmware upgrade failure email: {response.status_code} - {response.text}"
            )
        return response.status_code, response.json()

    def send_rsu_error_summary(self, recipients: list[str], subject: str, message: str):
        """
        Send an RSU error summary email via the API.

        Args:
            recipients (list[str]): List of recipient email addresses.
            subject (str): Email subject.
            message (str): Email message body.

        Returns:
            tuple[int, str]: The HTTP status code and the response JSON.
        """
        token = self.get_kc_token()
        response = requests.post(
            f"{self.iapi_endpoint}/emails/send-rsu-error-summary",
            headers={"Authorization": f"bearer {token}"},
            json={"recipients": recipients, "subject": subject, "message": message},
        )
        if not (200 <= response.status_code < 300):
            logging.error(
                f"Failed to send RSU error summary email: {response.status_code} - {response.text}"
            )
        return response.status_code, response.json()

    def send_api_error_email(
        self,
        error_message: str,
        stack_trace: str,
        timestamp: str,
        logs_link: str,
    ):
        """
        Send a critical api error email via the API.

        Args:
            error_message (str): Error message.
            stack_trace (str): Stack trace.
            timestamp (str): Timestamp of the error in ISO format.
            logs_link (str): Link to the logs.

        Returns:
            tuple[int, str]: The HTTP status code and the response JSON.
        """
        token = self.get_kc_token()
        response = requests.post(
            f"{self.iapi_endpoint}/emails/send-api-error",
            headers={"Authorization": f"bearer {token}"},
            json={
                "error_message": error_message,
                "stack_trace": stack_trace,
                "timestamp": timestamp,
                "logs_link": logs_link,
            },
        )
        if not (200 <= response.status_code < 300):
            logging.error(
                f"Failed to send API error email: {response.status_code} - {response.text}"
            )
        return response.status_code, response.json()

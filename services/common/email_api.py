import logging
import datetime
from typing import TypedDict
import requests
from common.keycloak_api import KeycloakServiceAccountApi


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
    def __init__(self, iapi_base_url, kc_api: KeycloakServiceAccountApi):
        """
        Initialize the EmailApi with the base URL, username, and password.

        Args:
            iapi_base_url (str): The base URL for the email API.
            kc_client_id (str): The Keycloak client ID for authentication.
            kc_client_secret (str): The Keycloak client secret for authentication.
        """
        self.iapi_endpoint = iapi_base_url
        self.kc_api = kc_api

    def send_message_counts(
        self,
        org_name: str,
        deployment_title: str,
        start_date: datetime.datetime,
        end_date: datetime.datetime,
        message_type_list: list[str],
        rsu_counts: list[dict],
    ) -> tuple[int, dict]:
        """
        Send a message counts email via the API.

        Args:
            org_name (str): Organization name.
            deployment_title (str): Deployment title.
            primary_route (str): Primary route.
            start_date (datetime.datetime): Start date.
            end_date (datetime.datetime): End date.
            message_type_list (list[str]): List of message types.
            rsu_counts (list[dict]): List of count dictionaries.

        Returns:
            tuple[int, str]: The HTTP status code and the response JSON.
        """
        token = self.kc_api.get_kc_token()
        if not token:
            return 500, {"error": "Unable to obtain Keycloak token."}
        response = requests.post(
            f"{self.iapi_endpoint}/emails/message-counts",
            headers={"Authorization": f"Bearer {token['access_token']}"},
            json={
                "org_name": org_name,
                "deployment_title": deployment_title,
                "start_date": start_date.timestamp(),
                "end_date": end_date.timestamp(),
                "message_type_list": message_type_list,
                "rsu_counts": rsu_counts,
            },
        )
        if not (200 <= response.status_code < 300):
            logging.error(
                f"Failed to send message counts email: {response.status_code} - {response.text}"
            )
        return response.status_code, response.json()

    def send_firmware_upgrade_failure(
        self, rsu_ip: str, error_message: str, failure_type: str, stack_trace: str
    ) -> tuple[int, dict]:
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
        token = self.kc_api.get_kc_token()
        if not token:
            return 500, {"error": "Unable to obtain Keycloak token."}

        response = requests.post(
            f"{self.iapi_endpoint}/emails/firmware-upgrade-failures",
            headers={"Authorization": f"Bearer {token['access_token']}"},
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

    def send_api_error_email(
        self,
        error_message: str,
        stack_trace: str,
        timestamp: str,
        logs_link: str,
    ) -> tuple[int, dict]:
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
        token = self.kc_api.get_kc_token()
        if not token:
            return 500, {"error": "Unable to obtain Keycloak token."}
        response = requests.post(
            f"{self.iapi_endpoint}/emails/api-errors",
            headers={"Authorization": f"Bearer {token['access_token']}"},
            json={
                "error_message": error_message,
                "stack_trace": stack_trace,
                "timestamp": timestamp,
                "logs_link": logs_link,
            },
            timeout=10,
        )
        if not (200 <= response.status_code < 300):
            logging.error(
                f"Failed to send API error email: {response.status_code} - {response.text}"
            )
        return response.status_code, response.json()

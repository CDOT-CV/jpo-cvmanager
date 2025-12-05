import logging
from logging import Handler
import datetime
import api_environment
from common.email_api import EmailApi


def configure_error_emails(app):
    mail_handler = ErrorEmailHandler()
    mail_handler.setLevel(logging.ERROR)
    # this seems weird, but it's the only way I can figure out how to include the stack trace info. This command appends the stack trace to the end of the self.format(record) call.
    mail_handler.setFormatter(logging.Formatter(""))
    app.logger.addHandler(mail_handler)


def get_environment_name(instance_connection_name: str) -> str:
    try:
        return instance_connection_name.split(":")[0]
    except (AttributeError, IndexError):
        return str(instance_connection_name)


class ErrorEmailHandler(Handler):
    def __init__(self):
        super().__init__()  # initialize handler
        self.email_api = EmailApi(
            api_environment.IAPI_ENDPOINT,
            api_environment.KC_USERNAME,
            api_environment.KC_PASSWORD,
        )

    def generate_message(self, environment_name, error_message, error_time, logs_link):
        return f"""<p>You are receiving this email because you have been included in the CV Manager developer group.</p>
            <br />
            <p>This error originated in the {environment_name} environment CV Manager API</p>
            <br />
            <p>Error Message: {error_message}</p>
            <br />
            <p>Error occurred at: {error_time}</p>
            <br />
            <p>View this error in Logs: <a href="{logs_link}">rsu-manager-api logs</a></p>"""

    def emit(self, record):
        try:
            if not hasattr(record, "asctime"):
                # For some reason, asctime is not always available. So we update it to the current time in the same format (2023-08-23 15:39:29,115)
                record.asctime = datetime.datetime.now().strftime(
                    "%Y-%m-%d %H:%M:%S,%f"
                )[:-3]

            message = self.generate_message(
                environment_name=api_environment.ENVIRONMENT_NAME,
                error_message=self.format(record).replace("\n", "<br>"),
                error_time=str(record.asctime),
                logs_link=api_environment.LOGS_LINK,
            )
            self.email_api.send_api_error_email(
                subject=self.subject,
                message=message,
            )

        except Exception:
            self.handleError(record)

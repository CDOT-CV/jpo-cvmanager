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


class ErrorEmailHandler(Handler):
    def __init__(self):
        super().__init__()  # initialize handler
        self.email_api = EmailApi(
            api_environment.IAPI_ENDPOINT,
            api_environment.KC_SA_CLIENT_ID,
            api_environment.KC_SA_CLIENT_SECRET,
        )

    def emit(self, record):
        try:
            if not hasattr(record, "asctime"):
                # For some reason, asctime is not always available. So we update it to the current time in the same format (2023-08-23 15:39:29,115)
                record.asctime = datetime.datetime.now().strftime(
                    "%Y-%m-%d %H:%M:%S,%f"
                )[:-3]

            # Ensure stack_trace is always a string
            stack_trace = record.exc_text if record.exc_text else "No stack trace available"
            stack_trace = str(stack_trace).replace("\n", "<br>")

            self.email_api.send_api_error_email(
                error_message=record.getMessage().replace("\n", "<br>"),
                stack_trace=stack_trace,
                timestamp=record.asctime,
                logs_link=api_environment.LOGS_LINK,
            )

        except Exception:
            self.handleError(record)

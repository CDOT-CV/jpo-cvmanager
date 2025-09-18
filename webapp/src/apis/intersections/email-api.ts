import { authApiHelper } from './api-helper-cviz'

class EmailApi {
  async sendRsuErrorSummary({
    token,
    emailContents,
  }: {
    token: string
    emailContents: RsuErrorSummaryEmailContents
  }): Promise<String> {
    const response = await authApiHelper.invokeApi({
      path: '/emails/send-rsu-error-summary',
      token: token,
      method: 'POST',
      body: emailContents,
      failureMessage: 'Failed to send RSU error summary email(s), please try again',
      successMessage: 'Successfully sent RSU error summary email(s)',
      booleanResponse: true,
    })
    return response
  }
}

export default new EmailApi()

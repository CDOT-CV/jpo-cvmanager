import { authApiHelper } from './api-helper-cviz'

class UserNotificationApi {
  async sendSupportRequest({ emailContents }: { emailContents: SupportRequestEmailContents }): Promise<boolean> {
    const response = await authApiHelper.invokeApi({
      path: '/users/submit-support-request',
      method: 'POST',
      body: emailContents,
      booleanResponse: true,
      failureMessage: 'Failed to submit support request, please try again',
      successMessage: 'Successfully submitted support request',
    })
    return response
  }
}

export default new UserNotificationApi()

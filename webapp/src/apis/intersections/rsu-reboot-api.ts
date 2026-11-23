import { authApiHelper } from './api-helper-cviz'

/** Requests background execution; acceptance does not mean the RSU has restarted. */
class RsuRebootApi {
  async reboot(token: string, organization: string, rsuIp: string): Promise<{ requestId: string; status: 'ACCEPTED' }> {
    const response = await authApiHelper.invokeApi({
      path: `/devices/rsus/reboot/${encodeURIComponent(rsuIp)}`,
      method: 'POST',
      token,
      headers: { Organization: organization },
      tag: 'rsu',
      returnErrorBody: true,
      toastOnFailure: false,
      failureMessage: 'Reboot request did not complete; check RSU state before retrying',
    })

    if (response?.__isErrorResponse) {
      throw new Error(response.body?.detail || 'Reboot request was not accepted')
    }

    // A missing acceptance leaves the outcome uncertain; do not retry automatically.
    if (response?.status !== 'ACCEPTED' || typeof response.requestId !== 'string') {
      throw new Error('Reboot request was not confirmed; check RSU state before retrying')
    }

    return response
  }
}

export default new RsuRebootApi()

import { ApiMsgRespWithCodes, RsuUpgradePostBody } from '../../models/RsuApi'
import { authApiHelper } from './api-helper-cviz'

class RsuFirmwareApi {
  async postRsuUpgradeData(
    token: string,
    org: string,
    body: RsuUpgradePostBody,
    url_ext = ''
  ): Promise<ApiMsgRespWithCodes<any> | null> {
    const response = await authApiHelper.invokeApi({
      path: `/devices/rsus/upgrade${url_ext}`,
      method: 'POST',
      token,
      headers: { Organization: org },
      body,
      tag: 'rsu',
      toastOnFailure: false,
      failureMessage: 'Failed to submit RSU firmware upgrade request',
    })

    if (!response) {
      return null
    }

    return {
      body: response,
      status: 200,
      message: response?.message,
    }
  }
}

export default new RsuFirmwareApi()

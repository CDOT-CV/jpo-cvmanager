import { MessageCount } from '../../models/MessageCount'
import { authApiHelper } from './api-helper-cviz'

class CountsApi {
  async getOrganizationCounts({
    token,
    organization,
    query_params,
    abortController,
  }: {
    token: string
    organization: string
    query_params?: Record<string, string>
    abortController?: AbortController
  }): Promise<MessageCount[]> {
    const response = await authApiHelper.invokeApi({
      path: `/data/counts/rsus/organizations/${organization}`,
      token: token,
      queryParams: query_params,
      abortController,
      failureMessage: 'Failed to retrieve organization counts',
      tag: 'rsu',
    })
    return response ?? []
  }

  async getRsuCounts({
    token,
    rsuIp,
    startTime,
    endTime,
    abortController,
  }: {
    token: string
    rsuIp: string
    startTime: number
    endTime: number
    abortController?: AbortController
  }): Promise<MessageCount[]> {
    const response = await authApiHelper.invokeApi({
      path: `/data/counts/rsus/${rsuIp}`,
      token: token,
      queryParams: {
        start_time_utc_millis: startTime.toString(),
        end_time_utc_millis: endTime.toString(),
      },
      abortController,
      failureMessage: 'Failed to retrieve RSU counts',
      tag: 'rsu',
    })
    return response ?? []
  }
}

export default new CountsApi()

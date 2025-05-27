import { apiHelper } from '../api-helper'

class MessageMonitorApi {
  async getMapMessages({
    token,
    intersectionId,
    startTime,
    endTime,
    latest,
    abortController,
  }: {
    token: string
    intersectionId: number
    startTime?: Date
    endTime?: Date
    latest?: boolean
    abortController?: AbortController
  }): Promise<ProcessedMap[]> {
    const queryParams: Record<string, string> = {}
    queryParams['intersection_id'] = intersectionId.toString()
    if (startTime) queryParams['start_time_utc_millis'] = startTime.getTime().toString()
    if (endTime) queryParams['end_time_utc_millis'] = endTime.getTime().toString()
    if (latest !== undefined) queryParams['latest'] = latest.toString()

    var response = await apiHelper.invokeApi({
      path: '/data/map',
      token: token,
      queryParams,
      abortController,
      failureMessage: 'Failed to retrieve MAP messages',
      tag: 'intersection',
    })
    return response?.content ?? ([] as ProcessedMap[])
  }
}

export default new MessageMonitorApi()

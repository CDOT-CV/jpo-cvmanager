import { authApiHelper } from './api-helper-cviz'

class SsmSrmApi {
  async getSsmMessages({
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
  }): Promise<ProcessedSsm[]> {
    const queryParams: Record<string, string> = {}
    queryParams['intersection_id'] = intersectionId.toString()
    if (startTime) queryParams['start_time_utc_millis'] = startTime.getTime().toString()
    if (endTime) queryParams['end_time_utc_millis'] = endTime.getTime().toString()
    if (latest !== undefined) queryParams['latest'] = latest.toString()

    const response = await authApiHelper.invokeApi({
      path: '/data/processed-ssm',
      token: token,
      queryParams,
      abortController,
      failureMessage: 'Failed to retrieve MAP messages',
      tag: 'intersection',
    })
    return response?.content ?? ([] as ProcessedSsm[])
  }

  async getSrmMessages({
    token,
    vehicleId,
    startTime,
    endTime,
    long,
    lat,
    distance,
    abortController,
  }: {
    token: string
    vehicleId?: string
    startTime?: Date
    endTime?: Date
    long?: number
    lat?: number
    distance?: number
    abortController?: AbortController
  }): Promise<ProcessedSrmFeature[]> {
    const queryParams: Record<string, string> = {}
    if (vehicleId) queryParams['origin_ip'] = vehicleId
    if (startTime) queryParams['start_time_utc_millis'] = startTime.getTime().toString()
    if (endTime) queryParams['end_time_utc_millis'] = endTime.getTime().toString()
    if (long) queryParams['longitude'] = long.toString()
    if (lat) queryParams['latitude'] = lat.toString()
    if (distance) queryParams['distance'] = distance.toString()

    const response: PagedResponse<ProcessedSrmFeature> = await authApiHelper.invokeApi({
      path: '/data/processed-srm',
      token: token,
      queryParams,
      abortController,
      failureMessage: 'Failed to retrieve SRM messages',
      tag: 'intersection',
    })
    return response?.content ?? ([] as ProcessedSrmFeature[])
  }
}

export default new SsmSrmApi()

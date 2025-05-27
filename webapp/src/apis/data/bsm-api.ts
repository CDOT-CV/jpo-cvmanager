import { apiHelper } from '../api-helper'

class BsmApi {
  async getBsmMessages({
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
  }): Promise<OdeBsmData[]> {
    const queryParams: Record<string, string> = {}
    if (vehicleId) queryParams['origin_ip'] = vehicleId
    if (startTime) queryParams['start_time_utc_millis'] = startTime.getTime().toString()
    if (endTime) queryParams['end_time_utc_millis'] = endTime.getTime().toString()
    if (long) queryParams['longitude'] = long.toString()
    if (lat) queryParams['latitude'] = lat.toString()
    if (distance) queryParams['distance'] = distance.toString()

    var response: PagedResponse<OdeBsmData> = await apiHelper.invokeApi({
      path: '/data/bsm',
      token: token,
      queryParams,
      abortController,
      failureMessage: 'Failed to retrieve BSM messages',
      tag: 'intersection',
    })
    return response?.content ?? ([] as OdeBsmData[])
  }
}

export default new BsmApi()

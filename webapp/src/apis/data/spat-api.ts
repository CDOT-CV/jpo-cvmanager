import { apiHelper } from '../api-helper'

class SpatApi {
  /**
   * Retrieves SPAT (Signal Phase and Timing) messages for a specific intersection,
   * including the latest SPAT message before the specified start time and all SPAT messages
   * within the specified time range
   *
   * @param {Object} params - The parameters for the API request.
   * @param {string} params.token - The authentication token for the API request.
   * @param {number} params.intersectionId - The ID of the intersection to filter SPAT messages.
   * @param {Date} [params.startTime] - The start time of the time range (optional).
   * @param {Date} [params.endTime] - The end time of the time range (optional).
   * @param {boolean} [params.compact] - Whether to request a compact version of the SPAT messages (optional).
   * @param {AbortController} [params.abortController] - Optional AbortController to cancel the API request.
   * @returns {Promise<ProcessedSpat[]>} - A promise that resolves to an array of processed SPAT messages.
   *
   * @throws {Error} - Throws an error if the API request fails.
   *
   * @description
   * This function retrieves SPAT messages for a specific intersection, including the latest SPAT message
   * before the specified start time and all SPAT messages within the specified time range.
   * This is intended to account for querying de-duplicated data, in which data within a specified time range may be sparse.
   * This function queries for data within the time range, as well as retrieving the latest SPaT message before the time range.
   * This ensures that there is data available for the start of the time range.
   */
  async getSpatMessagesWithLatest({
    token,
    intersectionId,
    startTime,
    endTime,
    compact,
    abortController,
  }: {
    token: string
    intersectionId: number
    startTime?: Date
    endTime?: Date
    compact?: boolean
    abortController?: AbortController
  }): Promise<ProcessedSpat[]> {
    // Retrieve latest data before time interval
    const latestSpats = await this.getSpatMessages({
      token,
      intersectionId,
      endTime: startTime,
      latest: true,
      compact,
      abortController,
    })
    // Retrieve data within time interval
    const allSpats = await this.getSpatMessages({
      token,
      intersectionId,
      startTime,
      endTime,
      compact,
      abortController,
    })
    return [...allSpats, ...latestSpats].filter((spat) => spat != null)
  }

  async getSpatMessages({
    token,
    intersectionId,
    startTime,
    endTime,
    latest,
    compact,
    abortController,
  }: {
    token: string
    intersectionId: number
    startTime?: Date
    endTime?: Date
    latest?: boolean
    compact?: boolean
    abortController?: AbortController
  }): Promise<ProcessedSpat[]> {
    const queryParams: Record<string, string> = {}
    queryParams['intersection_id'] = intersectionId.toString()
    if (startTime) queryParams['start_time_utc_millis'] = startTime.getTime().toString()
    if (endTime) queryParams['end_time_utc_millis'] = endTime.getTime().toString()
    if (latest) queryParams['latest'] = latest.toString()
    if (compact) queryParams['compact'] = compact.toString()

    var response: PagedResponse<ProcessedSpat> = await apiHelper.invokeApi({
      path: '/data/spat',
      token: token,
      queryParams,
      abortController,
      failureMessage: 'Failed to retrieve SPAT messages',
      tag: 'intersection',
    })
    return response?.content ?? ([] as ProcessedSpat[])
  }
}

export default new SpatApi()

// Need to use the React-specific entry point to import createApi
import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'
import { combineUrlPaths } from '../../apis/intersections/api-helper-cviz'
import {
  addSrmTimestampsAndSortAscending,
  addSsmTimestampsAndSortAscending,
} from '../intersections/map/utilities/message-utils'

export type LocationParams = {
  longitude: number
  latitude: number
  distance: number
}

export type TimeWindow = {
  startMillis: number
  endMillis: number
}

const getQueryString = (query_params: Record<string, string>) => {
  // filter out undefined values from query params
  const filteredQueryParams: Record<string, string> = { ...query_params }
  Object.keys(filteredQueryParams).forEach((key) => query_params[key] === undefined && delete query_params[key])
  const queryString = new URLSearchParams(query_params).toString()
  return `${queryString ? `?${queryString}` : ''}`
}

// Define a service using a base URL and expected endpoints
export const intersectionMapApiSlice = createApi({
  reducerPath: 'intersectionMapApi',
  baseQuery: fetchBaseQuery({
    baseUrl: combineUrlPaths(EnvironmentVars.CVIZ_API_SERVER_URL, '/data'),
    prepareHeaders: (headers, { getState, endpoint }) => {
      const token = selectToken(getState() as RootState)

      // Specify endpoints that do not require a token or organization. These names must match the keys in the endpoints object below.
      const endpointsWithoutToken = []

      if (token && !endpointsWithoutToken.includes(endpoint)) {
        headers.set('Authorization', `Bearer ${token}`)
      }

      return headers
    },
  }),
  tagTypes: ['defaultConfigs', 'intersectionConfigs'],
  endpoints: (builder) => ({
    getSsmLatest: builder.query<ProcessedSsm[], number>({
      query: (intersectionId: number) => {
        return `/processed-srm${getQueryString({
          intersection_id: intersectionId.toString(),
          latest: 'true',
        })}`
      },
      transformResponse: (response: { content: ProcessedSsm[] }) => addSsmTimestampsAndSortAscending(response.content),
    }),
    getSsmWithinTimeWindow: builder.query<ProcessedSsm[], { intersectionId: number; timeWindow: TimeWindow }>({
      query: ({ intersectionId, timeWindow }) => {
        return `/processed-srm${getQueryString({
          intersection_id: intersectionId.toString(),
          start_time_utc_millis: timeWindow.startMillis.toString(),
          end_time_utc_millis: timeWindow.endMillis.toString(),
        })}`
      },
      transformResponse: (response: { content: ProcessedSsm[] }) => addSsmTimestampsAndSortAscending(response.content),
    }),
    getSrmLatest: builder.query<ProcessedSrmFeature[], LocationParams>({
      query: (loc: LocationParams) => {
        return `/processed-ssm${getQueryString({
          longitude: loc.longitude.toString(),
          latitude: loc.latitude.toString(),
          distance: loc.distance.toString(),
          latest: 'true',
        })}`
      },
      transformResponse: (response: { content: ProcessedSrmFeature[] }) =>
        addSrmTimestampsAndSortAscending(response.content),
    }),
    getSrmWithinTimeWindow: builder.query<ProcessedSrmFeature[], { loc: LocationParams; timeWindow: TimeWindow }>({
      query: ({ loc, timeWindow }) => {
        return `/processed-ssm${getQueryString({
          longitude: loc.longitude.toString(),
          latitude: loc.latitude.toString(),
          distance: loc.distance.toString(),
          start_time_utc_millis: timeWindow.startMillis.toString(),
          end_time_utc_millis: timeWindow.endMillis.toString(),
        })}`
      },
      transformResponse: (response: { content: ProcessedSrmFeature[] }) =>
        addSrmTimestampsAndSortAscending(response.content),
    }),
  }),
})

// Export hooks for usage in functional components, which are
// auto-generated based on the defined endpoints
export const {
  useGetSsmLatestQuery,
  useGetSsmWithinTimeWindowQuery,
  useGetSrmLatestQuery,
  useGetSrmWithinTimeWindowQuery,
} = intersectionMapApiSlice

function upperBound<T>(messages: T[], target: number, getValue: (message: T) => number) {
  if (messages.length == 0) return 0
  // Finds the index of the first message with timestamp greater than target
  let lo = 0
  let hi = messages.length
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    if (getValue(messages[mid]) <= target) lo = mid + 1
    else hi = mid
  }
  return lo
}

function lowerBound<T>(messages: T[], target: number, getValue: (message: T) => number, hi: number) {
  if (messages.length == 0) return 0
  let lo = 0
  while (lo < hi) {
    const mid = (lo + hi) >> 1 // integer divide by 2
    if (getValue(messages[mid]) < target) lo = mid + 1
    else hi = mid
  }
  return lo
}

const filterSsms = (ssms: ProcessedSsm[], timeWindow: TimeWindow) => {
  const upper = upperBound(ssms, timeWindow.endMillis, (ssm) => ssm.timeStampEpochMillis)
  const lower = lowerBound(ssms, timeWindow.startMillis, (ssm) => ssm.timeStampEpochMillis, upper)
  return ssms.slice(lower, upper)
}

const filterSrms = (srms: ProcessedSrmFeature[], timeWindow: TimeWindow) => {
  const upper = upperBound(srms, timeWindow.endMillis, (srm) => srm.properties.timeStampEpochMillis)
  const lower = lowerBound(srms, timeWindow.startMillis, (srm) => srm.properties.timeStampEpochMillis, upper)
  return srms.slice(lower, upper)
}

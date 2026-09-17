import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'
import { MessageCount } from '../../models/MessageCount'
import { MessageType } from '../../models/MessageTypes'
import { getQueryString } from './intersectionConfigSlice'

/** Intersection API base URL from webapp/.env.local (VITE_CVIZ_API_SERVER_URL). */
const getIntersectionApiBaseUrl = () => (process.env.VITE_CVIZ_API_SERVER_URL ?? '').replace(/\/$/, '')

export const rsuCountsApiSlice = createApi({
  reducerPath: 'rsuCountsApi',
  baseQuery: fetchBaseQuery({
    baseUrl: getIntersectionApiBaseUrl(),
    prepareHeaders: (headers, { getState }) => {
      const token = selectToken(getState() as RootState)
      if (token) {
        headers.set('Authorization', `Bearer ${token}`)
      }
      return headers
    },
  }),
  endpoints: (builder) => ({
    getRsuCounts: builder.query<
      MessageCount[],
      { organization: string; startDate: Date; endDate: Date; message: MessageType | string }
    >({
      query: ({ organization, startDate, endDate, message }) => {
        const baseUrl = getIntersectionApiBaseUrl()
        if (!baseUrl) {
          throw new Error('VITE_CVIZ_API_SERVER_URL is not set (expected in webapp/.env.local)')
        }

        // Intersection API: GET /data/counts/rsus/organizations/{org}
        // Not the CV Manager gateway /rsucounts endpoint.
        return {
          url: `/data/counts/rsus/organizations/${encodeURIComponent(organization)}${getQueryString({
            message: String(message),
            start_time_utc_millis: startDate.getTime().toString(),
            end_time_utc_millis: endDate.getTime().toString(),
          })}`,
          headers: { Organization: organization },
        }
      },
      transformResponse: (response: MessageCount[] | null | undefined) => response ?? [],
    }),
    getRsuCountsByIp: builder.query<
      MessageCount[],
      { rsuIp: string; startDate: Date; endDate: Date; messages: string[] }
    >({
      query: ({ rsuIp, startDate, endDate, messages }) => {
        const baseUrl = getIntersectionApiBaseUrl()
        if (!baseUrl) {
          throw new Error('VITE_CVIZ_API_SERVER_URL is not set (expected in webapp/.env.local)')
        }

        return {
          url: `/data/counts/rsus/${encodeURIComponent(rsuIp)}${getQueryString({
            ...(messages.length > 0 ? { message: messages.join(',') } : {}),
            start_time_utc_millis: startDate.getTime().toString(),
            end_time_utc_millis: endDate.getTime().toString(),
          })}`,
        }
      },
      transformResponse: (response: MessageCount[] | null | undefined) => response ?? [],
    }),
  }),
})

const DEFAULT_RSU_COUNTS_ERROR = 'Failed to load message counts from Intersection API.'

/**
 * User-facing message for Intersection API counts failures.
 * Prefers RFC 7807 ProblemDetail.detail (for example VictoriaMetrics OOM mapped to HTTP 400).
 */
export function getRsuCountsErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'data' in error && error.data && typeof error.data === 'object') {
    const detail = (error.data as { detail?: unknown }).detail
    if (typeof detail === 'string' && detail.trim()) {
      return detail
    }
  }
  return DEFAULT_RSU_COUNTS_ERROR
}

export const {
  useGetRsuCountsQuery,
  useLazyGetRsuCountsQuery,
  useGetRsuCountsByIpQuery,
  util: rsuCountsApiUtil,
} = rsuCountsApiSlice

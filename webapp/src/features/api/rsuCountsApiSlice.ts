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
    prepareHeaders: (headers, { getState, endpoint }) => {
      const currentState = getState() as RootState
      const token = selectToken(currentState)

      if (token) {
        headers.set('Authorization', `${token}`)
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
        }
      },
      transformResponse: (response: MessageCount[] | null | undefined) => response ?? [],
    }),
  }),
})

export const { useGetRsuCountsQuery, useLazyGetRsuCountsQuery, util: rsuCountsApiUtil } = rsuCountsApiSlice

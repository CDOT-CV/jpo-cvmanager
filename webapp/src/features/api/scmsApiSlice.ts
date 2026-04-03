import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'

/**
 * API slice for SCMS health status.
 * Provides RSU certificate health information for a given organization.
 */

// Types matching the API response structure

// Individual SCMS health record for an RSU
export type ScmsHealthDto = {
  // "1" = healthy/up-to-date, "0" = unhealthy/out-of-date, null = unknown
  health: '0' | '1' | null
  // Certificate expiration timestamp, e.g. "04/10/2026 01:28:01 PM"
  expiration: string | null
}

// Map of RSU IPv4 addresses to their health status. Null values indicate no health record.
export type ScmsHealthStatus = {
  [ip: string]: ScmsHealthDto | null
}

// Raw API response wrapper - the API returns the map inside a scmsHealthByIp field
type ScmsHealthResponse = {
  scmsHealthByIp: ScmsHealthStatus
}

// Tag type constants
const SCMS_API_STATUS_TAG = 'ScmsStatus' as const

export const scmsApiSlice = createApi({
  reducerPath: 'scmsApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${EnvironmentVars.CVIZ_API_SERVER_URL}/scms`,
    prepareHeaders: (headers, { getState, endpoint }) => {
      const currentState = getState() as RootState
      const token = selectToken(currentState)

      // Endpoint names must match the keys in the endpoints object below
      const endpointsWithoutToken: string[] = []
      if (token && !endpointsWithoutToken.includes(endpoint)) {
        headers.set('Authorization', `Bearer ${token}`)
      }

      return headers
    },
  }),
  tagTypes: [SCMS_API_STATUS_TAG],
  endpoints: (builder) => ({
    getScmsStatus: builder.query<ScmsHealthStatus, string>({
      query: (organization) => ({
        url: '/status',
        headers: {
          Organization: organization,
        },
      }),
      // Unwrap the response so consumers get the map directly without needing to access .scmsHealthByIp
      transformResponse: (response: ScmsHealthResponse) => response.scmsHealthByIp,
      providesTags: [SCMS_API_STATUS_TAG],
    }),
  }),
})

export const { useGetScmsStatusQuery, useLazyGetScmsStatusQuery } = scmsApiSlice


import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'

// Types
export type ScmsHealthStatus = {
  [ip: string]: {
    health: '0' | '1'
    expiration: string
  }
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
      providesTags: [SCMS_API_STATUS_TAG],
    }),
  }),
})

export const { useGetScmsStatusQuery, useLazyGetScmsStatusQuery } = scmsApiSlice


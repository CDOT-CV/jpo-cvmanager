import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'
import { WZDxWorkZoneFeed } from '../../models/wzdx/WzdxWorkZoneFeed42'

// Tag type constants
export const WZDX_FEED_TAG = 'WZDxFeed' as const

export const wzdxFeedApi = createApi({
  reducerPath: 'wzdxFeedApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${EnvironmentVars.CVIZ_API_SERVER_URL}/wzdx-feed`,
    prepareHeaders: (headers, { getState }) => {
      const currentState = getState() as RootState
      const token = selectToken(currentState)

      if (token) {
        headers.set('Authorization', `Bearer ${token}`)
        headers.set('Content-Type', 'application/json')
        headers.set('Accept', 'application/json')
      }

      return headers
    },
  }),
  tagTypes: [WZDX_FEED_TAG],
  endpoints: (builder) => ({
    // The intersection-api returns the upstream WZDx FeatureCollection as-is (no wrapper object)
    getWzdxFeed: builder.query<WZDxWorkZoneFeed, void>({
      query: () => '',
      providesTags: [WZDX_FEED_TAG],
    }),
  }),
})

export const { useGetWzdxFeedQuery, useLazyGetWzdxFeedQuery } = wzdxFeedApi

import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'

export interface GeoMsgDataRequest {
  geometry: number[][]
  start: string
  end: string
  msg_type: string
}

export type GeoMsgFeature = GeoJSON.Feature<GeoJSON.Geometry, GeoJSON.GeoJsonProperties>

export const geoMsgApiSlice = createApi({
  reducerPath: 'geoMsgApi',
  baseQuery: fetchBaseQuery({
    baseUrl: EnvironmentVars.CVIZ_API_SERVER_URL,
    prepareHeaders: (headers, { getState }) => {
      const token = selectToken(getState() as RootState)

      headers.set('Accept', 'application/json')
      if (token) headers.set('Authorization', `Bearer ${token}`)

      return headers
    },
  }),
  endpoints: (builder) => ({
    getGeoMsgData: builder.mutation<GeoMsgFeature[], GeoMsgDataRequest>({
      query: (request) => ({
        url: '/rsu-geo-msg-data',
        method: 'POST',
        body: request,
      }),
    }),
  }),
})

export const { useGetGeoMsgDataMutation } = geoMsgApiSlice

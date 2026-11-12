import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'

export type RsuOnlineStatus = 'online' | 'offline' | 'unstable'

export type RsuOnlineStatusMap = {
  [ip: string]: {
    current_status: RsuOnlineStatus
  }
}

type RsuOnlineStatusResponse = {
  onlineStatusByIp: RsuOnlineStatusMap
}

export type RsuLastOnlineStatus = {
  ip: string
  last_online: string | null
}

const RSU_ONLINE_STATUS_TAG = 'RsuOnlineStatus' as const
const RSU_ONLINE_STATUS_LIST_ID = 'LIST' as const

export const rsuOnlineStatusApiSlice = createApi({
  reducerPath: 'rsuOnlineStatusApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${EnvironmentVars.CVIZ_API_SERVER_URL}/devices/rsus/online-status`,
    prepareHeaders: (headers, { getState }) => {
      const token = selectToken(getState() as RootState)
      if (token) headers.set('Authorization', `Bearer ${token}`)
      return headers
    },
  }),
  tagTypes: [RSU_ONLINE_STATUS_TAG],
  endpoints: (builder) => ({
    getRsuOnlineStatuses: builder.query<RsuOnlineStatusMap, string>({
      query: (organization) => ({
        url: '',
        headers: { Organization: organization },
      }),
      transformResponse: (response: RsuOnlineStatusResponse) => response.onlineStatusByIp,
      providesTags: (result) => [
        ...(result ? Object.keys(result).map((ip) => ({ type: RSU_ONLINE_STATUS_TAG, id: ip })) : []),
        { type: RSU_ONLINE_STATUS_TAG, id: RSU_ONLINE_STATUS_LIST_ID },
      ],
    }),
    getRsuLastOnline: builder.query<RsuLastOnlineStatus, string>({
      query: (ip) => ({
        url: `/${encodeURIComponent(ip)}`,
      }),
      providesTags: (result, error, ip) => [{ type: RSU_ONLINE_STATUS_TAG, id: ip }],
    }),
  }),
})

export const { useGetRsuOnlineStatusesQuery, useGetRsuLastOnlineQuery } = rsuOnlineStatusApiSlice

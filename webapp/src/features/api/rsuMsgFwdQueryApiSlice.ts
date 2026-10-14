import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'
import { RsuDsrcFwdConfigs, RsuMsgFwdConfigs, RsuRxTxMsgFwdConfigs } from '../../models/RsuApi'

export type RsuMsgFwdConfigsByIndex = RsuDsrcFwdConfigs | RsuRxTxMsgFwdConfigs

export const RSU_MSG_FWD_QUERY_TAG = 'RsuMsgFwdQuery' as const

export const rsuMsgFwdQueryApiSlice = createApi({
  reducerPath: 'rsuMsgFwdQueryApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${EnvironmentVars.CVIZ_API_SERVER_URL}/devices/rsus/msgfwd-query`,
    prepareHeaders: (headers, { getState }) => {
      const token = selectToken(getState() as RootState)
      if (token) headers.set('Authorization', `Bearer ${token}`)
      return headers
    },
  }),
  tagTypes: [RSU_MSG_FWD_QUERY_TAG],
  endpoints: (builder) => ({
    getRsuMsgFwdConfigs: builder.query<RsuMsgFwdConfigsByIndex, { organization: string; rsuIp: string }>({
      query: ({ organization, rsuIp }) => ({
        url: '',
        params: { rsu_ip: rsuIp },
        headers: { Organization: organization },
      }),
      transformResponse: (response: RsuMsgFwdConfigs) => response?.RsuFwdSnmpwalk ?? {},
      providesTags: (result, error, { rsuIp }) => [{ type: RSU_MSG_FWD_QUERY_TAG, id: rsuIp }],
    }),
  }),
})

export const { useGetRsuMsgFwdConfigsQuery } = rsuMsgFwdQueryApiSlice

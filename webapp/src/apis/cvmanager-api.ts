// Need to use the React-specific entry point to import createApi
import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../EnvironmentVars'
import {
  ApiMsgRespWithCodes,
  IssScmsStatus,
  RsuCommandPostBody,
  RsuCounts,
  RsuInfo,
  RsuMapInfo,
  RsuMapInfoIpList,
  RsuMsgFwdConfigs,
  RsuOnlineStatusRespMultiple,
  RsuOnlineStatusRespSingle,
  SsmSrmData,
} from './rsu-api-types'
import { RootState } from '../store'
import { selectOrganizationName, selectToken } from '../generalSlices/userSlice'
import { WZDxWorkZoneFeed } from '../models/wzdx/WzdxWorkZoneFeed42'

const getQueryString = (query_params: Record<string, string>) => {
  // filter out undefined values from query params
  const filteredQueryParams: Record<string, string> = { ...query_params }
  Object.keys(filteredQueryParams).forEach((key) => query_params[key] === undefined && delete query_params[key])
  const queryString = new URLSearchParams(query_params).toString()
  return `${queryString ? `?${queryString}` : ''}`
}

const transformPostResponseWithCodes = (response: any, meta: any): ApiMsgRespWithCodes<any> => {
  return {
    body: response,
    status: meta?.response?.status,
    message: meta?.response?.statusText,
  }
}

// Define a service using a base URL and expected endpoints
export const cvmanagerApi = createApi({
  reducerPath: 'cvmanagerApi',
  baseQuery: fetchBaseQuery({
    baseUrl: EnvironmentVars.cvmanagerBaseEndpoint,
    prepareHeaders: (headers, { getState, endpoint }) => {
      const token = selectToken(getState() as RootState)
      const org = selectOrganizationName(getState() as RootState)

      // Specify endpoints that do not require a token or organization. These names must match the keys in the endpoints object below.
      const endpointsWithoutToken = ['postContactSupport']
      const endpointsWithoutOrg = ['getSsmSrmData', 'getWzdxData', 'postGeoMsgData', 'postContactSupport']

      if (token && !endpointsWithoutToken.includes(endpoint)) {
        headers.set('Authorization', `Bearer ${token}`)
      }
      if (org && !endpointsWithoutOrg.includes(endpoint)) {
        headers.set('Organization', org)
      }

      return headers
    },
  }),
  endpoints: (builder) => ({
    getRsuInfo: builder.query<RsuInfo, null>({
      query: () => {
        return `rsuinfo`
      },
    }),
    getRsuOnline: builder.query<RsuOnlineStatusRespMultiple | RsuOnlineStatusRespSingle, string | undefined>({
      query: (rsu_ip) => {
        return `rsu-online-status/${getQueryString({ rsu_ip })}`
      },
    }),
    getRsuCounts: builder.query<RsuCounts, { message?: string; start?: string; end?: string }>({
      query: (query_params) => {
        return `rsucounts/${getQueryString(query_params)}`
      },
    }),
    getRsuMsgFwdConfigs: builder.query<RsuMsgFwdConfigs, string | undefined>({
      query: (rsu_ip) => {
        return `rsu-msgfwd-query/${getQueryString({ rsu_ip })}`
      },
    }),
    // TODO: Commented because this is currently unused
    // getRsuAuth: builder.query<GetRsuUserAuthResp, undefined>({
    //   query: () => {
    //     return `user-auth`
    //   },
    // }),
    // TODO: Commented because this is currently unused
    // getRsuCommand: builder.query<GetRsuCommandResp, undefined>({
    //   query: () => {
    //     return `rsu-command`
    //   },
    // }),

    getRsuMapInfoIplist: builder.query<RsuMapInfoIpList, undefined>({
      query: () => {
        return `rsu-map-info/${getQueryString({ ip_list: 'True' })}`
      },
    }),
    getRsuMapInfoByIp: builder.query<RsuMapInfo, string | undefined>({
      query: (ip_address) => {
        return `rsu-map-info/${getQueryString({ ip_address })}`
      },
    }),
    getSsmSrmData: builder.query<SsmSrmData, undefined>({
      query: () => {
        return `rsu-ssm-srm-data`
      },
    }),
    getIssScmsStatus: builder.query<IssScmsStatus, undefined>({
      query: () => {
        return `iss-scms-status`
      },
    }),
    getWzdxData: builder.query<WZDxWorkZoneFeed, undefined>({
      query: () => {
        return `wzdx-feed`
      },
    }),
    postGeoMsgData: builder.query<
      ApiMsgRespWithCodes<any>,
      {
        msg_type: string
        start: any
        end: any
        geometry: number[][]
      }
    >({
      query: (body) => ({
        url: 'rsu-geo-msg-data',
        method: 'POST',
        body,
      }),
      transformResponse: transformPostResponseWithCodes,
    }),
    postRsuData: builder.query<ApiMsgRespWithCodes<any>, RsuCommandPostBody>({
      query: (body) => ({
        url: 'rsu-command',
        method: 'POST',
        body,
      }),
      transformResponse: transformPostResponseWithCodes,
    }),
    postRsuGeo: builder.query<ApiMsgRespWithCodes<any>, { geometry: number[][]; vendor: string }>({
      query: (body) => ({
        url: 'rsu-geo-query',
        method: 'POST',
        body,
      }),
      transformResponse: transformPostResponseWithCodes,
    }),
    postContactSupport: builder.query<ApiMsgRespWithCodes<any>, Object>({
      query: (body) => ({
        url: 'contact-support',
        method: 'POST',
        body,
      }),
      transformResponse: transformPostResponseWithCodes,
    }),
  }),
})

// Export hooks for usage in functional components, which are
// auto-generated based on the defined endpoints
export const { useGetRsuInfoQuery } = cvmanagerApi

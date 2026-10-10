import toast from 'react-hot-toast'
import EnvironmentVars from '../EnvironmentVars'
import { WZDxWorkZoneFeed } from '../models/wzdx/WzdxWorkZoneFeed42'
import apiHelper from './api-helper'
import { authApiHelper } from './intersections/api-helper-cviz'
import {
  ApiMsgRespWithCodes,
  GetRsuCommandResp,
  RsuCommandPostBody,
  RsuCounts,
  RsuInfo,
  RsuInfoList,
  RsuMsgFwdConfigs,
} from '../models/RsuApi'

class RsuApi {
  // External Methods
  getRsuInfo = async (
    token: string,
    org: string,
    url_ext = '',
    query_params: Record<string, string> = {}
  ): Promise<RsuInfoList> => {
    const response = await authApiHelper.invokeApi({
      path: `${EnvironmentVars.rsuInfoPath}${url_ext}`,
      queryParams: query_params,
      token,
      headers: { Organization: org },
      toastOnFailure: false,
      tag: 'rsu',
    })

    const rsuArray = Array.isArray(response) ? (response as RsuInfo[]) : []
    return { rsuList: rsuArray }
  }
  getRsuCounts = async (
    token: string,
    org: string,
    url_ext = '',
    query_params: Record<string, string> = {}
  ): Promise<RsuCounts> =>
    apiHelper._getData({
      url: EnvironmentVars.rsuCountsEndpoint + url_ext,
      token,
      query_params,
      additional_headers: { Organization: org },
      tag: 'rsu',
    })
  getCachedRsuMsgFwdConfigsFromDatabase = async (
    token: string,
    org: string,
    url_ext = '',
    query_params: Record<string, string> = {}
  ): Promise<RsuMsgFwdConfigs> =>
    apiHelper._getData({
      url: EnvironmentVars.rsuMsgFwdQueryEndpoint + url_ext,
      token,
      query_params,
      additional_headers: { Organization: org },
      tag: 'rsu',
    })
  getRsuMsgConfigsFromRsu = async (
    token: string,
    org: string,
    url_ext = '',
    query_params: Record<string, string> = {}
  ): Promise<RsuMsgFwdConfigs> =>
    apiHelper._getData({
      url: EnvironmentVars.rsuMsgFwdFetchEndpoint + url_ext,
      token,
      query_params,
      additional_headers: { Organization: org },
      tag: 'rsu',
    })
  getRsuCommand = async (
    token: string,
    org: string,
    url_ext = '',
    query_params: Record<string, string> = {}
  ): Promise<GetRsuCommandResp> =>
    apiHelper._getData({
      url: EnvironmentVars.rsuCommandEndpoint + url_ext,
      token,
      query_params,
      additional_headers: { Organization: org },
      tag: 'rsu',
    })

  // WZDx
  getWzdxData = async (token: string, url_ext = '', query_params = {}): Promise<WZDxWorkZoneFeed> =>
    apiHelper._getData({
      url: EnvironmentVars.wzdxEndpoint + url_ext,
      token,
      query_params,
      tag: 'wzdx',
    })

  // POST
  postGeoMsgData = async (token: string, body: string, url_ext = ''): Promise<ApiMsgRespWithCodes<any>> =>
    apiHelper._postData({ url: EnvironmentVars.geoMsgDataEndpoint + url_ext, body, token, tag: 'rsu' })

  // POST
  postRsuData = async (
    token: string,
    org: string,
    body: RsuCommandPostBody,
    url_ext = ''
  ): Promise<ApiMsgRespWithCodes<any>> => {
    return await apiHelper._postData({
      url: EnvironmentVars.rsuCommandEndpoint + url_ext,
      body: JSON.stringify(body),
      token,
      additional_headers: { Organization: org },
      tag: 'rsu',
    })
  }

  // POST
  postRsuGeo = async (
    token: string,
    org: string,
    body: { geometry: number[][]; vendor?: string },
    url_ext = ''
  ): Promise<ApiMsgRespWithCodes<string[]> | null> => {
    const response = await authApiHelper.invokeApi({
      path: `${EnvironmentVars.rsuGeoQueryPath}${url_ext}`,
      method: 'POST',
      token,
      headers: { Organization: org },
      body,
      tag: 'rsu',
      toastOnFailure: false,
      returnErrorBody: true,
      failureMessage: 'Failed to query RSUs by geometry',
    })

    if (!response) {
      return null
    }

    if (response.__isErrorResponse) {
      const message = response.body?.detail ?? `Failed to query RSUs by geometry (${response.status})`
      if (response.status !== 401 && response.status !== 403) {
        toast.error(message)
      }
      return {
        body: [],
        status: response.status,
        message,
      }
    }

    return {
      body: Array.isArray(response) ? response : [],
      status: 200,
      message: '',
    }
  }
}

const rsuApiObject = new RsuApi()

export default rsuApiObject

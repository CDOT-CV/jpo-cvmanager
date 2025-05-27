import EnvironmentVars from '../EnvironmentVars'
import { WZDxWorkZoneFeed } from '../models/wzdx/WzdxWorkZoneFeed42'
import { MooveAiFeature } from '../models/moove-ai/MooveAiData'
import {
  ApiMsgRespWithCodes,
  GetRsuCommandResp,
  GetRsuUserAuthResp,
  IssScmsStatus,
  RsuCommandPostBody,
  RsuCounts,
  RsuInfoList,
  RsuMsgFwdConfigs,
  RsuOnlineStatusRespMultiple,
  RsuOnlineStatusRespSingle,
  SsmSrmData,
} from '../models/RsuApi'
import { apiHelper } from './api-helper'

class RsuApi {
  // External Methods
  getRsuInfo = async (
    token: string,
    org: string,
    url_ext: string = '',
    query_params: Record<string, string> = {}
  ): Promise<RsuInfoList> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.rsuInfoEndpoint,
      token: token,
      queryParams: query_params,
      headers: { Organization: org },
      tag: 'rsu',
    })
  getRsuOnline = async (
    token: string,
    org: string,
    url_ext: string = '',
    query_params: Record<string, string> = {}
  ): Promise<RsuOnlineStatusRespMultiple | RsuOnlineStatusRespSingle> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.rsuOnlineEndpoint,
      token: token,
      queryParams: query_params,
      headers: { Organization: org },
      tag: 'rsu',
    })
  getRsuCounts = async (
    token: string,
    org: string,
    url_ext: string = '',
    query_params: Record<string, string> = {}
  ): Promise<RsuCounts> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.rsuCountsEndpoint,
      token: token,
      queryParams: query_params,
      headers: { Organization: org },
      tag: 'rsu',
    })
  getRsuMsgFwdConfigs = async (
    token: string,
    org: string,
    url_ext: string = '',
    query_params: Record<string, string> = {}
  ): Promise<RsuMsgFwdConfigs> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.rsuMsgFwdQueryEndpoint,
      token: token,
      queryParams: query_params,
      headers: { Organization: org },
      tag: 'rsu',
    })
  getRsuAuth = async (
    token: string,
    org: string,
    url_ext: string = '',
    query_params: Record<string, string> = {}
  ): Promise<GetRsuUserAuthResp> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.authEndpoint,
      token: token,
      queryParams: query_params,
      headers: { Organization: org },
      tag: 'rsu',
    })
  getRsuCommand = async (
    token: string,
    org: string,
    url_ext: string = '',
    query_params: Record<string, string> = {}
  ): Promise<GetRsuCommandResp> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.rsuCommandEndpoint,
      token: token,
      queryParams: query_params,
      headers: { Organization: org },
      tag: 'rsu',
    })
  getSsmSrmData = async (
    token: string,
    url_ext: string = '',
    query_params: Record<string, string> = {}
  ): Promise<SsmSrmData> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.ssmSrmEndpoint,
      token: token,
      queryParams: query_params,
      tag: 'rsu',
    })
  getIssScmsStatus = async (
    token: string,
    org: string,
    url_ext: string = '',
    query_params: Record<string, string> = {}
  ): Promise<IssScmsStatus> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.issScmsStatusEndpoint,
      token: token,
      queryParams: query_params,
      headers: { Organization: org },
      tag: 'rsu',
    })

  // WZDx
  getWzdxData = async (token: string, url_ext: string = '', query_params = {}): Promise<WZDxWorkZoneFeed> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.wzdxEndpoint,
      token: token,
      queryParams: query_params,
      tag: 'rsu',
    })

  // Moove AI
  postMooveAiData = async (
    token: string,
    body: Object,
    url_ext: string = ''
  ): Promise<ApiMsgRespWithCodes<MooveAiFeature[]>> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.mooveAiDataEndpoint,
      token: token,
      method: 'POST',
      body,
      tag: 'mooveai',
    })
  // POST
  postGeoMsgData = async (token: string, body: Object, url_ext: string = ''): Promise<ApiMsgRespWithCodes<any>> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.geoMsgDataEndpoint,
      token: token,
      method: 'POST',
      body: body,
      tag: 'rsu',
    })

  // POST
  postRsuData = async (
    token: string,
    org: string,
    body: RsuCommandPostBody,
    url_ext = ''
  ): Promise<ApiMsgRespWithCodes<any>> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.rsuCommandEndpoint,
      token: token,
      method: 'POST',
      body: JSON.stringify(body),
      headers: { Organization: org },
      tag: 'rsu',
    })

  // POST
  postRsuGeo = async (token: string, org: string, body: Object, url_ext: string): Promise<ApiMsgRespWithCodes<any>> =>
    apiHelper.invokeApi({
      path: url_ext,
      basePath: EnvironmentVars.rsuGeoQueryEndpoint,
      token: token,
      method: 'POST',
      body: body,
      headers: { Organization: org },
      tag: 'rsu',
    })

  // POST
  postContactSupport = async (json: Object): Promise<ApiMsgRespWithCodes<any>> =>
    apiHelper.invokeApi({
      path: '',
      basePath: EnvironmentVars.contactSupport,
      method: 'POST',
      body: JSON.stringify(json),
      tag: 'rsu',
    })

  // POST
  postRsuErrorSummary = async (json: Object): Promise<ApiMsgRespWithCodes<any>> =>
    apiHelper.invokeApi({
      path: '',
      basePath: EnvironmentVars.rsuErrorSummary,
      method: 'POST',
      body: JSON.stringify(json),
      tag: 'rsu',
    })
}

const rsuApiObject = new RsuApi()

export default rsuApiObject

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
      path: EnvironmentVars.rsuInfoEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
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
      path: EnvironmentVars.rsuOnlineEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
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
      path: EnvironmentVars.rsuCountsEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
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
      path: EnvironmentVars.rsuMsgFwdQueryEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
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
      path: EnvironmentVars.authEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
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
      path: EnvironmentVars.rsuCommandEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
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
      path: EnvironmentVars.ssmSrmEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
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
      path: EnvironmentVars.issScmsStatusEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
      token: token,
      queryParams: query_params,
      headers: { Organization: org },
      tag: 'rsu',
    })

  // WZDx
  getWzdxData = async (token: string, url_ext: string = '', query_params = {}): Promise<WZDxWorkZoneFeed> =>
    apiHelper.invokeApi({
      path: EnvironmentVars.wzdxEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
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
      path: EnvironmentVars.mooveAiDataEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
      wrapResponseWithCode: true,
      token: token,
      method: 'POST',
      body,
      tag: 'mooveai',
    })
  // POST
  postGeoMsgData = async (token: string, body: Object, url_ext: string = ''): Promise<ApiMsgRespWithCodes<any>> =>
    apiHelper.invokeApi({
      path: EnvironmentVars.geoMsgDataEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
      wrapResponseWithCode: true,
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
      path: EnvironmentVars.rsuCommandEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
      wrapResponseWithCode: true,
      token: token,
      method: 'POST',
      body: body,
      headers: { Organization: org },
      tag: 'rsu',
    })

  // POST
  postRsuGeo = async (token: string, org: string, body: Object, url_ext: string): Promise<ApiMsgRespWithCodes<any>> =>
    apiHelper.invokeApi({
      path: EnvironmentVars.rsuGeoQueryEndpoint + url_ext,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
      wrapResponseWithCode: true,
      token: token,
      method: 'POST',
      body: body,
      headers: { Organization: org },
      tag: 'rsu',
    })

  // POST
  postContactSupport = async (json: Object): Promise<ApiMsgRespWithCodes<any>> =>
    apiHelper.invokeApi({
      path: EnvironmentVars.contactSupport,
      basePath: EnvironmentVars.cvmanagerBaseEndpoint,
      wrapResponseWithCode: true,
      method: 'POST',
      body: json,
      tag: 'rsu',
    })

  // POST
  postRsuErrorSummary = async (json: Object): Promise<ApiMsgRespWithCodes<any>> =>
    apiHelper.invokeApi({
      path: EnvironmentVars.contactSupport,
      basePath: EnvironmentVars.rsuErrorSummary,
      wrapResponseWithCode: true,
      method: 'POST',
      body: json,
      tag: 'rsu',
    })
}

const rsuApiObject = new RsuApi()

export default rsuApiObject

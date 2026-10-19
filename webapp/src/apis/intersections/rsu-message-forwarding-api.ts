import { ApiMsgRespWithCodes, RsuDsrcFwdConfigs, RsuMsgFwdConfigs } from '../../models/RsuApi'
import {
  CreateMessageForwardingRequest,
  MessageForwardingRequest,
  MessageForwardingResponse,
  MessageForwardingConfiguration,
} from '../../models/RsuMessageForwarding'
import { authApiHelper } from './api-helper-cviz'

/** Reads and changes forwarding through the Intersection API; writes are never automatically retried. */
class RsuMessageForwardingApi {
  create(token: string, organization: string, body: CreateMessageForwardingRequest) {
    return this.request<MessageForwardingResponse>(token, organization, 'POST', body)
  }

  delete(token: string, organization: string, body: MessageForwardingRequest) {
    return this.request<MessageForwardingResponse>(token, organization, 'DELETE', body)
  }

  cached(token: string, organization: string, rsuIp: string) {
    return this.read(token, organization, rsuIp, false)
  }

  live(token: string, organization: string, rsuIp: string) {
    return this.read(token, organization, rsuIp, true)
  }

  private async read(
    token: string,
    organization: string,
    rsuIp: string,
    live: boolean
  ): Promise<ApiMsgRespWithCodes<RsuMsgFwdConfigs> | null> {
    const path = `/devices/rsus/message-forwarding/${encodeURIComponent(rsuIp)}${live ? '/live' : ''}`
    const response = await this.request<MessageForwardingConfiguration[]>(token, organization, 'GET', undefined, path)
    if (!response || response.status !== 200) return response ? { ...response, body: null } : null
    if (!Array.isArray(response.body)) throw new Error('Invalid forwarding configuration response')
    return { ...response, body: { RsuFwdSnmpwalk: formatConfigurations(response.body) } }
  }

  private async request<T>(
    token: string,
    organization: string,
    method: 'GET' | 'POST' | 'DELETE',
    body?: MessageForwardingRequest,
    path = '/devices/rsus/message-forwarding'
  ): Promise<ApiMsgRespWithCodes<T> | null> {
    const response = await authApiHelper.invokeApi({
      path,
      method,
      token,
      // The shared helper only adds this automatically for POST; DELETE also carries JSON.
      headers: { Organization: organization, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      body,
      tag: 'rsu',
      toastOnFailure: false,
      returnErrorBody: true,
      failureMessage: method === 'GET'
        ? 'Failed to fetch RSU message forwarding configuration'
        : 'Forwarding request did not complete; check RSU state before retrying',
    })
    if (!response) return null
    if (response.__isErrorResponse) {
      const detail = response.body?.detail ?? response.body?.message
      return {
        body: null,
        status: response.status,
        message: typeof detail === 'string' ? detail : `Request failed with status ${response.status}`,
      }
    }
    return { body: response, status: 200, message: '' }
  }
}

// Adapt the row-oriented API to the existing RX/TX cards and cached RSU 4.1 display.
// Dates already carry the API's configured display timezone, matching the previous UI.
function formatConfigurations(rows: MessageForwardingConfiguration[]): RsuMsgFwdConfigs['RsuFwdSnmpwalk'] {
  const receive: RsuDsrcFwdConfigs = {}
  const transmit: RsuDsrcFwdConfigs = {}
  const dsrc: RsuDsrcFwdConfigs = {}
  let hasNtcip = false
  for (const row of rows) {
    const entry = {
      'Message Type': row.messageType,
      IP: row.destinationIp,
      Port: row.destinationPort,
      'Start DateTime': row.startDatetime,
      'End DateTime': row.endDatetime,
      'Config Active': row.active ? 'Enabled' : 'Disabled',
      'Full WSMP': row.includeSecurity ? 'Enabled' : 'Disabled',
    }
    if (row.table === 'DSRC') dsrc[row.index] = entry
    else if (row.table === 'RECEIVE') {
      receive[row.index] = entry
      hasNtcip = true
    } else if (row.table === 'TRANSMIT') {
      transmit[row.index] = entry
      hasNtcip = true
    }
    else throw new Error('Unknown forwarding table in API response')
  }
  return hasNtcip ? { ...dsrc, rsuReceivedMsgTable: receive, rsuXmitMsgFwdingTable: transmit } : dsrc
}

export default new RsuMessageForwardingApi()

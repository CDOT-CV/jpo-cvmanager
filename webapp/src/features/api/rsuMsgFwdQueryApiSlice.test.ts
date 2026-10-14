import fetchMock from 'jest-fetch-mock'
import { beforeEach, describe, expect, it } from 'vitest'
import { setupStore } from '../../store'
import EnvironmentVars from '../../EnvironmentVars'
import { rsuMsgFwdQueryApiSlice } from './rsuMsgFwdQueryApiSlice'

const BASE_URL = `${EnvironmentVars.CVIZ_API_SERVER_URL}/devices/rsus/msgfwd-query`
const mockUserState = {
  user: {
    value: {
      authLoginData: { token: 'test-token' },
      organization: { organization: 'test-org', role: 'admin' },
    },
  },
}

function getRequest(callIndex = 0): Request {
  return fetchMock.mock.calls[callIndex][0] as Request
}

describe('rsuMsgFwdQueryApiSlice', () => {
  beforeEach(() => fetchMock.resetMocks())

  it('unwraps RsuFwdSnmpwalk and sends rsu_ip, bearer and organization headers', async () => {
    const store = setupStore(mockUserState)
    const config = {
      'Message Type': 'BSM',
      IP: '10.0.0.80',
      Port: 46800,
      'Start DateTime': '2024-04-01T00:00:00-06:00',
      'End DateTime': '2034-04-01T00:00:00-06:00',
      'Config Active': 'Enabled',
      'Full WSMP': 'Disabled',
    }
    fetchMock.mockResponseOnce(JSON.stringify({ RsuFwdSnmpwalk: { '1': config } }))

    const result = await store.dispatch(
      rsuMsgFwdQueryApiSlice.endpoints.getRsuMsgFwdConfigs.initiate({ organization: 'test-org', rsuIp: '10.0.0.1' })
    )

    expect(result.data).toEqual({ '1': config })
    const request = getRequest()
    expect(request.url).toBe(`${BASE_URL}?rsu_ip=10.0.0.1`)
    expect(request.headers.get('Authorization')).toBe('Bearer test-token')
    expect(request.headers.get('Organization')).toBe('test-org')
  })

  it('returns an empty config map when the RSU has no configurations in the organization', async () => {
    const store = setupStore(mockUserState)
    fetchMock.mockResponseOnce(JSON.stringify({ RsuFwdSnmpwalk: {} }))

    const result = await store.dispatch(
      rsuMsgFwdQueryApiSlice.endpoints.getRsuMsgFwdConfigs.initiate({ organization: 'test-org', rsuIp: '10.0.0.2' })
    )

    expect(result.data).toEqual({})
  })
})

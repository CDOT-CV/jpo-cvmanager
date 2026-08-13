import fetchMock from 'jest-fetch-mock'
import { beforeEach, describe, expect, it } from 'vitest'
import { setupStore } from '../../store'
import EnvironmentVars from '../../EnvironmentVars'
import { rsuOnlineStatusApiSlice } from './rsuOnlineStatusApiSlice'

const BASE_URL = `${EnvironmentVars.CVIZ_API_SERVER_URL}/devices/rsus/online-status`
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

describe('rsuOnlineStatusApiSlice', () => {
  beforeEach(() => fetchMock.resetMocks())

  it('unwraps onlineStatusByIp and sends bearer and organization headers', async () => {
    const store = setupStore(mockUserState)
    fetchMock.mockResponseOnce(JSON.stringify({ onlineStatusByIp: { '10.0.0.1': { current_status: 'online' } } }))

    const result = await store.dispatch(rsuOnlineStatusApiSlice.endpoints.getRsuOnlineStatuses.initiate('test-org'))

    expect(result.data).toEqual({ '10.0.0.1': { current_status: 'online' } })
    const request = getRequest()
    expect(request.url).toBe(BASE_URL)
    expect(request.headers.get('Authorization')).toBe('Bearer test-token')
    expect(request.headers.get('Organization')).toBe('test-org')
  })

  it('gets last-online status by encoded IP and preserves a null timestamp', async () => {
    const store = setupStore(mockUserState)
    fetchMock.mockResponseOnce(JSON.stringify({ ip: '10.0.0.1', last_online: null }))

    const result = await store.dispatch(
      rsuOnlineStatusApiSlice.endpoints.getRsuLastOnline.initiate({ organization: 'test-org', ip: '10.0.0.1' })
    )

    expect(result.data).toEqual({ ip: '10.0.0.1', last_online: null })
    expect(getRequest().url).toBe(`${BASE_URL}/10.0.0.1`)
  })
})

import fetchMock from 'jest-fetch-mock'
import { beforeEach, describe, expect, it } from 'vitest'
import { setupStore } from '../../store'
import { rsuCountsApiSlice } from './rsuCountsApiSlice'

const BASE_URL = (process.env.VITE_CVIZ_API_SERVER_URL ?? '').replace(/\/$/, '')
const mockUserState = {
  user: {
    value: {
      authLoginData: { token: 'test-token' },
      organization: { name: 'test-org', role: 'admin' },
    },
  },
}

function getRequest(callIndex = 0): Request {
  return fetchMock.mock.calls[callIndex][0] as Request
}

describe('rsuCountsApiSlice', () => {
  beforeEach(() => {
    fetchMock.resetMocks()
    fetchMock.doMock()
  })

  it('sends Bearer token and Organization header to the org counts endpoint', async () => {
    const store = setupStore(mockUserState)
    fetchMock.mockResponseOnce(
      JSON.stringify([
        {
          message_type: 'MAP',
          rsu_ip: '10.0.0.11',
          ode_input_count: 100,
          ode_output_count: 95,
          road: 'I25',
        },
      ])
    )

    const startDate = new Date('2026-09-01T00:00:00.000Z')
    const endDate = new Date('2026-09-02T00:00:00.000Z')
    const result = await store.dispatch(
      rsuCountsApiSlice.endpoints.getRsuCounts.initiate({
        organization: 'test-org',
        startDate,
        endDate,
        message: 'MAP',
      })
    )

    expect(result.data).toEqual([
      {
        message_type: 'MAP',
        rsu_ip: '10.0.0.11',
        ode_input_count: 100,
        ode_output_count: 95,
        road: 'I25',
      },
    ])

    const request = getRequest()
    expect(request.method).toBe('GET')
    expect(request.url).toBe(
      `${BASE_URL}/data/counts/rsus/organizations/test-org?message=MAP&start_time_utc_millis=${startDate.getTime()}&end_time_utc_millis=${endDate.getTime()}`
    )
    expect(request.headers.get('Authorization')).toBe('Bearer test-token')
    expect(request.headers.get('Organization')).toBe('test-org')
  })

  it('does not set Authorization header when token is absent', async () => {
    const store = setupStore({
      user: {
        value: {
          authLoginData: { token: undefined },
          organization: { name: 'test-org' },
        },
      },
    })
    fetchMock.mockResponseOnce(JSON.stringify([]))

    await store.dispatch(
      rsuCountsApiSlice.endpoints.getRsuCounts.initiate({
        organization: 'test-org',
        startDate: new Date('2026-09-01T00:00:00.000Z'),
        endDate: new Date('2026-09-02T00:00:00.000Z'),
        message: 'MAP',
      })
    )

    const request = getRequest()
    expect(request.headers.has('Authorization')).toBe(false)
  })

  it('fetches all requested message types in one per-RSU request', async () => {
    const store = setupStore(mockUserState)
    fetchMock.mockResponseOnce(
      JSON.stringify([
        {
          message_type: 'BSM',
          rsu_ip: '10.0.0.16',
          ode_input_count: 10,
          ode_output_count: 9,
          road: 'I70',
        },
        {
          message_type: 'MAP',
          rsu_ip: '10.0.0.16',
          ode_input_count: 5,
          ode_output_count: 4,
          road: 'I70',
        },
      ])
    )

    const startDate = new Date('2026-09-01T00:00:00.000Z')
    const endDate = new Date('2026-09-02T00:00:00.000Z')
    const result = await store.dispatch(
      rsuCountsApiSlice.endpoints.getRsuCountsByIp.initiate({
        rsuIp: '10.0.0.16',
        startDate,
        endDate,
        messages: ['BSM', 'MAP'],
      })
    )

    expect(result.data).toEqual([
      {
        message_type: 'BSM',
        rsu_ip: '10.0.0.16',
        ode_input_count: 10,
        ode_output_count: 9,
        road: 'I70',
      },
      {
        message_type: 'MAP',
        rsu_ip: '10.0.0.16',
        ode_input_count: 5,
        ode_output_count: 4,
        road: 'I70',
      },
    ])

    expect(fetchMock.mock.calls).toHaveLength(1)
    expect(getRequest().url).toBe(
      `${BASE_URL}/data/counts/rsus/10.0.0.16?message=BSM%2CMAP&start_time_utc_millis=${startDate.getTime()}&end_time_utc_millis=${endDate.getTime()}`
    )
    expect(getRequest().headers.get('Authorization')).toBe('Bearer test-token')
  })
})

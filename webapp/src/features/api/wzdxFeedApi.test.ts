import fetchMock from 'jest-fetch-mock'
import { beforeEach, describe, expect, it } from 'vitest'
import { setupStore } from '../../store'
import EnvironmentVars from '../../EnvironmentVars'
import { wzdxFeedApi } from './wzdxFeedApi'

const BASE_URL = `${EnvironmentVars.CVIZ_API_SERVER_URL}/wzdx-feed`

const mockUserState = {
  user: {
    value: {
      authLoginData: { token: 'test-token' },
      organization: { organization: 'test-org', role: 'admin' },
    },
  },
}

const mockFeed = {
  type: 'FeatureCollection',
  features: [
    {
      id: 'event-1',
      type: 'Feature',
      properties: {
        core_details: {
          event_type: 'work-zone',
          road_names: ['I-25'],
          direction: 'northbound',
          description: 'Lane closure',
        },
        vehicle_impact: 'some-lanes-closed',
        start_date: '2026-10-01T00:00:00Z',
        end_date: '2026-10-31T00:00:00Z',
      },
      geometry: {
        type: 'LineString',
        coordinates: [
          [-104.9903, 39.7392],
          [-104.9908, 39.7398],
        ],
      },
    },
  ],
}

function getRequest(callIndex = 0): Request {
  return fetchMock.mock.calls[callIndex][0] as Request
}

describe('wzdxFeedApi', () => {
  beforeEach(() => {
    fetchMock.resetMocks()
  })

  describe('getWzdxFeed', () => {
    it('sends GET request to /wzdx-feed with auth headers', async () => {
      const store = setupStore(mockUserState)
      fetchMock.mockResponseOnce(JSON.stringify(mockFeed))

      await store.dispatch(wzdxFeedApi.endpoints.getWzdxFeed.initiate())

      expect(fetchMock).toHaveBeenCalledTimes(1)
      const request = getRequest()
      expect(request.url).toBe(BASE_URL)
      expect(request.method).toBe('GET')
      expect(request.headers.get('Authorization')).toBe('Bearer test-token')
      expect(request.headers.get('Accept')).toBe('application/json')
      expect(request.headers.get('Content-Type')).toBe('application/json')
    })

    it('returns the FeatureCollection unwrapped', async () => {
      const store = setupStore(mockUserState)
      fetchMock.mockResponseOnce(JSON.stringify(mockFeed))

      const result = await store.dispatch(wzdxFeedApi.endpoints.getWzdxFeed.initiate())

      expect(result.data).toEqual(mockFeed)
      expect(result.data?.features).toHaveLength(1)
    })

    it('returns an error without throwing when the upstream feed fails (502)', async () => {
      const store = setupStore(mockUserState)
      fetchMock.mockResponseOnce(JSON.stringify({ message: 'Failed to retrieve WZDX API response' }), {
        status: 502,
      })

      const result = await store.dispatch(wzdxFeedApi.endpoints.getWzdxFeed.initiate())

      expect(result.data).toBeUndefined()
      expect(result.error).toHaveProperty('status', 502)
    })

    it('returns an error without throwing on 401 response', async () => {
      const store = setupStore(mockUserState)
      fetchMock.mockResponseOnce(JSON.stringify({ message: 'Unauthorized' }), { status: 401 })

      const result = await store.dispatch(wzdxFeedApi.endpoints.getWzdxFeed.initiate())

      expect(result.error).toHaveProperty('status', 401)
    })

    it('does not set Authorization header when token is absent', async () => {
      const store = setupStore({
        user: {
          value: {
            authLoginData: { token: undefined },
            organization: { organization: 'test-org' },
          },
        },
      })
      fetchMock.mockResponseOnce(JSON.stringify(mockFeed))

      await store.dispatch(wzdxFeedApi.endpoints.getWzdxFeed.initiate())

      expect(getRequest().headers.has('Authorization')).toBe(false)
    })
  })
})

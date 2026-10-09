import fetchMock from 'jest-fetch-mock'
import { beforeEach, describe, expect, it } from 'vitest'
import EnvironmentVars from '../../EnvironmentVars'
import { setupStore } from '../../store'
import { geoMsgApiSlice, GeoMsgDataRequest } from './geoMsgApiSlice'

const requestBody: GeoMsgDataRequest = {
  msg_type: 'BSM',
  start: '2024-04-01T06:00:00Z',
  end: '2024-04-01T07:00:00Z',
  geometry: [
    [-105, 39],
    [-104, 39],
    [-104, 40],
    [-105, 39],
  ],
}

const mockUserState = {
  user: {
    value: {
      authLoginData: { token: 'test-token' },
    },
  },
}

describe('geoMsgApiSlice', () => {
  beforeEach(() => fetchMock.resetMocks())

  it('posts the geometry query to the Java API with bearer authentication and returns the bare feature array', async () => {
    const features = [{ type: 'Feature', properties: { id: 'vehicle-1' }, geometry: null }]
    const store = setupStore(mockUserState)
    fetchMock.mockResponseOnce(JSON.stringify(features))

    const result = await store.dispatch(geoMsgApiSlice.endpoints.getGeoMsgData.initiate(requestBody))

    expect('data' in result ? result.data : undefined).toEqual(features)
    const request = fetchMock.mock.calls[0][0] as Request
    expect(request.url).toBe(`${EnvironmentVars.CVIZ_API_SERVER_URL}/rsu-geo-msg-data`)
    expect(request.method).toBe('POST')
    expect(request.headers.get('Authorization')).toBe('Bearer test-token')
    expect(request.headers.get('Content-Type')).toContain('application/json')
    expect(await request.json()).toEqual(requestBody)
  })
})

import RsuApi from './rsu-api'
import EnvironmentVars from '../EnvironmentVars'
import { combineUrlPaths } from './intersections/api-helper-cviz'

beforeEach(() => {
  fetchMock.mockClear()
  fetchMock.doMock()
  EnvironmentVars.rsuCountsEndpoint = 'VITE_ENV/rsucounts'
  EnvironmentVars.rsuCommandEndpoint = 'VITE_ENV/rsu-command'
  EnvironmentVars.wzdxEndpoint = 'VITE_ENV/wzdx-feed'
  EnvironmentVars.geoMsgDataEndpoint = 'VITE_ENV/rsu-geo-data'
  EnvironmentVars.adminAddOrg = 'VITE_ENV/admin-new-org'
  EnvironmentVars.adminOrg = 'VITE_ENV/admin-org'
})

it('Test apiHelper mock', async () => {
  const expectedResponse = [{ id: 1, type: 'Feature' }]
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  const actualResponse = await RsuApi.getRsuInfo('testToken', 'testOrg')
  expect(actualResponse).toEqual({ rsuList: expectedResponse })

  expect(fetchMock.mock.calls[0][0]).toBe(
    combineUrlPaths(EnvironmentVars.CVIZ_API_SERVER_URL!, EnvironmentVars.rsuInfoPath)
  )
  expect(fetchMock.mock.calls[0][1].method).toBe('GET')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({
    Authorization: 'Bearer testToken',
    Organization: 'testOrg',
  })
})

it('Test getRsuInfo', async () => {
  const expectedResponse = [{ id: 2, type: 'Feature' }]
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  const actualResponse = await RsuApi.getRsuInfo('testToken', 'testOrg')
  expect(actualResponse).toEqual({ rsuList: expectedResponse })

  expect(fetchMock.mock.calls[0][0]).toBe(
    combineUrlPaths(EnvironmentVars.CVIZ_API_SERVER_URL!, EnvironmentVars.rsuInfoPath)
  )
  expect(fetchMock.mock.calls[0][1].method).toBe('GET')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({
    Authorization: 'Bearer testToken',
    Organization: 'testOrg',
  })
})

it('Test getRsuInfo With Params', async () => {
  // Set url_ext and query_params
  const url_ext = 'url_ext'
  const query_params = { query_param: 'test' }

  const expectedResponse = [{ id: 3, type: 'Feature' }]
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  const actualResponse = await RsuApi.getRsuInfo('testToken', 'testOrg', url_ext, query_params)
  expect(actualResponse).toEqual({ rsuList: expectedResponse })

  expect(fetchMock.mock.calls[0][0]).toBe(
    `${combineUrlPaths(EnvironmentVars.CVIZ_API_SERVER_URL!, EnvironmentVars.rsuInfoPath + url_ext)}?query_param=test`
  )
  expect(fetchMock.mock.calls[0][1].method).toBe('GET')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({
    Authorization: 'Bearer testToken',
    Organization: 'testOrg',
  })
})

it('Test getRsuCounts', async () => {
  const expectedResponse = { data: 'Test JSON' }
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  const actualResponse = await RsuApi.getRsuCounts('testToken', 'testOrg')
  expect(actualResponse).toEqual(expectedResponse)

  expect(fetchMock.mock.calls[0][0]).toBe(EnvironmentVars.rsuCountsEndpoint)
  expect(fetchMock.mock.calls[0][1].method).toBe('GET')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({ Authorization: 'testToken', Organization: 'testOrg' })
})

it('Test getRsuCounts With Params', async () => {
  // Set url_ext and query_params
  const url_ext = 'url_ext'
  const query_params = { query_param: 'test' }

  const expectedResponse = { data: 'Test JSON' }
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  const actualResponse = await RsuApi.getRsuCounts('testToken', 'testOrg', url_ext, query_params)
  expect(actualResponse).toEqual(expectedResponse)

  expect(fetchMock.mock.calls[0][0]).toBe(EnvironmentVars.rsuCountsEndpoint + url_ext + '?query_param=test')
  expect(fetchMock.mock.calls[0][1].method).toBe('GET')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({ Authorization: 'testToken', Organization: 'testOrg' })
})

it('Test getRsuCommand', async () => {
  const expectedResponse = { data: 'Test JSON' }
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  const actualResponse = await RsuApi.getRsuCommand('testToken', 'testOrg')
  expect(actualResponse).toEqual(expectedResponse)

  expect(fetchMock.mock.calls[0][0]).toBe(EnvironmentVars.rsuCommandEndpoint)
  expect(fetchMock.mock.calls[0][1].method).toBe('GET')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({ Authorization: 'testToken', Organization: 'testOrg' })
})

it('Test getRsuCommand With Params', async () => {
  // Set url_ext and query_params
  const url_ext = 'url_ext'
  const query_params = { query_param: 'test' }

  const expectedResponse = { data: 'Test JSON' }
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  const actualResponse = await RsuApi.getRsuCommand('testToken', 'testOrg', url_ext, query_params)
  expect(actualResponse).toEqual(expectedResponse)

  expect(fetchMock.mock.calls[0][0]).toBe(EnvironmentVars.rsuCommandEndpoint + url_ext + '?query_param=test')
  expect(fetchMock.mock.calls[0][1].method).toBe('GET')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({ Authorization: 'testToken', Organization: 'testOrg' })
})

it('Test getWzdxData', async () => {
  const expectedResponse = { data: 'Test JSON' }
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  const actualResponse = await RsuApi.getWzdxData('testToken')
  expect(actualResponse).toEqual(expectedResponse)

  expect(fetchMock.mock.calls[0][0]).toBe(EnvironmentVars.wzdxEndpoint)
  expect(fetchMock.mock.calls[0][1].method).toBe('GET')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({ Authorization: 'testToken' })
})

it('Test getWzdxData With Params', async () => {
  // Set url_ext and query_params
  const url_ext = 'url_ext'
  const query_params = { query_param: 'test' }

  const expectedResponse = { data: 'Test JSON' }
  fetchMock.mockResponseOnce(JSON.stringify(expectedResponse))
  const actualResponse = await RsuApi.getWzdxData('testToken', url_ext, query_params)
  expect(actualResponse).toEqual(expectedResponse)

  expect(fetchMock.mock.calls[0][0]).toBe(EnvironmentVars.wzdxEndpoint + url_ext + '?query_param=test')
  expect(fetchMock.mock.calls[0][1].method).toBe('GET')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({ Authorization: 'testToken' })
})

it('Test postGeoMsgData', async () => {
  const body = {
    data: 'Test JSON',
  } as any
  fetchMock.mockResponseOnce(JSON.stringify(body))
  const actualResponse = await RsuApi.postGeoMsgData('testToken', body)
  expect(actualResponse).toEqual({
    body: body,
    message: undefined,
    status: 200,
  })

  expect(fetchMock.mock.calls[0][0]).toBe(EnvironmentVars.geoMsgDataEndpoint)
  expect(fetchMock.mock.calls[0][1].method).toBe('POST')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({
    Authorization: 'testToken',
    'Content-Type': 'application/json',
  })
})

it('Test postGeoMsgData With Params', async () => {
  // Set url_ext
  const url_ext = 'url_ext'
  const body = {
    data: 'Test JSON',
  } as any

  fetchMock.mockResponseOnce(JSON.stringify(body))
  const actualResponse = await RsuApi.postGeoMsgData('testToken', body, url_ext)
  expect(actualResponse).toEqual({
    body: body,
    message: undefined,
    status: 200,
  })

  expect(fetchMock.mock.calls[0][0]).toBe(EnvironmentVars.geoMsgDataEndpoint + url_ext)
  expect(fetchMock.mock.calls[0][1].method).toBe('POST')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({
    Authorization: 'testToken',
    'Content-Type': 'application/json',
  })
})

it('Test postRsuGeo', async () => {
  const body = {
    geometry: [
      [-105.1, 39.7],
      [-105.2, 39.6],
      [-105.0, 39.6],
      [-105.1, 39.7],
    ],
    vendor: 'Select Vendor',
  }
  const ips = ['10.11.81.12']
  fetchMock.mockResponseOnce(JSON.stringify(ips))

  const actualResponse = await RsuApi.postRsuGeo('testToken', 'testOrg', body)

  expect(actualResponse).toEqual({
    body: ips,
    message: '',
    status: 200,
  })
  expect(fetchMock.mock.calls[0][0]).toBe(
    combineUrlPaths(EnvironmentVars.CVIZ_API_SERVER_URL!, EnvironmentVars.rsuGeoQueryPath)
  )
  expect(fetchMock.mock.calls[0][1].method).toBe('POST')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({
    Authorization: 'Bearer testToken',
    'Content-Type': 'application/json',
    Organization: 'testOrg',
  })
  expect(JSON.parse(String(fetchMock.mock.calls[0][1].body))).toEqual(body)
})

it('Test postRsuData', async () => {
  const body = {
    data: 'Test JSON',
  } as any

  fetchMock.mockResponseOnce(JSON.stringify(body))
  const actualResponse = await RsuApi.postRsuData('testToken', 'testOrg', body)
  expect(actualResponse).toEqual({
    body: body,
    message: undefined,
    status: 200,
  })

  expect(fetchMock.mock.calls[0][0]).toBe(EnvironmentVars.rsuCommandEndpoint)
  expect(fetchMock.mock.calls[0][1].method).toBe('POST')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({
    Authorization: 'testToken',
    'Content-Type': 'application/json',
    Organization: 'testOrg',
  })
})

it('Test postRsuData With Params', async () => {
  // Set url_ext
  const url_ext = 'url_ext'
  const body = {
    data: 'Test JSON',
  } as any

  fetchMock.mockResponseOnce(JSON.stringify(body))
  const actualResponse = await RsuApi.postRsuData('testToken', 'testOrg', body, url_ext)
  expect(actualResponse).toEqual({
    body: body,
    message: undefined,
    status: 200,
  })

  expect(fetchMock.mock.calls[0][0]).toBe(EnvironmentVars.rsuCommandEndpoint + url_ext)
  expect(fetchMock.mock.calls[0][1].method).toBe('POST')
  expect(fetchMock.mock.calls[0][1].headers).toStrictEqual({
    Authorization: 'testToken',
    'Content-Type': 'application/json',
    Organization: 'testOrg',
  })
})

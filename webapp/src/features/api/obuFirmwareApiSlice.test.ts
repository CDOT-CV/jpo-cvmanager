import fetchMock from 'jest-fetch-mock'
import EnvironmentVars from '../../EnvironmentVars'
import { setupStore } from '../../store'
import { obuFirmwareApiSlice } from './obuFirmwareApiSlice'

const BASE_URL = `${EnvironmentVars.CVIZ_API_SERVER_URL}/admin`
const mockUserState = {
  user: {
    value: {
      authLoginData: { token: 'test-token' },
      organization: { organization: 'test-org', role: 'admin' },
    },
  },
}

const lastRequest = () => fetchMock.mock.calls.at(-1)![0] as Request

describe('obuFirmwareApiSlice', () => {
  beforeEach(() => fetchMock.resetMocks())

  it('lists OBU firmware with its filters and the bearer token', async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ provider: 'gcp', storage_available: true, objects: [], total_elements: 0 }))
    const store = setupStore(mockUserState)

    await store.dispatch(
      obuFirmwareApiSlice.endpoints.listObuFirmwareObjects.initiate({
        page: 1,
        size: 25,
        manufacturer: 'Commsignia',
        published: false,
        sort: 'file_name,desc',
      })
    )

    const url = new URL(lastRequest().url)
    expect(`${url.origin}${url.pathname}`).toBe(`${BASE_URL}/obu-firmware/objects`)
    expect(Object.fromEntries(url.searchParams)).toEqual({
      page: '1',
      size: '25',
      manufacturer: 'Commsignia',
      published: 'false',
      sort: 'file_name,desc',
    })
    expect(lastRequest().headers.get('Authorization')).toBe('Bearer test-token')
  })

  it('checks a file name before uploading it', async () => {
    const store = setupStore(mockUserState)
    fetchMock.mockResponseOnce(JSON.stringify({ file_name: 'ob4.tar.sig', device_type: 'OBU' }))

    const result = await store.dispatch(
      obuFirmwareApiSlice.endpoints.checkObuFirmwareFileName.initiate({
        manufacturer_name: 'Vendor',
        file_name: 'ob4.tar.sig',
      })
    )

    const url = new URL(lastRequest().url)
    expect(`${url.origin}${url.pathname}`).toBe(`${BASE_URL}/obu-firmware/file-name-check`)
    expect(Object.fromEntries(url.searchParams)).toEqual({ manufacturer_name: 'Vendor', file_name: 'ob4.tar.sig' })
    expect(lastRequest().method).toBe('GET')
    expect(result.data?.device_type).toBe('OBU')
  })

  it('publishes and unpublishes firmware by catalog id', async () => {
    const store = setupStore(mockUserState)
    const replaced = [{ obu_firmware_id: 3, file_name: 'ob4-generic-rw-y20.47.0-b220000.tar.sig' }]
    fetchMock.mockResponseOnce(JSON.stringify({ obu_firmware_id: 7, published: true, replaced }))

    const result = await store.dispatch(
      obuFirmwareApiSlice.endpoints.setObuFirmwarePublished.initiate({ obuFirmwareId: 7, published: true })
    )

    expect('data' in result && result.data.replaced).toEqual(replaced)

    expect(lastRequest().url).toBe(`${BASE_URL}/obu-firmware/images/7/publication`)
    expect(lastRequest().method).toBe('PUT')
    expect(await lastRequest().json()).toEqual({ published: true })
  })

  it('deletes stored files with their version and cleans up records without one', async () => {
    const store = setupStore(mockUserState)
    fetchMock.mockResponse('', { status: 204 })

    await store.dispatch(
      obuFirmwareApiSlice.endpoints.deleteObuFirmwareObject.initiate({ object_id: 'a/b', provider_object_version: '17' })
    )
    expect(lastRequest().url).toBe(`${BASE_URL}/obu-firmware/objects/a%2Fb?provider_object_version=17`)
    expect(lastRequest().method).toBe('DELETE')

    await store.dispatch(
      obuFirmwareApiSlice.endpoints.deleteObuFirmwareObject.initiate({ object_id: 'a/b', provider_object_version: null })
    )
    expect(lastRequest().url).toBe(`${BASE_URL}/obu-firmware/objects/a%2Fb/records`)
    expect(lastRequest().method).toBe('DELETE')
  })

  it('requests upload options, signed upload URLs, and completion', async () => {
    const store = setupStore(mockUserState)
    fetchMock.mockResponse(JSON.stringify({}))
    const upload = {
      manufacturer_name: 'Commsignia',
      file_name: 'ob4-generic-ro-y20.48.2.tar.sig',
      content_length: 9,
      content_type: 'application/octet-stream',
      checksum_algorithm: 'CRC32C',
      checksum: '4waSgw==',
    }

    await store.dispatch(obuFirmwareApiSlice.endpoints.getObuFirmwareUploadOptions.initiate())
    expect(lastRequest().url).toBe(`${BASE_URL}/obu-firmware/upload-options`)

    await store.dispatch(obuFirmwareApiSlice.endpoints.createObuFirmwareUploadUrl.initiate(upload))
    expect(lastRequest().url).toBe(`${BASE_URL}/obu-firmware/signed-upload-url`)
    expect(lastRequest().method).toBe('POST')
    expect(await lastRequest().json()).toEqual(upload)

    await store.dispatch(obuFirmwareApiSlice.endpoints.completeObuFirmwareUpload.initiate('upload-1'))
    expect(lastRequest().url).toBe(`${BASE_URL}/obu-firmware/uploads/upload-1/complete`)
    expect(lastRequest().method).toBe('POST')
  })

  it('lists OBU OTA requests with their filters', async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ items: [], total_elements: 0 }))
    const store = setupStore(mockUserState)

    await store.dispatch(
      obuFirmwareApiSlice.endpoints.listObuOtaRequests.initiate({ page: 0, size: 50, serial: 'OBU', status: 'error' })
    )

    const url = new URL(lastRequest().url)
    expect(`${url.origin}${url.pathname}`).toBe(`${BASE_URL}/obu-ota-requests`)
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: '0', size: '50', serial: 'OBU', status: 'error' })
  })
})

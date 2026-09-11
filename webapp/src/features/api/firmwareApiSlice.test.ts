import fetchMock from 'jest-fetch-mock'
import EnvironmentVars from '../../EnvironmentVars'
import { setupStore } from '../../store'
import { FirmwareUploadUrlRequest } from '../../models/Firmware'
import { firmwareApiSlice } from './firmwareApiSlice'

const BASE_URL = `${EnvironmentVars.CVIZ_API_SERVER_URL}/admin/firmware`
const mockUserState = {
  user: {
    value: {
      authLoginData: { token: 'test-token' },
      organization: { organization: 'test-org', role: 'admin' },
    },
  },
}

const requestBody: FirmwareUploadUrlRequest = {
  vendor_name: 'Commsignia',
  model_name: 'ITS-RS4-M',
  version: 'y20.97.0',
  file_name: 'firmware.tar.sig',
  content_length: 9,
  content_type: 'application/octet-stream',
  checksum_algorithm: 'CRC32C',
  checksum: '4waSgw==',
}

describe('firmwareApiSlice', () => {
  beforeEach(() => fetchMock.resetMocks())

  it('requests structured firmware upload options', async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ manufacturers: [] }))
    const store = setupStore(mockUserState)

    await store.dispatch(firmwareApiSlice.endpoints.getFirmwareUploadOptions.initiate())

    const request = fetchMock.mock.calls[0][0] as Request
    expect(request.url).toBe(`${BASE_URL}/upload-options`)
    expect(request.method).toBe('GET')
    expect(request.headers.get('Authorization')).toBe('Bearer test-token')
  })

  it('passes global search and manufacturer filtering with the requested page', async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ provider: 'gcp', objects: [], total_elements: 0 }))
    const store = setupStore(mockUserState)

    await store.dispatch(
      firmwareApiSlice.endpoints.listFirmwareObjects.initiate({
        manufacturer: 'Yunex',
        page: 1,
        size: 25,
        search: '2.2.19 tar',
      })
    )

    const request = fetchMock.mock.calls[0][0] as Request
    const url = new URL(request.url)
    expect(url.pathname).toBe(new URL(`${BASE_URL}/objects`).pathname)
    expect(Object.fromEntries(url.searchParams)).toEqual({
      manufacturer: 'Yunex', page: '1', size: '25', search: '2.2.19 tar',
    })
    expect(request.method).toBe('GET')
  })

  it('requests signed upload instructions with authentication', async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({
        upload_id: 'c8ddabda-d98c-4b2d-b719-c79f180f5801',
        upload_url: 'https://storage.googleapis.com/signed',
        method: 'PUT',
        object_name: 'Commsignia/ITS-RS4-M/y20.97.0/y20.97.0.tar.sig',
        expires_at: '2026-09-03T23:00:00Z',
        required_headers: { 'Content-Type': 'application/octet-stream' },
      })
    )
    const store = setupStore(mockUserState)

    const result = await store.dispatch(firmwareApiSlice.endpoints.createFirmwareUploadUrl.initiate(requestBody))

    expect('error' in result).toBe(false)
    const request = fetchMock.mock.calls[0][0] as Request
    expect(request.url).toBe(`${BASE_URL}/signed-upload-url`)
    expect(request.method).toBe('POST')
    expect(request.headers.get('Authorization')).toBe('Bearer test-token')
    await expect(request.json()).resolves.toEqual(requestBody)
  })

  it('requests completion for the tracked upload', async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({
        upload_id: 'c8ddabda-d98c-4b2d-b719-c79f180f5801',
        status: 'VERIFIED',
        object_name: 'firmware.tar.sig',
      })
    )
    const store = setupStore(mockUserState)

    await store.dispatch(
      firmwareApiSlice.endpoints.completeFirmwareUpload.initiate('c8ddabda-d98c-4b2d-b719-c79f180f5801')
    )

    const request = fetchMock.mock.calls[0][0] as Request
    expect(request.url).toBe(
      `${BASE_URL}/uploads/c8ddabda-d98c-4b2d-b719-c79f180f5801/complete`
    )
    expect(request.method).toBe('POST')
  })
})

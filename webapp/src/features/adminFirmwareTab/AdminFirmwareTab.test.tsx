import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import { Provider } from 'react-redux'
import fetchMock from 'jest-fetch-mock'
import { vi } from 'vitest'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import AdminFirmwareTab from './AdminFirmwareTab'
import { calculateFileChecksum, uploadFileToSignedUrl } from './firmwareUpload'

vi.mock('./firmwareUpload', () => ({
  calculateFileChecksum: vi.fn().mockResolvedValue('4waSgw=='),
  uploadFileToSignedUrl: vi.fn((_file, _instructions, onProgress) => {
    onProgress(50)
    onProgress(100)
    return Promise.resolve()
  }),
}))

const signedUploadResponse = {
  upload_id: 'c8ddabda-d98c-4b2d-b719-c79f180f5801',
  upload_url: 'https://storage.googleapis.com/signed',
  method: 'PUT',
  object_name: 'Commsignia/ITS-RS4-M/y20.97.0/firmware.tar.sig',
  expires_at: '2026-09-03T23:00:00Z',
  required_headers: {
    'Content-Type': 'application/octet-stream',
    'x-goog-hash': 'crc32c=4waSgw==',
  },
}

const renderTab = () => {
  const store = setupStore({
    user: {
      value: {
        authLoginData: { token: 'test-token' },
        organization: { organization: 'test-org', role: 'admin' },
      },
    },
  })
  return render(
    <ThemeProvider theme={testTheme}>
      <Provider store={store}>
        <AdminFirmwareTab />
      </Provider>
    </ThemeProvider>
  )
}

const fillForm = (container: HTMLElement) => {
  fireEvent.change(screen.getByLabelText(/Vendor Name/), { target: { value: 'Commsignia' } })
  fireEvent.change(screen.getByLabelText(/Model Name/), { target: { value: 'ITS-RS4-M' } })
  fireEvent.change(screen.getByLabelText(/Version/), { target: { value: 'y20.97.0' } })
  const file = new File(['123456789'], 'firmware.tar.sig', { type: 'application/octet-stream' })
  fireEvent.change(container.querySelector('input[type="file"]')!, { target: { files: [file] } })
  return file
}

describe('AdminFirmwareTab', () => {
  beforeEach(() => {
    fetchMock.resetMocks()
    vi.mocked(calculateFileChecksum).mockClear()
    vi.mocked(uploadFileToSignedUrl).mockClear()
  })

  it('creates an upload, transfers the file, and completes verification', async () => {
    fetchMock.mockResponseOnce(JSON.stringify(signedUploadResponse))
    fetchMock.mockResponseOnce(
      JSON.stringify({
        upload_id: signedUploadResponse.upload_id,
        status: 'VERIFIED',
        object_name: signedUploadResponse.object_name,
        content_length: 9,
        checksum_algorithm: 'CRC32C',
        checksum: '4waSgw==',
        provider_object_version: '1',
        verified_at: '2026-09-03T22:50:00Z',
      })
    )
    const { container } = renderTab()
    const file = fillForm(container)

    fireEvent.click(screen.getByRole('button', { name: 'Upload Firmware' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Firmware uploaded and verified successfully')
    expect(calculateFileChecksum).toHaveBeenCalledWith(file, 'CRC32C')
    expect(uploadFileToSignedUrl).toHaveBeenCalledWith(file, signedUploadResponse, expect.any(Function))
    expect(fetchMock).toHaveBeenCalledTimes(2)

    const signedUrlRequest = fetchMock.mock.calls[0][0] as Request
    await expect(signedUrlRequest.json()).resolves.toMatchObject({
      vendor_name: 'Commsignia',
      model_name: 'ITS-RS4-M',
      version: 'y20.97.0',
      file_name: 'firmware.tar.sig',
      content_length: 9,
      checksum_algorithm: 'CRC32C',
      checksum: '4waSgw==',
    })
  })

  it('shows API failures as red error text and does not upload the file', async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ message: 'Vendor/model pair was not found' }), { status: 404 })
    const { container } = renderTab()
    fillForm(container)

    fireEvent.click(screen.getByRole('button', { name: 'Upload Firmware' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Vendor/model pair was not found'))
    expect(uploadFileToSignedUrl).not.toHaveBeenCalled()
  })
})

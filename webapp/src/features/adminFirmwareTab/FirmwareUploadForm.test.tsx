import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import { Provider } from 'react-redux'
import fetchMock from 'jest-fetch-mock'
import { vi } from 'vitest'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import FirmwareUploadForm from './FirmwareUploadForm'
import { calculateFileChecksum, uploadFileToSignedUrl } from './firmwareUpload'
import toast from 'react-hot-toast'

vi.mock('./firmwareUpload', () => ({
  calculateFileChecksum: vi.fn().mockResolvedValue('4waSgw=='),
  uploadFileToSignedUrl: vi.fn((_file, _instructions, onProgress) => {
    onProgress(50)
    onProgress(100)
    return Promise.resolve()
  }),
}))
vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn() },
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

const uploadOptionsResponse = {
  manufacturers: [
    {
      manufacturer_id: 1,
      name: 'Commsignia',
      models: [
        { model_id: 10, name: 'ITS-RS4-M' },
        { model_id: 11, name: 'ITS-RS4-S' },
      ],
    },
    {
      manufacturer_id: 2,
      name: 'Kapsch',
      models: [{ model_id: 20, name: 'RIS-9260' }],
    },
  ],
}

const renderTab = () => {
  const onClose = vi.fn()
  const onSuccess = vi.fn()
  const store = setupStore({
    user: {
      value: {
        authLoginData: { token: 'test-token' },
        organization: { organization: 'test-org', role: 'admin' },
      },
    },
  })
  const rendered = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={store}>
        <FirmwareUploadForm open onClose={onClose} onSuccess={onSuccess} />
      </Provider>
    </ThemeProvider>
  )
  return { ...rendered, onClose, onSuccess }
}

const chooseSelectOption = async (label: string, option: string) => {
  fireEvent.mouseDown(await screen.findByRole('combobox', { name: label }))
  fireEvent.click(await screen.findByRole('option', { name: option }))
}

const fillForm = async () => {
  await chooseSelectOption('Manufacturer', 'Commsignia')
  await chooseSelectOption('Model', 'ITS-RS4-M')
  fireEvent.change(screen.getByLabelText(/Version/), { target: { value: 'y20.97.0' } })
  const file = new File(['123456789'], 'firmware.tar.sig', { type: 'application/octet-stream' })
  fireEvent.change(document.querySelector('input[type="file"]')!, { target: { files: [file] } })
  return file
}

describe('FirmwareUploadForm', () => {
  beforeEach(() => {
    fetchMock.resetMocks()
    vi.mocked(calculateFileChecksum).mockClear()
    vi.mocked(uploadFileToSignedUrl).mockClear()
  })

  it('creates an upload, transfers the file, and completes verification', async () => {
    fetchMock.mockResponseOnce(JSON.stringify(uploadOptionsResponse))
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
    const { onSuccess } = renderTab()
    const file = await fillForm()

    fireEvent.click(screen.getByRole('button', { name: 'Add Firmware' }))

    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1))
    expect(toast.success).toHaveBeenCalledWith('Firmware uploaded and verified successfully')
    expect(calculateFileChecksum).toHaveBeenCalledWith(file, 'CRC32C')
    expect(uploadFileToSignedUrl).toHaveBeenCalledWith(file, signedUploadResponse, expect.any(Function))
    expect(fetchMock).toHaveBeenCalledTimes(3)

    const signedUrlRequest = fetchMock.mock.calls[1][0] as Request
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
    fetchMock.mockResponseOnce(JSON.stringify(uploadOptionsResponse))
    fetchMock.mockResponseOnce(JSON.stringify({ message: 'Vendor/model pair was not found' }), { status: 404 })
    const { onSuccess } = renderTab()
    await fillForm()

    fireEvent.click(screen.getByRole('button', { name: 'Add Firmware' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Vendor/model pair was not found'))
    expect(uploadFileToSignedUrl).not.toHaveBeenCalled()
    expect(onSuccess).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('limits models to the selected manufacturer and uses the selected file name', async () => {
    fetchMock.mockResponseOnce(JSON.stringify(uploadOptionsResponse))
    renderTab()

    await chooseSelectOption('Manufacturer', 'Commsignia')
    await chooseSelectOption('Model', 'ITS-RS4-S')
    await chooseSelectOption('Manufacturer', 'Kapsch')

    expect(screen.getByRole('combobox', { name: 'Model' })).not.toHaveTextContent('ITS-RS4-S')
    expect(screen.queryByLabelText(/Stored File Name/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Checksum Algorithm/)).not.toBeInTheDocument()

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Model' }))
    expect(await screen.findByRole('option', { name: 'RIS-9260' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'ITS-RS4-S' })).not.toBeInTheDocument()
  })

  it('shows an error and disables submission when upload options cannot be loaded', async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ message: 'Unavailable' }), { status: 503 })
    renderTab()

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load firmware manufacturers and models')
    expect(screen.getByRole('button', { name: 'Add Firmware' })).toBeDisabled()
  })

  it('disables submission when no RSU firmware models are configured', async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ manufacturers: [] }))
    renderTab()

    expect(await screen.findByText('No RSU manufacturers with models are available.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add Firmware' })).toBeDisabled()
  })
})

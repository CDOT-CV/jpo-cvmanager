import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import { Provider } from 'react-redux'
import fetchMock from 'jest-fetch-mock'
import toast from 'react-hot-toast'
import { vi } from 'vitest'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import { calculateFileChecksum, uploadFileToSignedUrl } from '../adminFirmwareTab/firmwareUpload'
import ObuFirmwareUploadForm, { localNameError, OBU_FIRMWARE_CONFLICT_MESSAGE } from './ObuFirmwareUploadForm'

vi.mock('../adminFirmwareTab/firmwareUpload', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../adminFirmwareTab/firmwareUpload')>()
  return {
    ...actual,
    calculateFileChecksum: vi.fn().mockResolvedValue('ImIEBA=='),
    uploadFileToSignedUrl: vi.fn((_file, _instructions, onProgress) => {
      onProgress(100)
      return Promise.resolve()
    }),
  }
})
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn() } }))

const FILE_NAME = 'ob4-generic-ro-secureboot-y20.48.2-b228647.tar.sig'
const vendor = (id: number, name: string) => ({
  manufacturer_id: id,
  name,
  file_extension: '.tar.sig',
  file_name_pattern: '^ob4-[a-z0-9.-]+\\.tar\\.sig$',
  max_file_name_length: 128,
  example_file_name: FILE_NAME,
})
const ONE_VENDOR = { manufacturers: [vendor(1, 'Vendor')] }
const nameCheck = {
  manufacturer: 'Vendor',
  file_name: FILE_NAME,
  device_type: 'OBU',
  variant: 'ob4-generic',
  release_version: 'y20.48.2-b228647',
  writable_system_partition: false,
  secure_boot: true,
}
const signedUpload = {
  upload_id: 'c8ddabda-d98c-4b2d-b719-c79f180f5801',
  upload_url: 'https://storage.googleapis.com/signed',
  method: 'PUT',
  object_name: `ota/Vendor/${FILE_NAME}`,
  expires_at: '2026-10-15T12:15:00Z',
  required_headers: { 'x-goog-hash': 'crc32c=ImIEBA==' },
}
const verification = {
  upload_id: signedUpload.upload_id,
  status: 'VERIFIED',
  object_name: signedUpload.object_name,
  content_length: 9,
  checksum_algorithm: 'CRC32C',
  checksum: 'ImIEBA==',
  provider_object_version: '17',
  verified_at: '2026-10-15T12:05:00Z',
  obu_firmware_id: 42,
}

type Route = { status?: number; body: unknown }

// Answers each API path with a fixed response and records the requests
const routeApi = (routes: Record<string, Route>) => {
  fetchMock.mockResponse(async (request) => {
    const path = new URL(request.url).pathname.replace(/^.*\/admin\//, '')
    const key = Object.keys(routes).find((route) => path.startsWith(route))
    if (!key) return { status: 500, body: '{}' }
    return { status: routes[key].status ?? 200, body: JSON.stringify(routes[key].body) }
  })
}

const requests = (path: string) =>
  fetchMock.mock.calls.map(([request]) => request as Request).filter((request) => request.url.includes(path))

const renderForm = () => {
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
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={store}>
        <ObuFirmwareUploadForm open onClose={onClose} onSuccess={onSuccess} />
      </Provider>
    </ThemeProvider>
  )
  return { onClose, onSuccess }
}

const chooseFile = (name: string) => {
  const file = new File(['123456789'], name, { type: 'application/octet-stream' })
  fireEvent.change(document.querySelector('input[type="file"]')!, { target: { files: [file] } })
  return file
}

const addButton = () => screen.getByRole('button', { name: 'Add Firmware' })

describe('ObuFirmwareUploadForm', () => {
  beforeEach(() => {
    fetchMock.resetMocks()
    vi.mocked(calculateFileChecksum).mockClear()
    vi.mocked(uploadFileToSignedUrl).mockClear()
    vi.mocked(toast.success).mockClear()
  })

  it('previews what the API reads from the name, then uploads and verifies the file', async () => {
    routeApi({
      'obu-firmware/upload-options': { body: ONE_VENDOR },
      'obu-firmware/file-name-check': { body: nameCheck },
      'obu-firmware/signed-upload-url': { body: signedUpload },
      'obu-firmware/uploads/': { body: verification },
    })
    const { onSuccess } = renderForm()

    // A single manufacturer is selected for the admin
    expect(await screen.findByText(`.tar.sig file, for example ${FILE_NAME}`)).toBeInTheDocument()
    expect(addButton()).toBeDisabled()
    const file = chooseFile(FILE_NAME)

    const details = await screen.findByLabelText('Firmware details')
    expect(within(details).getByText('OBU')).toBeInTheDocument()
    expect(within(details).getByText('ob4-generic')).toBeInTheDocument()
    expect(within(details).getByText('y20.48.2-b228647')).toBeInTheDocument()
    expect(within(details).getByText('Read-only')).toBeInTheDocument()
    expect(within(details).getByText('Yes')).toBeInTheDocument()
    const check = new URL(requests('file-name-check')[0].url)
    expect(Object.fromEntries(check.searchParams)).toEqual({ manufacturer_name: 'Vendor', file_name: FILE_NAME })

    fireEvent.click(addButton())

    await waitFor(() => expect(onSuccess).toHaveBeenCalled())
    expect(await requests('signed-upload-url')[0].json()).toEqual({
      manufacturer_name: 'Vendor',
      file_name: FILE_NAME,
      content_length: 9,
      content_type: 'application/octet-stream',
      checksum_algorithm: 'CRC32C',
      checksum: 'ImIEBA==',
    })
    expect(uploadFileToSignedUrl).toHaveBeenCalledWith(
      file,
      expect.objectContaining({ upload_id: signedUpload.upload_id }),
      expect.any(Function),
      OBU_FIRMWARE_CONFLICT_MESSAGE
    )
    expect(requests(`uploads/${signedUpload.upload_id}/complete`)).toHaveLength(1)
    expect(toast.success).toHaveBeenCalledWith('OBU firmware uploaded and verified. Publish it to offer it to OBUs.')
  })

  it('rejects a name outside the naming convention without calling the API', async () => {
    routeApi({ 'obu-firmware/upload-options': { body: ONE_VENDOR } })
    renderForm()
    await screen.findByText(`.tar.sig file, for example ${FILE_NAME}`)

    chooseFile('firmware.tar.sig')

    expect(await screen.findByText(/firmware\.tar\.sig does not follow the Vendor OBU firmware naming/)).toBeInTheDocument()
    expect(addButton()).toBeDisabled()
    expect(requests('file-name-check')).toHaveLength(0)
    expect(requests('signed-upload-url')).toHaveLength(0)
  })

  it("shows the API's reason when it would not accept a name", async () => {
    const reason = 'Read-only OBU firmware must use secure boot. Supported OBU builds are listed here.'
    routeApi({
      'obu-firmware/upload-options': { body: ONE_VENDOR },
      'obu-firmware/file-name-check': { status: 400, body: { detail: reason } },
    })
    renderForm()
    await screen.findByText(`.tar.sig file, for example ${FILE_NAME}`)

    chooseFile('ob4-generic-ro-y20.48.2-b228647.tar.sig')

    expect(await screen.findByText(reason)).toBeInTheDocument()
    expect(addButton()).toBeDisabled()
    expect(requests('signed-upload-url')).toHaveLength(0)
  })

  it('reports an upload the API refuses', async () => {
    routeApi({
      'obu-firmware/upload-options': { body: ONE_VENDOR },
      'obu-firmware/file-name-check': { body: nameCheck },
      'obu-firmware/signed-upload-url': { status: 409, body: { detail: 'OBU firmware with this file name already exists' } },
    })
    const { onSuccess } = renderForm()
    await screen.findByText(`.tar.sig file, for example ${FILE_NAME}`)
    chooseFile(FILE_NAME)
    await screen.findByLabelText('Firmware details')

    fireEvent.click(addButton())

    expect(await screen.findByText('OBU firmware with this file name already exists')).toBeInTheDocument()
    expect(uploadFileToSignedUrl).not.toHaveBeenCalled()
    expect(onSuccess).not.toHaveBeenCalled()
    expect(addButton()).toBeEnabled()
  })

  it('asks for a manufacturer when several accept uploads, and clears the file when it changes', async () => {
    routeApi({
      'obu-firmware/upload-options': { body: { manufacturers: [vendor(1, 'Vendor'), vendor(2, 'Other')] } },
      'obu-firmware/file-name-check': { body: nameCheck },
    })
    renderForm()

    const chooseButton = await screen.findByText('Choose File')
    expect(chooseButton.closest('label')).toHaveClass('Mui-disabled')

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
    fireEvent.click(await screen.findByRole('option', { name: 'Vendor' }))
    chooseFile(FILE_NAME)
    await screen.findByLabelText('Firmware details')

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
    fireEvent.click(await screen.findByRole('option', { name: 'Other' }))
    expect(screen.getByText('No file selected')).toBeInTheDocument()
    expect(screen.queryByLabelText('Firmware details')).not.toBeInTheDocument()
    expect(addButton()).toBeDisabled()
  })

  it('explains when manufacturers cannot be loaded', async () => {
    routeApi({ 'obu-firmware/upload-options': { status: 500, body: {} } })
    renderForm()

    expect(await screen.findByText(/Unable to load OBU firmware manufacturers/)).toBeInTheDocument()
    expect(addButton()).toBeDisabled()
  })
})

describe('localNameError', () => {
  it('checks the length and the pattern from the upload options', () => {
    const option = { ...vendor(1, 'Vendor'), max_file_name_length: 60 }

    expect(localNameError(option, FILE_NAME)).toBeNull()
    expect(localNameError(option, `ob4-${'1'.repeat(60)}.tar.sig`)).toBe('File names can be at most 60 characters.')
    expect(localNameError(option, 'rs4-generic.tar.sig')).toContain(`for example ${FILE_NAME}`)
    // An invalid pattern leaves the decision to the API
    expect(localNameError({ ...option, file_name_pattern: '(' }, 'anything')).toBeNull()
  })
})

import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import toast from 'react-hot-toast'
import { vi } from 'vitest'
import { testTheme } from '../../styles'
import { ObuFirmwareObject, ObuFirmwarePublication, ObuFirmwareReplacement } from '../../models/ObuFirmware'
import {
  useDeleteObuFirmwareObjectMutation,
  useGetObuFirmwareUploadOptionsQuery,
  useLazyListObuFirmwareObjectsQuery,
  useSetObuFirmwarePublishedMutation,
} from '../api/obuFirmwareApiSlice'
import AdminObuFirmwareTab from './AdminObuFirmwareTab'

vi.mock('../api/obuFirmwareApiSlice', () => ({
  useGetObuFirmwareUploadOptionsQuery: vi.fn(),
  useLazyListObuFirmwareObjectsQuery: vi.fn(),
  useSetObuFirmwarePublishedMutation: vi.fn(),
  useDeleteObuFirmwareObjectMutation: vi.fn(),
}))
vi.mock('../../components/AdminTable', async () => ({
  default: (await import('./test-utils')).MockAdminTable,
}))
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn(), loading: vi.fn() } }))

const publication = (replaced: ObuFirmwareReplacement[]): ObuFirmwarePublication => ({
  obu_firmware_id: 1,
  published: true,
  published_at: '2026-10-15T12:02:00Z',
  published_by: 'admin',
  replaced,
})

const firmware = (overrides: Partial<ObuFirmwareObject>): ObuFirmwareObject => ({
  object_id: 'id',
  object_name: 'ota/Commsignia/file.tar.sig',
  storage_provider: 'gcp',
  provider_object_version: '17',
  manufacturer: 'Commsignia',
  file_name: 'file.tar.sig',
  device_type: 'OBU',
  variant: 'ob4-generic',
  release_version: 'y20.48.2',
  writable_system_partition: false,
  secure_boot: true,
  content_length: 1024,
  updated_at: '2026-10-15T12:00:00Z',
  registered_at: '2026-10-15T12:01:00Z',
  upload_id: null,
  upload_status: 'VERIFIED',
  obu_firmware_id: 1,
  published: false,
  published_at: null,
  published_by: null,
  verification_status: 'VERIFIED',
  ...overrides,
})

const objects = [
  firmware({ object_id: 'verified', file_name: 'verified.tar.sig' }),
  firmware({
    object_id: 'published',
    file_name: 'published.tar.sig',
    obu_firmware_id: 2,
    published: true,
    published_at: '2026-10-15T12:02:00Z',
    published_by: 'admin',
    verification_status: 'CHANGED',
  }),
  firmware({ object_id: 'untracked', file_name: 'untracked.tar.sig', obu_firmware_id: null, verification_status: 'UNTRACKED' }),
  firmware({ object_id: 'local', file_name: 'local.tar.sig', obu_firmware_id: 4, storage_provider: 'local', provider_object_version: null, verification_status: 'LOCAL' }),
  firmware({ object_id: 'missing', file_name: 'missing.tar.sig', obu_firmware_id: 5, provider_object_version: null, verification_status: 'MISSING' }),
  firmware({ object_id: 'unknown', file_name: 'unknown.tar.sig', obu_firmware_id: 6, provider_object_version: null, verification_status: 'UNKNOWN' }),
]

describe('OBU firmware tab', () => {
  const list = vi.fn()
  const setPublished = vi.fn()
  const deleteObject = vi.fn()
  let storageAvailable = true

  const renderTab = () =>
    render(
      <ThemeProvider theme={testTheme}>
        <AdminObuFirmwareTab />
      </ThemeProvider>
    )
  const row = (fileName: string) => screen.getByText(fileName).closest('tr') as HTMLElement

  beforeEach(() => {
    vi.resetAllMocks()
    storageAvailable = true
    list.mockImplementation(() => ({
      unwrap: () => Promise.resolve({ provider: 'gcp', storage_available: storageAvailable, objects, total_elements: 26 }),
    }))
    vi.mocked(useLazyListObuFirmwareObjectsQuery).mockReturnValue([list] as any)
    setPublished.mockImplementation(() => ({ unwrap: () => Promise.resolve(publication([])) }))
    vi.mocked(useSetObuFirmwarePublishedMutation).mockReturnValue([setPublished] as any)
    deleteObject.mockImplementation(() => ({ unwrap: () => Promise.resolve() }))
    vi.mocked(useDeleteObuFirmwareObjectMutation).mockReturnValue([deleteObject] as any)
    vi.mocked(useGetObuFirmwareUploadOptionsQuery).mockReturnValue({
      data: { manufacturers: [{ manufacturer_id: 1, name: 'Commsignia' }] },
      isFetching: false,
    } as any)
    vi.mocked(toast.loading).mockReturnValue('notification')
  })

  it('lists firmware details and explains that publish state is not applied yet', async () => {
    renderTab()

    expect(await screen.findByText('verified.tar.sig')).toBeInTheDocument()
    expect(within(row('verified.tar.sig')).getByText('ob4-generic · Read-only · Secure boot')).toBeInTheDocument()
    expect(within(row('verified.tar.sig')).getByText('1 KB')).toBeInTheDocument()
    expect(within(row('local.tar.sig')).getByText('Local file')).toBeInTheDocument()
    expect(within(row('published.tar.sig')).getByText('File changed')).toBeInTheDocument()
    expect(screen.getByText(/not yet applied by the OBU OTA server/)).toBeInTheDocument()
    expect(screen.queryByText(/Object storage could not be listed/)).not.toBeInTheDocument()
    expect(list).toHaveBeenCalledWith({
      page: 0,
      size: 25,
      search: '',
      manufacturer: undefined,
      published: undefined,
      sort: 'manufacturer,asc',
    })
  })

  it('warns when only catalog entries could be listed', async () => {
    storageAvailable = false
    renderTab()

    expect(await screen.findByText(/Object storage could not be listed/)).toBeInTheDocument()
  })

  it('allows publishing verified and local firmware, and unpublishing published firmware', async () => {
    renderTab()
    await screen.findByText('verified.tar.sig')

    expect(screen.getByRole('checkbox', { name: 'Publish verified.tar.sig' })).toBeEnabled()
    expect(screen.getByRole('checkbox', { name: 'Publish local.tar.sig' })).toBeEnabled()
    // Published firmware stays withdrawable even after its file changed
    expect(screen.getByRole('checkbox', { name: 'Unpublish published.tar.sig' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Unpublish published.tar.sig' })).toBeEnabled()
    for (const name of ['untracked.tar.sig', 'missing.tar.sig', 'unknown.tar.sig']) {
      expect(screen.getByRole('checkbox', { name: `Publish ${name}` })).toBeDisabled()
    }
  })

  it('confirms publication, publishes, and reloads the list', async () => {
    renderTab()
    await screen.findByText('verified.tar.sig')

    fireEvent.click(screen.getByRole('checkbox', { name: 'Publish verified.tar.sig' }))
    expect(screen.getByText(/replaces the currently published release of the same build/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() => expect(setPublished).toHaveBeenCalledWith({ obuFirmwareId: 1, published: true }))
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Firmware published', { id: 'notification' }))
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2))
  })

  it('names the releases a publication replaced', async () => {
    setPublished.mockImplementation(() => ({
      unwrap: () => Promise.resolve(publication([{ obu_firmware_id: 2, file_name: 'published.tar.sig' }])),
    }))
    renderTab()
    await screen.findByText('verified.tar.sig')

    fireEvent.click(screen.getByRole('checkbox', { name: 'Publish verified.tar.sig' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith('Firmware published. It replaces published.tar.sig.', {
        id: 'notification',
      })
    )
  })

  it('unpublishes firmware and reports API errors', async () => {
    setPublished.mockImplementation(() => ({
      unwrap: () => Promise.reject({ data: { detail: 'OBU firmware was not found' } }),
    }))
    renderTab()
    await screen.findByText('published.tar.sig')

    fireEvent.click(screen.getByRole('checkbox', { name: 'Unpublish published.tar.sig' }))
    expect(screen.getByText(/Stop offering this firmware/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() => expect(setPublished).toHaveBeenCalledWith({ obuFirmwareId: 2, published: false }))
    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('OBU firmware was not found', { id: 'notification' })
    )
  })

  it('blocks deletion of published firmware and of files that cannot be checked', async () => {
    renderTab()
    await screen.findByText('published.tar.sig')

    expect(within(row('published.tar.sig')).getByRole('button', { name: 'Unpublish before deleting' })).toBeDisabled()
    expect(within(row('unknown.tar.sig')).getByRole('button', { name: /once storage is available/ })).toBeDisabled()
    expect(within(row('verified.tar.sig')).getByRole('button', { name: 'Delete Firmware' })).toBeEnabled()
    expect(within(row('untracked.tar.sig')).getByRole('button', { name: 'Delete Firmware' })).toBeEnabled()
  })

  it('deletes a stored file with its listed version', async () => {
    renderTab()
    await screen.findByText('untracked.tar.sig')

    fireEvent.click(within(row('untracked.tar.sig')).getByRole('button', { name: 'Delete Firmware' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() =>
      expect(deleteObject).toHaveBeenCalledWith({ object_id: 'untracked', provider_object_version: '17' })
    )
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith('Firmware deleted successfully', { id: 'notification' })
    )
  })

  it('cleans up records for missing and local files without a version', async () => {
    renderTab()
    await screen.findByText('local.tar.sig')

    fireEvent.click(within(row('local.tar.sig')).getByRole('button', { name: 'Clean Up Records' }))
    expect(screen.getByText(/must be removed there/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))
    await waitFor(() => expect(deleteObject).toHaveBeenCalledWith({ object_id: 'local', provider_object_version: null }))

    fireEvent.click(within(row('missing.tar.sig')).getByRole('button', { name: 'Clean Up Records' }))
    expect(screen.getByText(/missing from storage/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))
    await waitFor(() => expect(deleteObject).toHaveBeenCalledWith({ object_id: 'missing', provider_object_version: null }))
  })

  it('filters by manufacturer and publish state, and sorts by API fields', async () => {
    renderTab()
    await screen.findByText('verified.tar.sig')

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
    fireEvent.click(screen.getByRole('option', { name: 'Commsignia' }))
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Published' }))
    fireEvent.click(screen.getByRole('option', { name: 'Not published' }))
    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ manufacturer: 'Commsignia', published: false, page: 0 }))
    )

    fireEvent.click(screen.getByRole('columnheader', { name: 'Release' }))
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'release_version,asc' })))
    fireEvent.click(screen.getByRole('columnheader', { name: 'Published' }))
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'published,asc' })))
    // Unsortable columns fall back to the default order
    fireEvent.click(screen.getByRole('columnheader', { name: 'Details' }))
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'manufacturer,asc' })))

    fireEvent.click(screen.getByRole('button', { name: 'Next Page' }))
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })))
  })

  it('reports listing failures', async () => {
    list.mockImplementation(() => ({ unwrap: () => Promise.reject(new Error('down')) }))
    vi.spyOn(console, 'error').mockImplementation(() => {})
    renderTab()

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Failed to fetch OBU firmware'))
  })
})

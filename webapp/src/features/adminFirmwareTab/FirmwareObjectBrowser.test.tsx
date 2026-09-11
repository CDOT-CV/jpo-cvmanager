import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import AdminFirmwareTab from './AdminFirmwareTab'
import toast from 'react-hot-toast'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import {
  useDeleteFirmwareObjectMutation,
  useGetFirmwareUploadOptionsQuery,
  useLazyListFirmwareObjectsQuery,
} from '../api/firmwareApiSlice'

vi.mock('../api/firmwareApiSlice', () => ({
  useGetFirmwareUploadOptionsQuery: vi.fn(),
  useLazyListFirmwareObjectsQuery: vi.fn(),
  useDeleteFirmwareObjectMutation: vi.fn(),
}))
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn(), loading: vi.fn() } }))
vi.mock('./FirmwareUploadForm', () => ({
  default: ({ open, onSuccess }: { open: boolean; onSuccess: () => void }) =>
    open ? (
      <div>
        Upload form<button onClick={onSuccess}>Complete mocked upload</button>
      </div>
    ) : null,
}))

describe('Firmware object browser', () => {
  const renderFirmware = () =>
    render(
      <ThemeProvider theme={testTheme}>
        <AdminFirmwareTab />
      </ThemeProvider>
    )
  const trigger = vi.fn()
  const lazyQuery = vi.mocked(useLazyListFirmwareObjectsQuery)
  const optionsQuery = vi.mocked(useGetFirmwareUploadOptionsQuery)
  const deleteObject = vi.fn()

  const page = {
    objects: [
      {
        object_id: 'one',
        object_name: 'Commsignia/ITS-RS4-M/v1/file.bin',
        manufacturer: 'Commsignia',
        model: 'ITS-RS4-M',
        version: 'v1',
        file_name: 'file.bin',
        content_length: 9,
        verification_status: 'VERIFIED' as const,
        upload_status: 'VERIFIED',
        provider_object_version: '17',
      },
      {
        object_id: 'two',
        object_name: 'Kapsch/RIS-9260/v2/update.tar',
        manufacturer: 'Kapsch',
        model: 'RIS-9260',
        version: 'v2',
        file_name: 'update.tar',
        content_length: 3,
        verification_status: 'UNTRACKED' as const,
        provider_object_version: '18',
      },
    ],
    next_page_token: null,
  }

  beforeEach(() => {
    vi.resetAllMocks()
    trigger.mockImplementation(() => ({ unwrap: () => Promise.resolve(page) }))
    lazyQuery.mockReturnValue([trigger] as any)
    vi.mocked(useDeleteFirmwareObjectMutation).mockReturnValue([deleteObject] as any)
    deleteObject.mockImplementation(() => ({ unwrap: () => Promise.resolve() }))
    optionsQuery.mockReturnValue({
      data: {
        manufacturers: [
          { manufacturer_id: 1, name: 'Commsignia', file_extension: '.tar.sig', models: [] },
          { manufacturer_id: 2, name: 'Kapsch', file_extension: null, models: [] },
        ],
      },
      isFetching: false,
    } as any)
  })

  it('uses the standard admin table toolbar without page-local search', async () => {
    renderFirmware()

    expect(await screen.findByRole('button', { name: 'v1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'v2' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Manufacturer' })).toHaveStyle({ textTransform: 'none' })
    expect(screen.getByRole('columnheader', { name: 'Model' })).toBeInTheDocument()
    expect(screen.queryByPlaceholderText('Search')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Manufacturer' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'New' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'v1' }))
    expect(screen.getByText('Commsignia/ITS-RS4-M/v1/file.bin')).toBeInTheDocument()
  })

  it('reloads the first page when a manufacturer is selected', async () => {
    renderFirmware()
    await screen.findByRole('button', { name: 'v1' })

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
    fireEvent.click(screen.getByRole('option', { name: 'Kapsch' }))

    await waitFor(() =>
      expect(trigger).toHaveBeenLastCalledWith({
        manufacturer: 'Kapsch',
        page_size: 25,
        page_token: undefined,
      })
    )
  })

  it('uses the provider page token with the standard table pagination', async () => {
    trigger.mockImplementation(() => ({
      unwrap: () => Promise.resolve({ ...page, next_page_token: 'next-page' }),
    }))
    renderFirmware()
    await screen.findByRole('button', { name: 'v1' })

    fireEvent.click(screen.getByRole('button', { name: 'Next Page' }))

    await waitFor(() =>
      expect(trigger).toHaveBeenLastCalledWith({
        manufacturer: undefined,
        page_size: 25,
        page_token: 'next-page',
      })
    )
  })

  it('refreshes the table after a successful upload', async () => {
    renderFirmware()
    await screen.findByRole('button', { name: 'v1' })

    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    expect(screen.getByText('Upload form')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Complete mocked upload' }))
    expect(screen.queryByText('Upload form')).not.toBeInTheDocument()
    await waitFor(() => expect(trigger).toHaveBeenCalledTimes(2))
  })

  it('confirms deletion and refreshes the table after the API succeeds', async () => {
    renderFirmware()
    await screen.findByRole('button', { name: 'v1' })
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete firmware' })[0])

    expect(screen.getByText(/Commsignia \/ ITS-RS4-M \/ v1/)).toBeInTheDocument()
    expect(deleteObject).not.toHaveBeenCalled()
    trigger.mockImplementation(() => ({ unwrap: () => Promise.resolve({ ...page, objects: [page.objects[1]] }) }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() => expect(deleteObject).toHaveBeenCalledWith({ object_id: 'one', provider_object_version: '17' }))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'v1' })).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'v2' })).toBeInTheDocument()
    expect(toast.success).toHaveBeenCalledWith('Firmware deleted successfully', expect.any(Object))
  })

  it('cancels deletion without changing the listing', async () => {
    renderFirmware()
    await screen.findByRole('button', { name: 'v1' })
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete firmware' })[1])
    expect(screen.getByText(/Kapsch \/ RIS-9260 \/ v2/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'No' }))
    expect(deleteObject).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'v2' })).toBeInTheDocument()
    expect(trigger).toHaveBeenCalledTimes(1)
  })

  it('keeps an untracked file listed and displays the API conflict message', async () => {
    deleteObject.mockImplementation(() => ({
      unwrap: () => Promise.reject({ status: 409, data: { detail: 'The firmware file changed. Refresh the table.' } }),
    }))
    renderFirmware()
    await screen.findByRole('button', { name: 'v2' })
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete firmware' })[1])
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('The firmware file changed. Refresh the table.', expect.any(Object))
    )
    expect(deleteObject).toHaveBeenCalledWith({ object_id: 'two', provider_object_version: '18' })
    expect(screen.getByRole('button', { name: 'v2' })).toBeInTheDocument()
    expect(trigger).toHaveBeenCalledTimes(1)
    expect(toast.success).not.toHaveBeenCalled()
  })
})

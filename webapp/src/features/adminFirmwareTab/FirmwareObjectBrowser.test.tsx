import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
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
    total_elements: 2,
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

  it('uses the standard admin table toolbar with global search', async () => {
    renderFirmware()

    expect(await screen.findByRole('button', { name: 'v1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'v2' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Manufacturer' })).toHaveStyle({ textTransform: 'none' })
    expect(screen.getByRole('columnheader', { name: 'Model' })).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Search')).toBeInTheDocument()
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
        page: 0,
        size: 25,
        search: '',
      })
    )
  })

  it('uses numbered pages and the server result count for pagination', async () => {
    trigger.mockImplementation(() => ({
      unwrap: () => Promise.resolve({ ...page, total_elements: 26 }),
    }))
    renderFirmware()
    await screen.findByRole('button', { name: 'v1' })

    fireEvent.click(screen.getByRole('button', { name: 'Next Page' }))

    await waitFor(() =>
      expect(trigger).toHaveBeenLastCalledWith({
        manufacturer: undefined,
        page: 1,
        size: 25,
        search: '',
      })
    )
  })

  describe('server-side search', () => {
    beforeEach(async () => {
      // Exercise the real table debounce without waiting for wall-clock timers in CI.
      vi.useFakeTimers()
      trigger.mockImplementation(({ search }) => ({
        unwrap: () => Promise.resolve(search ? {
          objects: [{ ...page.objects[0], object_id: 'later', version: 'later-release' }],
          total_elements: 1,
        } : { ...page, total_elements: 26 }),
      }))
      await act(async () => { renderFirmware() })
    })

    afterEach(() => {
      cleanup()
      vi.clearAllTimers()
      vi.useRealTimers()
    })

    const searchFor = async (value: string) => {
      fireEvent.change(screen.getByPlaceholderText('Search'), { target: { value } })
      await act(async () => { await vi.advanceTimersByTimeAsync(500) })
    }

    it('searches from page zero and displays matches not previously loaded', async () => {
      await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Next Page' })) })
      expect(trigger).toHaveBeenLastCalledWith({
        page: 1, size: 25, search: '', manufacturer: undefined,
      })

      await searchFor('later-release')

      expect(trigger).toHaveBeenLastCalledWith({
        page: 0, size: 25, search: 'later-release', manufacturer: undefined,
      })
      expect(screen.getByRole('button', { name: 'later-release' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'v1' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Next Page' })).toBeDisabled()
    })

    it('debounces typing into one request with the final search term', async () => {
      fireEvent.change(screen.getByPlaceholderText('Search'), { target: { value: 'later' } })
      await act(async () => { await vi.advanceTimersByTimeAsync(300) })
      expect(trigger).toHaveBeenCalledTimes(1)

      await searchFor('later-release')

      expect(trigger).toHaveBeenCalledTimes(2)
      expect(trigger).toHaveBeenLastCalledWith({
        page: 0, size: 25, search: 'later-release', manufacturer: undefined,
      })
    })

    it('preserves the search when filtering by manufacturer and refreshing', async () => {
      await searchFor('later-release')
      fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
      await act(async () => { fireEvent.click(screen.getByRole('option', { name: 'Kapsch' })) })
      expect(trigger).toHaveBeenLastCalledWith({
        page: 0, size: 25, search: 'later-release', manufacturer: 'Kapsch',
      })

      trigger.mockClear()
      await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Refresh' })) })

      expect(trigger).toHaveBeenCalledTimes(1)
      expect(trigger).toHaveBeenLastCalledWith({
        page: 0, size: 25, search: 'later-release', manufacturer: 'Kapsch',
      })
      expect(screen.getByRole('button', { name: 'later-release' })).toBeInTheDocument()
    })

    it('restores the listing when search is cleared without losing the manufacturer filter', async () => {
      fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
      await act(async () => { fireEvent.click(screen.getByRole('option', { name: 'Kapsch' })) })
      await searchFor('later-release')
      expect(screen.queryByRole('button', { name: 'v1' })).not.toBeInTheDocument()

      await searchFor('')

      expect(trigger).toHaveBeenLastCalledWith({
        page: 0, size: 25, search: '', manufacturer: 'Kapsch',
      })
      expect(screen.getByRole('button', { name: 'v1' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Next Page' })).toBeEnabled()
    })

    it('deletes a search result and refreshes with the current search and manufacturer', async () => {
      fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
      await act(async () => { fireEvent.click(screen.getByRole('option', { name: 'Commsignia' })) })
      await searchFor('later-release')
      fireEvent.click(screen.getByRole('button', { name: 'later-release' }))
      expect(screen.getByText('File details')).toBeInTheDocument()

      fireEvent.click(screen.getByRole('button', { name: 'Delete Firmware' }))
      expect(deleteObject).not.toHaveBeenCalled()
      trigger.mockClear()
      trigger.mockImplementation(() => ({ unwrap: () => Promise.resolve({ objects: [], total_elements: 0 }) }))
      await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Yes' })) })

      expect(deleteObject).toHaveBeenCalledWith({ object_id: 'later', provider_object_version: '17' })
      expect(trigger).toHaveBeenCalledTimes(1)
      expect(trigger).toHaveBeenLastCalledWith({
        page: 0, size: 25, search: 'later-release', manufacturer: 'Commsignia',
      })
      expect(screen.queryByRole('button', { name: 'later-release' })).not.toBeInTheDocument()
      expect(screen.queryByText('File details')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Next Page' })).toBeDisabled()
      expect(toast.success).toHaveBeenCalledWith('Firmware deleted successfully', expect.any(Object))
    })
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
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete Firmware' })[0])

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
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete Firmware' })[1])
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
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete Firmware' })[1])
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

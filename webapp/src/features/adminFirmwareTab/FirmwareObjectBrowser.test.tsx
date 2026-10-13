import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import AdminFirmwareTab from './AdminFirmwareTab'
import { useGetFirmwareUploadOptionsQuery, useLazyListFirmwareObjectsQuery } from '../api/firmwareApiSlice'

vi.mock('../api/firmwareApiSlice', () => ({
  useGetFirmwareUploadOptionsQuery: vi.fn(),
  useLazyListFirmwareObjectsQuery: vi.fn(),
}))
vi.mock('./FirmwareUploadForm', () => ({
  default: ({ open, onSuccess }: { open: boolean; onSuccess: () => void }) =>
    open ? (
      <div>
        Upload form<button onClick={onSuccess}>Complete mocked upload</button>
      </div>
    ) : null,
}))

describe('Firmware object browser', () => {
  const trigger = vi.fn()
  const lazyQuery = vi.mocked(useLazyListFirmwareObjectsQuery)
  const optionsQuery = vi.mocked(useGetFirmwareUploadOptionsQuery)

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
      },
    ],
    total_elements: 2,
  }

  beforeEach(() => {
    vi.resetAllMocks()
    trigger.mockImplementation(() => ({ unwrap: () => Promise.resolve(page) }))
    lazyQuery.mockReturnValue([trigger] as any)
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
    render(<AdminFirmwareTab />)

    expect(await screen.findByRole('button', { name: 'v1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'v2' })).toBeInTheDocument()
    expect(screen.getAllByRole('columnheader', { name: 'Manufacturer' })[0]).toHaveStyle({ textTransform: 'none' })
    expect(screen.getAllByRole('columnheader', { name: 'Model' })[0]).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Search')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Manufacturer' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'New' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'v1' }))
    expect(screen.getByText('Commsignia/ITS-RS4-M/v1/file.bin')).toBeInTheDocument()
  })

  it('reloads the first page when a manufacturer is selected', async () => {
    render(<AdminFirmwareTab />)
    await screen.findByRole('button', { name: 'v1' })

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
    fireEvent.click(screen.getByRole('option', { name: 'Kapsch' }))

    await waitFor(() =>
      expect(trigger).toHaveBeenLastCalledWith({
        manufacturer: 'Kapsch',
        page: 0,
        size: 25,
        search: '',
        sort: 'manufacturer,asc',
      })
    )
  })

  it('uses numbered pages and the server result count for pagination', async () => {
    trigger.mockImplementation(() => ({
      unwrap: () => Promise.resolve({ ...page, total_elements: 26 }),
    }))
    render(<AdminFirmwareTab />)
    await screen.findByRole('button', { name: 'v1' })

    fireEvent.click(screen.getByRole('button', { name: 'Next Page' }))

    await waitFor(() =>
      expect(trigger).toHaveBeenLastCalledWith({
        manufacturer: undefined,
        page: 1,
        size: 25,
        search: '',
        sort: 'manufacturer,asc',
      })
    )
  })

  it('requests server-side sorting when a column header is selected', async () => {
    render(<AdminFirmwareTab />)
    await screen.findByRole('button', { name: 'v1' })

    fireEvent.click(screen.getAllByRole('columnheader', { name: 'Version' })[1])

    await waitFor(() => expect(trigger).toHaveBeenLastCalledWith({
      manufacturer: undefined,
      page: 0,
      size: 25,
      search: '',
      sort: 'version,asc',
    }))
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
      await act(async () => { render(<AdminFirmwareTab />) })
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
        page: 1, size: 25, search: '', manufacturer: undefined, sort: 'manufacturer,asc',
      })

      await searchFor('later-release')

      expect(trigger).toHaveBeenLastCalledWith({
        page: 0, size: 25, search: 'later-release', manufacturer: undefined, sort: 'manufacturer,asc',
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
        page: 0, size: 25, search: 'later-release', manufacturer: undefined, sort: 'manufacturer,asc',
      })
    })

    it('preserves the search when filtering by manufacturer and refreshing', async () => {
      await searchFor('later-release')
      fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
      await act(async () => { fireEvent.click(screen.getByRole('option', { name: 'Kapsch' })) })
      expect(trigger).toHaveBeenLastCalledWith({
        page: 0, size: 25, search: 'later-release', manufacturer: 'Kapsch', sort: 'manufacturer,asc',
      })

      trigger.mockClear()
      await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Refresh' })) })

      expect(trigger).toHaveBeenCalledTimes(1)
      expect(trigger).toHaveBeenLastCalledWith({
        page: 0, size: 25, search: 'later-release', manufacturer: 'Kapsch', sort: 'manufacturer,asc',
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
        page: 0, size: 25, search: '', manufacturer: 'Kapsch', sort: 'manufacturer,asc',
      })
      expect(screen.getByRole('button', { name: 'v1' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Next Page' })).toBeEnabled()
    })
  })

  it('refreshes the table after a successful upload', async () => {
    render(<AdminFirmwareTab />)
    await screen.findByRole('button', { name: 'v1' })

    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    expect(screen.getByText('Upload form')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Complete mocked upload' }))
    expect(screen.queryByText('Upload form')).not.toBeInTheDocument()
    await waitFor(() => expect(trigger).toHaveBeenCalledTimes(2))
  })
})

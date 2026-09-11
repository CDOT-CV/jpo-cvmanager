import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
      })
    )
  })

  it('searches through the API from page zero and displays matches not previously loaded', async () => {
    trigger.mockImplementation(({ search }) => ({
      unwrap: () => Promise.resolve(search ? {
        objects: [{ ...page.objects[0], object_id: 'later', version: 'later-release' }],
        total_elements: 1,
      } : { ...page, total_elements: 26 }),
    }))
    render(<AdminFirmwareTab />)
    await screen.findByRole('button', { name: 'v1' })
    fireEvent.click(screen.getByRole('button', { name: 'Next Page' }))
    await waitFor(() => expect(trigger).toHaveBeenLastCalledWith({
      page: 1, size: 25, search: '', manufacturer: undefined,
    }))

    fireEvent.change(screen.getByPlaceholderText('Search'), { target: { value: 'later-release' } })

    await waitFor(() => expect(trigger).toHaveBeenLastCalledWith({
      page: 0, size: 25, search: 'later-release', manufacturer: undefined,
    }))
    expect(await screen.findByRole('button', { name: 'later-release' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'v1' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next Page' })).toBeDisabled()

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
    fireEvent.click(screen.getByRole('option', { name: 'Kapsch' }))
    await waitFor(() => expect(trigger).toHaveBeenLastCalledWith({
      page: 0, size: 25, search: 'later-release', manufacturer: 'Kapsch',
    }))

    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(trigger).toHaveBeenCalledTimes(5))
    expect(trigger).toHaveBeenLastCalledWith({
      page: 0, size: 25, search: 'later-release', manufacturer: 'Kapsch',
    })

    fireEvent.change(screen.getByPlaceholderText('Search'), { target: { value: '' } })
    await waitFor(() => expect(trigger).toHaveBeenLastCalledWith({
      page: 0, size: 25, search: '', manufacturer: 'Kapsch',
    }))
    expect(await screen.findByRole('button', { name: 'v1' })).toBeInTheDocument()
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

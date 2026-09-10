import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import AdminFirmwareTab from './AdminFirmwareTab'
import { useLazyListFirmwareObjectsQuery, useListFirmwareObjectsQuery } from '../api/firmwareApiSlice'

vi.mock('../api/firmwareApiSlice', () => ({
  useListFirmwareObjectsQuery: vi.fn(),
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
  const query = vi.mocked(useListFirmwareObjectsQuery)
  const lazyQuery = vi.mocked(useLazyListFirmwareObjectsQuery)
  const loadManufacturer = vi.fn()

  beforeEach(() => {
    vi.resetAllMocks()
    lazyQuery.mockReturnValue([loadManufacturer] as any)
  })

  it('loads and displays a complete manufacturer tree when expanded', async () => {
    query.mockReturnValue({
      currentData: {
        objects: [
          {
            object_id: 'folder-marker',
            object_name: 'Vendor/',
            content_length: 0,
            verification_status: 'UNTRACKED',
          },
          {
            object_id: 'two',
            object_name: 'loose.bin',
            content_length: 3,
            verification_status: 'UNTRACKED',
          },
        ],
      },
      isFetching: false,
      refetch: vi.fn(),
    } as any)
    loadManufacturer.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          objects: [
            {
              object_id: 'one',
              object_name: 'Vendor/Model/v1/file.bin',
              content_length: 9,
              verification_status: 'VERIFIED',
              upload_status: 'VERIFIED',
            },
          ],
        }),
    })

    render(<AdminFirmwareTab />)
    expect(screen.queryByText('(empty segment)')).not.toBeInTheDocument()
    expect(screen.getByText('Untracked')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'file.bin' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Toggle Vendor' }))
    expect(await screen.findByRole('button', { name: 'file.bin' })).toBeInTheDocument()
    expect(loadManufacturer).toHaveBeenCalledWith({ manufacturer: 'Vendor' })

    fireEvent.click(screen.getByRole('button', { name: 'Toggle Vendor' }))
    expect(screen.queryByRole('button', { name: 'file.bin' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Toggle Vendor' }))
    fireEvent.click(screen.getByRole('button', { name: 'file.bin' }))
    expect(screen.getByText('Vendor/Model/v1/file.bin')).toBeInTheDocument()
    expect(loadManufacturer).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(query).toHaveBeenLastCalledWith({}, { refetchOnMountOrArgChange: true })
  })

  it('shows loading and empty states without pagination', () => {
    query.mockReturnValue({ isFetching: true, refetch: vi.fn() } as any)
    const { rerender } = render(<AdminFirmwareTab />)
    expect(screen.getByRole('status', { name: 'Loading firmware files' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeDisabled()
    query.mockReturnValue({
      currentData: { objects: [] },
      isFetching: false,
      refetch: vi.fn(),
    } as any)
    rerender(<AdminFirmwareTab />)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByText('No firmware folders were found.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Previous' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument()
  })

  it('shows errors and keeps uploading accessible', () => {
    query.mockReturnValue({
      error: { status: 503 },
      isFetching: false,
      refetch: vi.fn(),
    } as any)
    render(<AdminFirmwareTab />)
    expect(screen.getByRole('alert')).toHaveTextContent('Unable to load firmware files')
    fireEvent.click(screen.getByRole('button', { name: 'Add Firmware' }))
    expect(screen.getByText('Upload form')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Complete mocked upload' }))
    expect(screen.queryByText('Upload form')).not.toBeInTheDocument()
  })
})

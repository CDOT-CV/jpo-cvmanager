import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import AdminFirmwareTab from './AdminFirmwareTab'
import { useListFirmwareObjectsQuery } from '../api/firmwareApiSlice'

vi.mock('../api/firmwareApiSlice', () => ({
  useListFirmwareObjectsQuery: vi.fn(),
}))
vi.mock('./FirmwareUploadForm', () => ({
  default: () => <div>Upload form</div>,
}))

describe('Firmware object browser', () => {
  const query = vi.mocked(useListFirmwareObjectsQuery)
  beforeEach(() => vi.resetAllMocks())

  it('groups arbitrary paths, shows verification and selects files', () => {
    query.mockReturnValue({
      currentData: {
        container: 'bucket',
        objects: [
          {
            object_id: 'one',
            object_name: 'Vendor/Model/v1/file.bin',
            content_length: 9,
            verification_status: 'VERIFIED',
            upload_status: 'VERIFIED',
          },
          {
            object_id: 'two',
            object_name: 'loose.bin',
            content_length: 3,
            verification_status: 'UNTRACKED',
          },
        ],
        next_page_token: 'next',
      },
      isFetching: false,
      refetch: vi.fn(),
    } as any)
    render(<AdminFirmwareTab />)
    expect(screen.getByText('Untracked')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Toggle Vendor' }))
    expect(screen.queryByRole('button', { name: 'file.bin' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Toggle Vendor' }))
    fireEvent.click(screen.getByRole('button', { name: 'file.bin' }))
    expect(screen.getByText('Vendor/Model/v1/file.bin')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(query).toHaveBeenLastCalledWith(
      { page_size: 100, page_token: 'next' },
      { refetchOnMountOrArgChange: true }
    )
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(query).toHaveBeenLastCalledWith(
      { page_size: 100, page_token: undefined },
      { refetchOnMountOrArgChange: true }
    )
  })

  it('shows loading and empty states without enabling pagination', () => {
    query.mockReturnValue({ isFetching: true, refetch: vi.fn() } as any)
    const { rerender } = render(<AdminFirmwareTab />)
    expect(screen.getByRole('status', { name: 'Loading firmware files' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeDisabled()
    query.mockReturnValue({
      currentData: { container: 'bucket', objects: [], next_page_token: null },
      isFetching: false,
      refetch: vi.fn(),
    } as any)
    rerender(<AdminFirmwareTab />)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByText('No firmware files on this page.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })

  it('shows errors and keeps uploading accessible', () => {
    query.mockReturnValue({
      error: { status: 503 },
      isFetching: false,
      refetch: vi.fn(),
    } as any)
    render(<AdminFirmwareTab />)
    expect(screen.getByRole('alert')).toHaveTextContent('Unable to load firmware files')
    fireEvent.click(screen.getByRole('button', { name: 'Upload firmware' }))
    expect(screen.getByText('Upload form')).toBeInTheDocument()
  })
})

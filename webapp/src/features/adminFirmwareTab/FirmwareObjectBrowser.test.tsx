import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import AdminFirmwareTab from './AdminFirmwareTab'
import { useGetFirmwareUploadOptionsQuery, useListFirmwareObjectsQuery } from '../api/firmwareApiSlice'

vi.mock('../api/firmwareApiSlice', () => ({
  useGetFirmwareUploadOptionsQuery: vi.fn(),
  useListFirmwareObjectsQuery: vi.fn(),
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
  const optionsQuery = vi.mocked(useGetFirmwareUploadOptionsQuery)

  beforeEach(() => {
    vi.resetAllMocks()
    optionsQuery.mockReturnValue({
      data: {
        manufacturers: [
          { manufacturer_id: 1, name: 'Commsignia', models: [] },
          { manufacturer_id: 2, name: 'Kapsch', models: [] },
        ],
      },
      isFetching: false,
    } as any)
  })

  it('shows one firmware per row and searches the current page', () => {
    query.mockReturnValue({
      currentData: {
        objects: [
          {
            object_id: 'one',
            object_name: 'Commsignia/ITS-RS4-M/v1/file.bin',
            manufacturer: 'Commsignia',
            model: 'ITS-RS4-M',
            version: 'v1',
            file_name: 'file.bin',
            content_length: 9,
            verification_status: 'VERIFIED',
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
            verification_status: 'UNTRACKED',
          },
        ],
        next_page_token: 'next',
      },
      isFetching: false,
      refetch: vi.fn(),
    } as any)

    render(<AdminFirmwareTab />)

    expect(screen.getByRole('columnheader', { name: 'Manufacturer' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Model' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'file.bin' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'update.tar' })).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Search firmware'), { target: { value: 'RS4' } })
    expect(screen.getByRole('button', { name: 'file.bin' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'update.tar' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'file.bin' }))
    expect(screen.getByText('Commsignia/ITS-RS4-M/v1/file.bin')).toBeInTheDocument()
  })

  it('paginates and applies manufacturer filtering to the API request', () => {
    query.mockReturnValue({
      currentData: { objects: [], next_page_token: 'next' },
      isFetching: false,
      refetch: vi.fn(),
    } as any)
    render(<AdminFirmwareTab />)

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(query).toHaveBeenLastCalledWith(
      { manufacturer: undefined, page_size: 100, page_token: 'next' },
      { refetchOnMountOrArgChange: true }
    )

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
    fireEvent.click(screen.getByRole('option', { name: 'Kapsch' }))
    expect(query).toHaveBeenLastCalledWith(
      { manufacturer: 'Kapsch', page_size: 100, page_token: undefined },
      { refetchOnMountOrArgChange: true }
    )
  })

  it('shows loading and empty states without enabling pagination', () => {
    query.mockReturnValue({ isFetching: true, refetch: vi.fn() } as any)
    const { rerender } = render(<AdminFirmwareTab />)
    expect(screen.getByRole('status', { name: 'Loading firmware files' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeDisabled()

    query.mockReturnValue({
      currentData: { objects: [], next_page_token: null },
      isFetching: false,
      refetch: vi.fn(),
    } as any)
    rerender(<AdminFirmwareTab />)

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByText('No matching firmware on this page.')).toBeInTheDocument()
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
    fireEvent.click(screen.getByRole('button', { name: 'Add Firmware' }))
    expect(screen.getByText('Upload form')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Complete mocked upload' }))
    expect(screen.queryByText('Upload form')).not.toBeInTheDocument()
  })
})

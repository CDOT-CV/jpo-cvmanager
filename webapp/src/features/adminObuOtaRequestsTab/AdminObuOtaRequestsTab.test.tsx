import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import toast from 'react-hot-toast'
import { vi } from 'vitest'
import { testTheme } from '../../styles'
import { ObuOtaRequest } from '../../models/ObuFirmware'
import { useGetObuFirmwareUploadOptionsQuery, useLazyListObuOtaRequestsQuery } from '../api/obuFirmwareApiSlice'
import AdminObuOtaRequestsTab from './AdminObuOtaRequestsTab'

vi.mock('../api/obuFirmwareApiSlice', () => ({
  useGetObuFirmwareUploadOptionsQuery: vi.fn(),
  useLazyListObuOtaRequestsQuery: vi.fn(),
}))
vi.mock('../../components/AdminTable', async () => ({
  default: (await import('../adminObuFirmwareTab/test-utils')).MockAdminTable,
}))
vi.mock('react-hot-toast', () => ({ default: { error: vi.fn() } }))

const request = (overrides: Partial<ObuOtaRequest>): ObuOtaRequest => ({
  request_id: 1,
  obu_sn: 'OBU-0001',
  request_datetime: '2026-10-15T12:00:00Z',
  origin_ip: '10.0.0.5',
  obu_firmware_version: 'y20.47.0',
  requested_firmware_version: 'ob4-generic-ro-secureboot-y20.48.2-b228647.tar.sig',
  status: 'success',
  error_message: '',
  manufacturer: 'Vendor',
  ...overrides,
})

const requests = [
  request({}),
  request({
    request_id: 2,
    obu_sn: 'OBU-0002',
    status: 'error',
    error_message: 'Requested firmware was not found',
  }),
]

const row = (text: string) => screen.getByText(text).closest('tr') as HTMLElement

describe('AdminObuOtaRequestsTab', () => {
  const list = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    list.mockImplementation(() => ({ unwrap: () => Promise.resolve({ items: requests, total_elements: 30 }) }))
    vi.mocked(useLazyListObuOtaRequestsQuery).mockReturnValue([list] as any)
    vi.mocked(useGetObuFirmwareUploadOptionsQuery).mockReturnValue({
      data: { manufacturers: [{ manufacturer_id: 1, name: 'Vendor' }] },
      isFetching: false,
    } as any)
  })

  const renderTab = () =>
    render(
      <ThemeProvider theme={testTheme}>
        <AdminObuOtaRequestsTab />
      </ThemeProvider>
    )

  const chooseFilter = async (label: string, option: string) => {
    fireEvent.mouseDown(screen.getByRole('combobox', { name: label }))
    fireEvent.click(await screen.findByRole('option', { name: option }))
  }

  it('lists requests newest first in local time with their status', async () => {
    renderTab()

    await screen.findByText('OBU-0001')
    expect(list).toHaveBeenCalledWith({
      page: 0,
      size: 25,
      serial: undefined,
      manufacturer: undefined,
      status: undefined,
      sort: 'request_datetime,desc',
    })
    const success = row('OBU-0001')
    expect(within(success).getByText(new Date('2026-10-15T12:00:00Z').toLocaleString())).toBeInTheDocument()
    expect(within(success).getByText('y20.47.0')).toBeInTheDocument()
    expect(within(success).getByText('ob4-generic-ro-secureboot-y20.48.2-b228647.tar.sig')).toBeInTheDocument()
    expect(within(success).getByText('10.0.0.5')).toBeInTheDocument()
    expect(within(success).getByText('Success')).toBeInTheDocument()

    fireEvent.mouseOver(within(row('OBU-0002')).getByText('Error'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Requested firmware was not found')
  })

  it('searches by serial and filters by manufacturer and status', async () => {
    renderTab()
    await screen.findByText('OBU-0001')

    fireEvent.change(screen.getByPlaceholderText('Search'), { target: { value: '0002' } })
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, serial: '0002' })))

    await chooseFilter('Manufacturer', 'Vendor')
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ manufacturer: 'Vendor' })))

    await chooseFilter('Status', 'Error')
    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith(
        expect.objectContaining({ serial: '0002', manufacturer: 'Vendor', status: 'error' })
      )
    )

    await chooseFilter('Status', 'All requests')
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ status: undefined })))
  })

  it('sorts by the API fields and pages through results', async () => {
    renderTab()
    await screen.findByText('OBU-0001')

    fireEvent.click(screen.getByText('Serial Number'))
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'obu_sn,asc' })))
    fireEvent.click(screen.getByText('Serial Number'))
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'obu_sn,desc' })))
    fireEvent.click(screen.getByText('Requested Firmware'))
    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'requested_firmware_version,asc' }))
    )
    // Columns the API cannot sort fall back to the newest requests first
    fireEvent.click(screen.getByText('Origin IP'))
    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'request_datetime,desc' }))
    )

    fireEvent.click(screen.getByRole('button', { name: 'Next Page' }))
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })))
  })

  it('refreshes from the first page', async () => {
    renderTab()
    await screen.findByText('OBU-0001')
    fireEvent.click(screen.getByRole('button', { name: 'Next Page' }))
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })))

    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 })))
  })

  it('reports listing failures', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    list.mockImplementation(() => ({ unwrap: () => Promise.reject(new Error('offline')) }))
    renderTab()

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Failed to fetch OBU update requests'))
  })
})

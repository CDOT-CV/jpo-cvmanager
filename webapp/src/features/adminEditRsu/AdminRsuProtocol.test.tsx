import { fireEvent, render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { setupStore } from '../../store'
import AdminEditRsu from './AdminEditRsu'
import AdminAddRsu from '../adminAddRsu/AdminAddRsu'
import {
  useGetRsuQuery,
  useGetRsuAllowedSelectionsQuery,
  usePatchRsuMutation,
  useCreateRsuMutation,
} from '../api/rsuApiSlice'

vi.mock('../api/rsuApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/rsuApiSlice')>()),
  useGetRsuQuery: vi.fn(),
  useGetRsuAllowedSelectionsQuery: vi.fn(),
  usePatchRsuMutation: vi.fn(),
  useCreateRsuMutation: vi.fn(),
}))

beforeEach(() => {
  vi.mocked(useGetRsuAllowedSelectionsQuery).mockReturnValue({
    data: {
      primary_routes: ['I-70'],
      rsu_models: ['Vendor Model'],
      ssh_credential_groups: ['SSH'],
      snmp_credential_groups: ['SNMP'],
      snmp_version_groups: ['NTCIP 1218'],
      organizations: ['Org'],
    },
    isLoading: false,
  } as any)
  vi.mocked(useGetRsuQuery).mockReturnValue({
    data: {
      ip: '192.0.2.1',
      geo_position: { latitude: '39', longitude: '-105' },
      primary_route: 'I-70',
      milepost: 1,
      model: 'Vendor Model',
      ssh_credential_group: 'SSH',
      snmp_credential_group: 'SNMP',
      snmp_version_group: 'RSU 4.1',
      organizations: ['Org'],
    },
    isLoading: false,
  } as any)
  vi.mocked(usePatchRsuMutation).mockReturnValue([vi.fn(), { isLoading: false }] as any)
  vi.mocked(useCreateRsuMutation).mockReturnValue([vi.fn(), { isLoading: false }] as any)
})

it('shows an existing legacy assignment but makes it unavailable for selection', () => {
  render(
    <Provider store={setupStore({})}>
      <MemoryRouter>
        <AdminEditRsu />
      </MemoryRouter>
    </Provider>
  )
  expect(screen.getByText('RSU 4.1 (retired)')).toBeInTheDocument()
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /SNMP Protocol/ }))
  expect(screen.getByRole('option', { name: 'RSU 4.1 (retired)' })).toHaveAttribute('aria-disabled', 'true')
  expect(screen.getByRole('option', { name: 'NTCIP 1218' })).not.toHaveAttribute('aria-disabled', 'true')
})

it('offers only supported protocol assignments for new RSUs', () => {
  render(
    <Provider store={setupStore({})}>
      <MemoryRouter>
        <AdminAddRsu />
      </MemoryRouter>
    </Provider>
  )
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /SNMP Protocol/ }))
  expect(screen.getByRole('option', { name: 'NTCIP 1218' })).toBeInTheDocument()
  expect(screen.queryByRole('option', { name: /RSU 4.1/ })).not.toBeInTheDocument()
})

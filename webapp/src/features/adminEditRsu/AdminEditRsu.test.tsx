import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminEditRsu from './AdminEditRsu'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { BrowserRouter } from 'react-router-dom'

const mocks = vi.hoisted(() => ({
  loading: true,
  rsu: null as any,
  allowed: null as any,
  authLoginData: {
    data: {
      organizations: [
        { organization: 101, name: 'Org One', role: 'ADMIN' },
        { organization: 202, name: 'Org Two', role: 'ADMIN' },
        { organization: 303, name: 'Org Three', role: 'ADMIN' },
      ],
    },
  },
}))

vi.mock('../../generalSlices/userSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../generalSlices/userSlice')>()),
  selectAuthLoginData: () => mocks.authLoginData,
}))
vi.mock('../api/rsuApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/rsuApiSlice')>()),
  useGetRsuQuery: () => ({ data: mocks.rsu, isLoading: mocks.loading }),
  useGetRsuAllowedSelectionsQuery: () => ({ data: mocks.allowed, isLoading: mocks.loading }),
  usePatchRsuMutation: () => [vi.fn(), { isLoading: false }],
}))

const rsuInfo = {
  ip: '10.0.0.1',
  geo_position: { latitude: '39.7', longitude: '-104.9' },
  milepost: 1,
  primary_route: 'I-25',
  serial_number: 'serial',
  model: 'model',
  scms_id: 'scms',
  ssh_credential_group: 'ssh',
  snmp_credential_group: 'snmp',
  snmp_version_group: 'v3',
  organizations: [101, 202],
  tim_deposit: false,
  snmp_monitoring: false,
}
const allowedSelections = {
  primary_routes: ['I-25'],
  rsu_models: ['model'],
  ssh_credential_groups: ['ssh'],
  snmp_credential_groups: ['snmp'],
  snmp_version_groups: ['v3'],
  organizations: [101, 202, 303],
}

const renderComponent = () =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <BrowserRouter>
          <AdminEditRsu />
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

describe('AdminEditRsu', () => {
  beforeEach(() => {
    mocks.loading = true
    mocks.rsu = null
    mocks.allowed = null
  })

  it('should take a snapshot', () => {
    const { container } = renderComponent()
    expect(replaceChaoticIds(container)).toMatchSnapshot()
  })

  it('uses organization names for RSU organization IDs', async () => {
    mocks.loading = false
    mocks.rsu = rsuInfo
    mocks.allowed = allowedSelections
    renderComponent()
    const organizations = document.body.querySelector<HTMLElement>('#organizations')!
    fireEvent.mouseDown(organizations)
    expect(await screen.findByRole('option', { name: 'Org Three' })).toBeTruthy()
    expect(screen.getByRole('option', { name: 'Org One' })).toBeTruthy()
    expect(screen.getByRole('option', { name: 'Org Two' })).toBeTruthy()
  })
})

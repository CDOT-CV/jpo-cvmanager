import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { vi } from 'vitest'
import toast from 'react-hot-toast'
import { confirmAlert } from 'react-confirm-alert'
import AdminOrganizationTabRsu from './AdminOrganizationTabRsu'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import {
  getOrgData,
  updateOrgTimDeposit,
  updateOrgSnmpMonitoring,
} from '../adminOrganizationTab/adminOrganizationTabSlice'

vi.mock('react-confirm-alert', () => ({ confirmAlert: vi.fn() }))

vi.mock('../adminOrganizationTab/adminOrganizationTabSlice', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../adminOrganizationTab/adminOrganizationTabSlice')>()
  return {
    ...actual,
    getOrgData: vi.fn(),
    updateOrgTimDeposit: vi.fn(),
    updateOrgSnmpMonitoring: vi.fn(),
  }
})

const ORG = 'Owner Org'

// A plain thunk that resolves to { payload }, mirroring what dispatching an async thunk returns
const thunkReturning = (payload: any) => () => async () => ({ payload })

const orgRsu = (ip: string, owner: string) => ({ ip, primary_route: 'I-25', milepost: 1, owner_organization: owner })

const mockOrgRsus = (rsus: ReturnType<typeof orgRsu>[] | undefined, success = true) => {
  vi.mocked(getOrgData).mockImplementation(
    thunkReturning({ success, data: rsus ? { org_data: { org_rsus: rsus } } : undefined }) as any
  )
}

const lastConfirm = () => vi.mocked(confirmAlert).mock.calls.at(-1)![0] as any

const renderTab = () =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <AdminOrganizationTabRsu
          selectedOrg={ORG}
          selectedOrgEmail="org@test.com"
          tableData={[]}
          updateTableData={() => {}}
        />
      </Provider>
    </ThemeProvider>
  )

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <AdminOrganizationTabRsu
          selectedOrg={''}
          selectedOrgEmail={''}
          tableData={[]}
          updateTableData={(_: string) => {}}
        />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

describe('AdminOrganizationTabRsu org-wide RSU option changes', () => {
  beforeEach(() => {
    vi.mocked(confirmAlert).mockClear()
    vi.mocked(updateOrgTimDeposit).mockReset()
    vi.mocked(updateOrgSnmpMonitoring).mockReset()
    vi.mocked(updateOrgTimDeposit).mockImplementation(thunkReturning({ success: true, message: 'TIM ok' }) as any)
    vi.mocked(updateOrgSnmpMonitoring).mockImplementation(thunkReturning({ success: true, message: 'SNMP ok' }) as any)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows an error and updates nothing when RSU ownership cannot be verified', async () => {
    const toastError = vi.spyOn(toast, 'error').mockReturnValue('id' as any)
    mockOrgRsus(undefined, false)
    renderTab()

    fireEvent.click(screen.getByText('Enable TIM Deposit'))

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('Failed to verify RSU ownership; no RSUs were updated'))
    expect(confirmAlert).not.toHaveBeenCalled()
    expect(updateOrgTimDeposit).not.toHaveBeenCalled()
  })

  it('tells the user there is nothing to update when the organization has no RSUs', async () => {
    mockOrgRsus([])
    renderTab()

    fireEvent.click(screen.getByText('Disable TIM Deposit'))

    await waitFor(() => expect(confirmAlert).toHaveBeenCalled())
    expect(lastConfirm().title).toBe('Disable TIM Deposit')
    expect(lastConfirm().message).toBe('This organization has no RSUs to update.')
    expect(lastConfirm().buttons.map((b: any) => b.label)).toEqual(['OK'])
  })

  it('explains that no RSUs will be updated when none are owned by the organization', async () => {
    mockOrgRsus([orgRsu('10.0.0.1', 'Other Org')])
    renderTab()

    fireEvent.click(screen.getByText('Enable SNMP Monitoring'))

    await waitFor(() => expect(confirmAlert).toHaveBeenCalled())
    expect(lastConfirm().message).toContain(`None of the RSUs in this organization are owned by ${ORG}`)
    lastConfirm().buttons[0].onClick()
    expect(updateOrgSnmpMonitoring).not.toHaveBeenCalled()
  })

  it('updates all RSUs when the organization owns every RSU', async () => {
    const toastSuccess = vi.spyOn(toast, 'success').mockReturnValue('id' as any)
    mockOrgRsus([orgRsu('10.0.0.1', ORG), orgRsu('10.0.0.2', ORG)])
    renderTab()

    fireEvent.click(screen.getByText('Enable TIM Deposit'))

    await waitFor(() => expect(confirmAlert).toHaveBeenCalled())
    expect(lastConfirm().message).toContain('all 2 RSU(s)')
    lastConfirm().buttons[0].onClick()

    await waitFor(() => expect(toastSuccess).toHaveBeenCalledWith('TIM ok'))
    expect(updateOrgTimDeposit).toHaveBeenCalledWith({ orgName: ORG, email: 'org@test.com', timDeposit: true })
  })

  it('warns that only owned RSUs will be updated and reports API failures', async () => {
    const toastError = vi.spyOn(toast, 'error').mockReturnValue('id' as any)
    vi.mocked(updateOrgSnmpMonitoring).mockImplementation(thunkReturning({ success: false }) as any)
    mockOrgRsus([orgRsu('10.0.0.1', ORG), orgRsu('10.0.0.2', 'Other Org')])
    renderTab()

    fireEvent.click(screen.getByText('Disable SNMP Monitoring'))

    await waitFor(() => expect(confirmAlert).toHaveBeenCalled())
    expect(lastConfirm().title).toBe('Disable SNMP Monitoring')
    expect(lastConfirm().message).toContain(`Only the 1 RSU(s) owned by ${ORG} will be updated`)
    expect(lastConfirm().message).toContain('1 RSU(s) owned by other organizations will not be changed')
    lastConfirm().buttons[0].onClick()

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('Failed to update RSUs'))
    expect(updateOrgSnmpMonitoring).toHaveBeenCalledWith({ orgName: ORG, email: 'org@test.com', snmpMonitoring: false })
  })

  it('shows the API error message when an owned-RSU update fails', async () => {
    const toastError = vi.spyOn(toast, 'error').mockReturnValue('id' as any)
    vi.mocked(updateOrgTimDeposit).mockImplementation(thunkReturning({ success: false, message: 'nope' }) as any)
    mockOrgRsus([orgRsu('10.0.0.1', ORG)])
    renderTab()

    fireEvent.click(screen.getByText('Disable TIM Deposit'))

    await waitFor(() => expect(confirmAlert).toHaveBeenCalled())
    lastConfirm().buttons[0].onClick()
    await waitFor(() => expect(toastError).toHaveBeenCalledWith('nope'))
  })
})

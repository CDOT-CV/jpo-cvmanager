import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminOrganizationTabRsu from './AdminOrganizationTabRsu'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'

const mocks = vi.hoisted(() => ({
  tableProps: { current: null as any },
  patch: vi.fn(),
  getRsuOrgs: vi.fn(),
  confirmAlert: vi.fn(),
  loadingGlobal: { value: false },
  rsus: { value: [] as any[] },
  toast: {
    loading: vi.fn(() => 'toast-id'),
    success: vi.fn(),
    error: vi.fn(),
    dismiss: vi.fn(),
  },
}))

vi.mock('react-hot-toast', () => ({ default: mocks.toast }))
vi.mock('react-confirm-alert', () => ({ confirmAlert: mocks.confirmAlert }))
vi.mock('../../components/AdminTable', () => ({
  default: (props: any) => {
    mocks.tableProps.current = props
    return <div data-testid="admin-table" />
  },
}))
vi.mock('../../generalSlices/userSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../generalSlices/userSlice')>()),
  selectLoadingGlobal: () => mocks.loadingGlobal.value,
}))
vi.mock('../api/organizationApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/organizationApiSlice')>()),
  useGetAllRsusNotInOrganizationQuery: () => ({ currentData: [{ id: 1, ip: '10.0.0.9' }] }),
  usePatchOrganizationMutation: () => [mocks.patch],
  useLazyGetRsuOrganizationsQuery: () => [mocks.getRsuOrgs],
}))
vi.mock('../api/rsuApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/rsuApiSlice')>()),
  useGetAllRsusQuery: () => ({ currentData: { content: mocks.rsus.value } }),
}))

const ok = (value: any = undefined) => ({ unwrap: () => Promise.resolve(value) })
const fail = (err: any = 'boom') => ({ unwrap: () => Promise.reject(err) })
const orgsResult = (n: number) => ok(Array.from({ length: n }, (_, i) => ({ id: i })))
const rsu = (ip: string, tim = true, snmp = true) => ({ ip, tim_deposit: tim, snmp_monitoring: snmp }) as any

const renderComponent = () =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <AdminOrganizationTabRsu selectedOrgId={1} selectedOrgName="Org" />
      </Provider>
    </ThemeProvider>
  )

const actions = () => mocks.tableProps.current.actions as any[]

const confirmButton = (label: string) => {
  const options = mocks.confirmAlert.mock.calls.at(-1)![0]
  return { options, button: options.buttons.find((b: any) => b.label === label) }
}

// Triggers a table action and presses "Yes" in the resulting confirm dialog
const confirmYes = async (actionIndex: number, rowData: any) => {
  act(() => {
    actions()[actionIndex].onClick({}, rowData)
  })
  const { options, button } = confirmButton('Yes')
  await act(async () => {
    await button.onClick()
  })
  return options
}

// Clicks a header button and presses "Yes" in the resulting confirm dialog
const clickHeaderAndConfirm = async (name: string) => {
  fireEvent.click(screen.getByRole('button', { name }))
  const { options, button } = confirmButton('Yes')
  await act(async () => {
    await button.onClick()
  })
  return options
}

const selectRsus = (list: any[]) => {
  const multiselect = actions()[2].icon() as ReactElement<any>
  act(() => {
    multiselect.props.onChange(list)
  })
}

describe('AdminOrganizationTabRsu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.loadingGlobal.value = false
    mocks.rsus.value = [rsu('1.1.1.1'), rsu('2.2.2.2')]
    mocks.patch.mockReturnValue(ok())
    mocks.getRsuOrgs.mockReturnValue(orgsResult(2))
    vi.spyOn(window, 'alert').mockImplementation(() => {})
  })

  it('should take a snapshot', () => {
    const { container } = renderComponent()
    expect(replaceChaoticIds(container)).toMatchSnapshot()
  })

  it('does not render the table while global loading', () => {
    mocks.loadingGlobal.value = true
    renderComponent()
    expect(screen.queryByTestId('admin-table')).toBeNull()
  })

  describe('TIM deposit and SNMP monitoring status', () => {
    it.each([
      ['all enabled', [rsu('a', true, true), rsu('b', true, true)], 'Enabled'],
      ['all disabled', [rsu('a', false, false), rsu('b', false, false)], 'Disabled'],
      ['mixed', [rsu('a', true, false), rsu('b', false, true)], 'Mixed'],
      ['no rsus', [], 'Disabled'],
    ])('%s', (_name, list, expected) => {
      mocks.rsus.value = list
      renderComponent()
      expect(screen.getByText(`TIM Deposit: ${expected}`)).toBeTruthy()
      expect(screen.getByText(`SNMP Monitoring: ${expected}`)).toBeTruthy()
    })

    it('computes TIM and SNMP independently', () => {
      mocks.rsus.value = [rsu('a', true, false), rsu('b', true, false)]
      renderComponent()
      expect(screen.getByText('TIM Deposit: Enabled')).toBeTruthy()
      expect(screen.getByText('SNMP Monitoring: Disabled')).toBeTruthy()
    })
  })

  describe('column renderers', () => {
    it.each([
      ['TIM Deposit', 'tim_deposit'],
      ['SNMP Monitoring', 'snmp_monitoring'],
    ])('%s renders Enabled/Disabled', (title, field) => {
      renderComponent()
      const column = mocks.tableProps.current.columns.find((c: any) => c.title === title)
      const enabled = render(<ThemeProvider theme={testTheme}>{column.render({ [field]: true })}</ThemeProvider>)
      expect(enabled.getByText('Enabled')).toBeTruthy()
      const disabled = render(<ThemeProvider theme={testTheme}>{column.render({ [field]: false })}</ThemeProvider>)
      expect(disabled.getByText('Disabled')).toBeTruthy()
    })
  })

  describe('delete confirmation dialogs', () => {
    it('row action asks for a single RSU and calls rsuOnDelete on Yes', async () => {
      renderComponent()
      const options = await confirmYes(0, rsu('1.1.1.1'))
      expect(options.title).toBe('Delete RSU')
      expect(options.message).toContain('"1.1.1.1"')
      expect(options.message).toContain('Org organization')
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, rsus_to_remove: ['1.1.1.1'] })
    })

    it('row action with an array lists IPs and calls rsuMultiDelete', async () => {
      renderComponent()
      const options = await confirmYes(0, [rsu('1.1.1.1'), rsu('2.2.2.2')])
      expect(options.message).toContain('1.1.1.1, 2.2.2.2')
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, rsus_to_remove: ['1.1.1.1', '2.2.2.2'] })
    })

    it('toolbar action reports the selected count for arrays', async () => {
      renderComponent()
      const options = await confirmYes(1, [rsu('1.1.1.1'), rsu('2.2.2.2')])
      expect(options.title).toBe('Delete Selected RSUs')
      expect(options.message).toContain('delete 2 RSU(s)')
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, rsus_to_remove: ['1.1.1.1', '2.2.2.2'] })
    })

    it('toolbar action reports 1 for a single row and uses rsuOnDelete', async () => {
      renderComponent()
      const options = await confirmYes(1, rsu('1.1.1.1'))
      expect(options.message).toContain('delete 1 RSU(s)')
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, rsus_to_remove: ['1.1.1.1'] })
    })

    it.each([0, 1])('choosing No on action %i does nothing', (index) => {
      renderComponent()
      act(() => {
        actions()[index].onClick({}, rsu('1.1.1.1'))
      })
      confirmButton('No').button.onClick()
      expect(mocks.patch).not.toHaveBeenCalled()
    })
  })

  describe('rsuOnDelete', () => {
    it('removes an RSU that belongs to multiple organizations', async () => {
      renderComponent()
      await confirmYes(0, rsu('1.1.1.1'))
      expect(mocks.toast.loading).toHaveBeenCalledWith('Deleting RSU 1.1.1.1...')
      expect(mocks.toast.success).toHaveBeenCalledWith('RSU deleted successfully', { id: 'toast-id' })
    })

    it('alerts and does not patch when the RSU only belongs to one organization', async () => {
      mocks.getRsuOrgs.mockReturnValue(orgsResult(1))
      renderComponent()
      await confirmYes(0, rsu('1.1.1.1'))
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(mocks.toast.dismiss).toHaveBeenCalledWith('toast-id')
      expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('Cannot remove RSU 1.1.1.1 from Org'))
    })

    it('shows an error toast when the lookup fails', async () => {
      mocks.getRsuOrgs.mockReturnValue(fail('lookup failed'))
      renderComponent()
      await confirmYes(0, rsu('1.1.1.1'))
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete RSU due to error: lookup failed', {
        id: 'toast-id',
      })
    })

    it('shows an error toast when the patch fails', async () => {
      mocks.patch.mockReturnValue(fail('patch failed'))
      renderComponent()
      await confirmYes(0, rsu('1.1.1.1'))
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete RSU due to error: patch failed', {
        id: 'toast-id',
      })
    })
  })

  describe('rsuMultiDelete', () => {
    it('looks up every RSU and removes them all', async () => {
      renderComponent()
      await confirmYes(0, [rsu('1.1.1.1'), rsu('2.2.2.2')])
      expect(mocks.getRsuOrgs).toHaveBeenCalledTimes(2)
      expect(mocks.toast.success).toHaveBeenCalledWith('RSU(s) deleted successfully', { id: 'toast-id' })
    })

    it('alerts with every invalid RSU and skips the patch', async () => {
      mocks.getRsuOrgs.mockImplementation(() => orgsResult(1))
      renderComponent()
      await confirmYes(0, [rsu('1.1.1.1'), rsu('2.2.2.2')])
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('1.1.1.1, 2.2.2.2'))
      expect(mocks.toast.dismiss).toHaveBeenCalledWith('toast-id')
    })

    it('blocks the whole deletion if only one RSU is invalid', async () => {
      mocks.getRsuOrgs.mockReturnValueOnce(orgsResult(2)).mockReturnValueOnce(orgsResult(1))
      renderComponent()
      await confirmYes(0, [rsu('1.1.1.1'), rsu('2.2.2.2')])
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('RSU(s) 2.2.2.2 from'))
    })

    it('shows an error toast when a lookup fails', async () => {
      mocks.getRsuOrgs.mockReturnValue(fail('nope'))
      renderComponent()
      await confirmYes(0, [rsu('1.1.1.1'), rsu('2.2.2.2')])
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete RSU(s) due to error: nope', {
        id: 'toast-id',
      })
    })

    it('shows an error toast when the patch fails', async () => {
      mocks.patch.mockReturnValue(fail('patch failed'))
      renderComponent()
      await confirmYes(0, [rsu('1.1.1.1'), rsu('2.2.2.2')])
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete RSU(s) due to error: patch failed', {
        id: 'toast-id',
      })
    })
  })

  describe('rsuMultiAdd', () => {
    it('errors when no RSUs are selected', async () => {
      renderComponent()
      await act(async () => {
        await actions()[3].onClick()
      })
      expect(mocks.toast.error).toHaveBeenCalledWith('Please select RSUs to add')
      expect(mocks.patch).not.toHaveBeenCalled()
    })

    it('adds the selected RSUs, shows success and clears the selection', async () => {
      renderComponent()
      selectRsus([rsu('3.3.3.3'), rsu('4.4.4.4')])
      await act(async () => {
        await actions()[3].onClick()
      })
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, rsus_to_add: ['3.3.3.3', '4.4.4.4'] })
      expect(mocks.toast.loading).toHaveBeenCalledWith('Adding 2 RSU(s)...')
      expect(mocks.toast.success).toHaveBeenCalledWith('RSU(s) added successfully', { id: 'toast-id' })

      mocks.patch.mockClear()
      await act(async () => {
        await actions()[3].onClick()
      })
      expect(mocks.toast.error).toHaveBeenCalledWith('Please select RSUs to add')
      expect(mocks.patch).not.toHaveBeenCalled()
    })

    it('shows an error toast and keeps the selection when the patch fails', async () => {
      mocks.patch.mockReturnValue(fail('patch failed'))
      renderComponent()
      selectRsus([rsu('3.3.3.3')])
      await act(async () => {
        await actions()[3].onClick()
      })
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to add RSU(s) due to error: patch failed', {
        id: 'toast-id',
      })
      mocks.patch.mockReturnValue(ok())
      await act(async () => {
        await actions()[3].onClick()
      })
      expect(mocks.patch).toHaveBeenLastCalledWith({ id: 1, rsus_to_add: ['3.3.3.3'] })
    })
  })

  describe.each([
    ['Enable TIM Deposit', 'tim_deposit', true, 'Enabling TIM Deposit...', 'enabled TIM Deposit', 'TIM Deposit'],
    ['Disable TIM Deposit', 'tim_deposit', false, 'Disabling TIM Deposit...', 'disabled TIM Deposit', 'TIM Deposit'],
    [
      'Enable SNMP Monitoring',
      'snmp_monitoring',
      true,
      'Enabling SNMP Monitoring...',
      'enabled SNMP Monitoring',
      'SNMP Monitoring',
    ],
    [
      'Disable SNMP Monitoring',
      'snmp_monitoring',
      false,
      'Disabling SNMP Monitoring...',
      'disabled SNMP Monitoring',
      'SNMP Monitoring',
    ],
  ])('%s button', (buttonName, field, value, loadingMsg, successFragment, label) => {
    it('opens a confirmation dialog', () => {
      renderComponent()
      fireEvent.click(screen.getByRole('button', { name: buttonName }))
      const { options } = confirmButton('Yes')
      expect(options.title).toBe(buttonName)
      expect(options.message).toContain('overwrite any previous settings')
      expect(mocks.patch).not.toHaveBeenCalled()
    })

    it('patches the organization and shows success on Yes', async () => {
      renderComponent()
      await clickHeaderAndConfirm(buttonName)
      expect(mocks.toast.loading).toHaveBeenCalledWith(loadingMsg)
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, [field]: value })
      expect(mocks.toast.success).toHaveBeenCalledWith(`Successfully ${successFragment} for all RSUs in Org`, {
        id: 'toast-id',
      })
    })

    it('shows an error toast when the patch fails', async () => {
      mocks.patch.mockReturnValue(fail('patch failed'))
      renderComponent()
      await clickHeaderAndConfirm(buttonName)
      expect(mocks.toast.error).toHaveBeenCalledWith(`Failed to update ${label}: patch failed`, { id: 'toast-id' })
    })

    it('does nothing on No', () => {
      renderComponent()
      fireEvent.click(screen.getByRole('button', { name: buttonName }))
      confirmButton('No').button.onClick()
      expect(mocks.patch).not.toHaveBeenCalled()
    })
  })

  it('clears the pending RSUs when the selected organization changes', async () => {
    const { rerender } = renderComponent()
    selectRsus([rsu('3.3.3.3')])
    rerender(
      <ThemeProvider theme={testTheme}>
        <Provider store={setupStore({})}>
          <AdminOrganizationTabRsu selectedOrgId={1} selectedOrgName="Other" />
        </Provider>
      </ThemeProvider>
    )
    await act(async () => {
      await actions()[3].onClick()
    })
    await waitFor(() => expect(mocks.toast.error).toHaveBeenCalledWith('Please select RSUs to add'))
  })
})

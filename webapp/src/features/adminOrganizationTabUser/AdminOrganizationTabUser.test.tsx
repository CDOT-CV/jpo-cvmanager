import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminOrganizationTabUser from './AdminOrganizationTabUser'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'

const mocks = vi.hoisted(() => ({
  tableProps: { current: null as any },
  patch: vi.fn(),
  getUserOrgs: vi.fn(),
  dispatch: vi.fn(),
  confirmAlert: vi.fn(),
  loadingGlobal: { value: false },
  authEmail: { value: 'me@test.com' },
  toast: {
    loading: vi.fn(() => 'toast-id'),
    success: vi.fn(),
    error: vi.fn(),
    dismiss: vi.fn(),
  },
}))

vi.mock('react-hot-toast', () => ({ default: mocks.toast }))
vi.mock('react-confirm-alert', () => ({ confirmAlert: mocks.confirmAlert }))
vi.mock('react-redux', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-redux')>()),
  useDispatch: () => mocks.dispatch,
}))
vi.mock('../../components/AdminTable', () => ({
  default: (props: any) => {
    mocks.tableProps.current = props
    return <div data-testid="admin-table" />
  },
}))
vi.mock('../../generalSlices/userSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../generalSlices/userSlice')>()),
  selectLoadingGlobal: () => mocks.loadingGlobal.value,
  selectAuthLoginData: () => ({ data: { email: mocks.authEmail.value } }),
  selectEmail: () => mocks.authEmail.value,
  addOrModifyOrgAssociationByOrgName: (payload: any) => ({ type: 'addOrModify', payload }),
}))
vi.mock('../api/organizationApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/organizationApiSlice')>()),
  useGetAllUsersNotInOrganizationQuery: () => ({
    data: [{ id: 'u1', email: 'a@test.com', first_name: 'A', last_name: 'A', role: '' }],
  }),
  usePatchOrganizationMutation: () => [mocks.patch],
  useLazyGetUserOrganizationsQuery: () => [mocks.getUserOrgs],
}))
vi.mock('../api/userApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/userApiSlice')>()),
  useGetUsersQuery: () => ({
    data: {
      content: [
        { email: 'me@test.com', first_name: 'Me', last_name: 'X', organizations: [{ organization: 'Org', role: 'ADMIN' }] },
        { email: 'b@test.com', first_name: 'B', last_name: 'B', organizations: [] },
      ],
    },
  }),
  useGetUserAllowedSelectionsQuery: () => ({ data: { roles: ['user', 'operator', 'admin'] } }),
}))

const ok = (value: any = undefined) => ({ unwrap: () => Promise.resolve(value) })
const fail = (err: any = 'boom') => ({ unwrap: () => Promise.reject(err) })
const orgsResult = (n: number) => ok(Array.from({ length: n }, (_, i) => ({ id: i })))

const renderComponent = () =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <AdminOrganizationTabUser selectedOrgId={1} selectedOrgName="Org" />
      </Provider>
    </ThemeProvider>
  )

const actions = () => mocks.tableProps.current.actions as any[]
const row = (email: string, role = 'user') => ({ email, role, first_name: '', last_name: '' }) as any

// Triggers an action's onClick and runs the "Yes" button of the confirm dialog
const confirmYes = async (actionIndex: number, rowData: any) => {
  act(() => {
    actions()[actionIndex].onClick({}, rowData)
  })
  const options = mocks.confirmAlert.mock.calls.at(-1)![0]
  await act(async () => {
    await options.buttons.find((b: any) => b.label === 'Yes').onClick()
  })
  return options
}

const selectUsers = (users: any[]) => {
  const multiselect = actions()[2].icon() as ReactElement<any>
  act(() => {
    multiselect.props.onChange(users)
  })
}

describe('AdminOrganizationTabUser', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.loadingGlobal.value = false
    mocks.authEmail.value = 'me@test.com'
    mocks.patch.mockReturnValue(ok())
    mocks.getUserOrgs.mockReturnValue(orgsResult(2))
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

  it('maps roles to lowercase and falls back to empty string', () => {
    renderComponent()
    const data = mocks.tableProps.current.data
    expect(data.map((d: any) => d.role)).toEqual(['admin', ''])
  })

  describe('delete confirmation dialogs', () => {
    it('row delete action asks for a single user and calls userOnDelete on Yes', async () => {
      renderComponent()
      const options = await confirmYes(0, row('b@test.com'))
      expect(options.title).toBe('Delete User')
      expect(options.message).toContain('"b@test.com"')
      expect(options.message).toContain('Org organization')
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, users_to_remove: ['b@test.com'] })
    })

    it('row delete action with an array lists all emails and calls userMultiDelete', async () => {
      renderComponent()
      const options = await confirmYes(0, [row('b@test.com'), row('c@test.com')])
      expect(options.message).toContain('b@test.com, c@test.com')
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, users_to_remove: ['b@test.com', 'c@test.com'] })
    })

    it('toolbar delete action reports the selected count for arrays', async () => {
      renderComponent()
      const options = await confirmYes(1, [row('b@test.com'), row('c@test.com')])
      expect(options.title).toBe('Delete Selected Users')
      expect(options.message).toContain('delete 2 user(s)')
      expect(mocks.patch).toHaveBeenCalledTimes(1)
    })

    it('toolbar delete action reports 1 for a single row and uses userOnDelete', async () => {
      renderComponent()
      const options = await confirmYes(1, row('b@test.com'))
      expect(options.message).toContain('delete 1 user(s)')
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, users_to_remove: ['b@test.com'] })
    })

    it('choosing No does nothing', () => {
      renderComponent()
      act(() => {
        actions()[0].onClick({}, row('b@test.com'))
      })
      const options = mocks.confirmAlert.mock.calls.at(-1)![0]
      options.buttons.find((b: any) => b.label === 'No').onClick()
      expect(mocks.patch).not.toHaveBeenCalled()
    })
  })

  describe('userOnDelete', () => {
    it('removes a user that belongs to multiple organizations', async () => {
      renderComponent()
      await confirmYes(0, row('b@test.com'))
      expect(mocks.toast.loading).toHaveBeenCalledWith('Deleting User b@test.com...')
      expect(mocks.dispatch).not.toHaveBeenCalled()
    })

    it('dispatches deleteOrgAssociationByOrgId when deleting the logged-in user', async () => {
      renderComponent()
      await confirmYes(0, row('me@test.com'))
      expect(mocks.dispatch).toHaveBeenCalledTimes(1)
      expect(mocks.dispatch.mock.calls[0][0].payload).toBe(1)
    })

    it('alerts and does not patch when the user only belongs to one organization', async () => {
      mocks.getUserOrgs.mockReturnValue(orgsResult(1))
      renderComponent()
      await confirmYes(0, row('b@test.com'))
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(mocks.toast.dismiss).toHaveBeenCalledWith('toast-id')
      expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('Cannot remove User b@test.com from Org'))
    })

    it('shows an error toast when the organization lookup fails', async () => {
      mocks.getUserOrgs.mockReturnValue(fail('lookup failed'))
      renderComponent()
      await confirmYes(0, row('b@test.com'))
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete User due to error: lookup failed', {
        id: 'toast-id',
      })
    })

    it('shows an error toast when the patch fails', async () => {
      mocks.patch.mockReturnValue(fail('patch failed'))
      renderComponent()
      await confirmYes(0, row('b@test.com'))
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete User due to error: patch failed', {
        id: 'toast-id',
      })
    })
  })

  describe('userMultiDelete', () => {
    it('removes all users and shows success', async () => {
      renderComponent()
      await confirmYes(0, [row('b@test.com'), row('c@test.com')])
      expect(mocks.getUserOrgs).toHaveBeenCalledTimes(2)
      expect(mocks.toast.success).toHaveBeenCalledWith('User(s) deleted successfully', { id: 'toast-id' })
      expect(mocks.dispatch).not.toHaveBeenCalled()
    })

    it('dispatches when the logged-in user is among the removed users', async () => {
      renderComponent()
      await confirmYes(0, [row('b@test.com'), row('me@test.com')])
      expect(mocks.dispatch).toHaveBeenCalledTimes(1)
    })

    it('alerts with every invalid user and skips the patch', async () => {
      mocks.getUserOrgs.mockImplementation(() => orgsResult(1))
      renderComponent()
      await confirmYes(0, [row('b@test.com'), row('c@test.com')])
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('b@test.com, c@test.com'))
      expect(mocks.toast.dismiss).toHaveBeenCalledWith('toast-id')
    })

    it('blocks the whole deletion if only one user is invalid', async () => {
      mocks.getUserOrgs.mockReturnValueOnce(orgsResult(2)).mockReturnValueOnce(orgsResult(1))
      renderComponent()
      await confirmYes(0, [row('b@test.com'), row('c@test.com')])
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('User(s) c@test.com from'))
    })

    it('shows an error toast when a lookup fails', async () => {
      mocks.getUserOrgs.mockReturnValue(fail('nope'))
      renderComponent()
      await confirmYes(0, [row('b@test.com'), row('c@test.com')])
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete User(s) due to error: nope', {
        id: 'toast-id',
      })
    })

    it('shows an error toast when the patch fails', async () => {
      mocks.patch.mockReturnValue(fail('patch failed'))
      renderComponent()
      await confirmYes(0, [row('b@test.com'), row('c@test.com')])
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete User(s) due to error: patch failed', {
        id: 'toast-id',
      })
    })
  })

  describe('userMultiAdd', () => {
    it('errors when no users are selected', async () => {
      renderComponent()
      await act(async () => {
        await actions()[3].onClick()
      })
      expect(mocks.toast.error).toHaveBeenCalledWith('Please select users to add')
      expect(mocks.patch).not.toHaveBeenCalled()
    })

    it('adds users with lowercased roles and clears the selection', async () => {
      renderComponent()
      selectUsers([{ id: 'u1', email: 'a@test.com', role: 'OPERATOR' }])
      expect(screen.getByText(/New User: a@test.com/)).toBeTruthy()
      await act(async () => {
        await actions()[3].onClick()
      })
      expect(mocks.patch).toHaveBeenCalledWith({
        id: 1,
        users_to_add: [{ email: 'a@test.com', role: 'operator' }],
      })
      expect(mocks.toast.success).toHaveBeenCalledWith('User(s) added successfully', { id: 'toast-id' })
      expect(mocks.dispatch).not.toHaveBeenCalled()
      await waitFor(() => expect(screen.queryByText(/New User:/)).toBeNull())
    })

    it('defaults the role to USER when selecting a user without a role', async () => {
      renderComponent()
      selectUsers([{ id: 'u1', email: 'a@test.com' }])
      await act(async () => {
        await actions()[3].onClick()
      })
      expect(mocks.patch).toHaveBeenCalledWith({
        id: 1,
        users_to_add: [{ email: 'a@test.com', role: 'user' }],
      })
    })

    it('dispatches when the logged-in user is added', async () => {
      renderComponent()
      selectUsers([{ id: 'u2', email: 'me@test.com', role: 'admin' }])
      await act(async () => {
        await actions()[3].onClick()
      })
      expect(mocks.dispatch).toHaveBeenCalledTimes(1)
      expect(mocks.dispatch.mock.calls[0][0].payload).toEqual({ name: 'Org', role: 'admin' })
    })

    it('shows an error toast and keeps the selection when the patch fails', async () => {
      mocks.patch.mockReturnValue(fail('patch failed'))
      renderComponent()
      selectUsers([{ id: 'u1', email: 'a@test.com', role: 'user' }])
      await act(async () => {
        await actions()[3].onClick()
      })
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to add User(s) due to error: patch failed', {
        id: 'toast-id',
      })
      expect(screen.getByText(/New User: a@test.com/)).toBeTruthy()
    })

    it('removes a pending user with the remove button', () => {
      renderComponent()
      selectUsers([{ id: 'u1', email: 'a@test.com', role: 'user' }])
      fireEvent.click(screen.getByLabelText('Remove a@test.com'))
      expect(screen.queryByText(/New User:/)).toBeNull()
    })
  })

  describe('userBulkEdit', () => {
    const bulk = (changes: Record<number, any>) => {
      // The returned promise resolves after a 2s timer; the edit itself is not awaited
      void mocks.tableProps.current.editable.onBulkUpdate(changes)
    }

    it('patches modified users and shows success', async () => {
      renderComponent()
      bulk({ 0: { oldData: row('b@test.com', 'user'), newData: row('b@test.com', 'admin') } })
      await waitFor(() => expect(mocks.toast.success).toHaveBeenCalledWith('User(s) updated successfully', { id: 'toast-id' }))
      expect(mocks.patch).toHaveBeenCalledWith({
        id: 1,
        users_to_modify: [{ email: 'b@test.com', role: 'admin' }],
      })
      expect(mocks.dispatch).not.toHaveBeenCalled()
    })

    it('dispatches for the logged-in user only', async () => {
      renderComponent()
      bulk({
        0: { oldData: row('b@test.com'), newData: row('b@test.com', 'operator') },
        1: { oldData: row('me@test.com'), newData: row('me@test.com', 'admin') },
      })
      await waitFor(() => expect(mocks.toast.success).toHaveBeenCalled())
      expect(mocks.dispatch).toHaveBeenCalledTimes(1)
      expect(mocks.dispatch.mock.calls[0][0].payload).toEqual({ name: 'Org', role: 'admin' })
    })

    it('shows an error toast when the patch fails', async () => {
      mocks.patch.mockReturnValue(fail('patch failed'))
      renderComponent()
      bulk({ 0: { oldData: row('b@test.com'), newData: row('b@test.com', 'admin') } })
      await waitFor(() =>
        expect(mocks.toast.error).toHaveBeenCalledWith('Failed to update User(s) due to error: patch failed', {
          id: 'toast-id',
        })
      )
      expect(mocks.dispatch).not.toHaveBeenCalled()
    })
  })

  it('clears the pending users when the selected organization changes', () => {
    const { rerender } = renderComponent()
    selectUsers([{ id: 'u1', email: 'a@test.com', role: 'user' }])
    expect(screen.getByText(/New User:/)).toBeTruthy()
    rerender(
      <ThemeProvider theme={testTheme}>
        <Provider store={setupStore({})}>
          <AdminOrganizationTabUser selectedOrgId={1} selectedOrgName="Other" />
        </Provider>
      </ThemeProvider>
    )
    expect(screen.queryByText(/New User:/)).toBeNull()
  })
})

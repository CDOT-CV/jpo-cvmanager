import { act, render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { BrowserRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import AdminUserTab from './AdminUserTab'

const mocks = vi.hoisted(() => ({
  tableProps: { current: null as any },
}))

vi.mock('../../components/AdminTable', () => ({
  default: (props: any) => {
    mocks.tableProps.current = props
    return <div data-testid="admin-table" />
  },
}))

vi.mock('../api/userApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/userApiSlice')>()),
  useGetUsersQuery: () => ({ data: { content: [] }, isFetching: false }),
  useLazyGetUsersQuery: () => [vi.fn()],
  useDeleteUserMutation: () => [vi.fn()],
  useDeleteMultipleUsersMutation: () => [vi.fn()],
}))

test('supports cross-page selection and bulk delete for the user table', () => {
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <BrowserRouter>
          <AdminUserTab />
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

  expect(screen.getByTestId('admin-table')).toBeTruthy()
  expect(mocks.tableProps.current.selectionKeyField).toBe('email')
  expect(mocks.tableProps.current.selectedRows).toEqual([])

  const selectedUser = {
    id: 1,
    email: 'person@example.com',
    first_name: 'Test',
    last_name: 'Person',
    super_user: false,
    organizations: [],
  }
  act(() => mocks.tableProps.current.onSelectionChange([selectedUser]))

  expect(mocks.tableProps.current.selectedRows).toEqual([selectedUser])
  const bulkDeleteAction = mocks.tableProps.current.actions.find(
    (action: any) => action.position === 'toolbarOnSelect'
  )
  expect(bulkDeleteAction.iconProps.title).toBe('Delete Selected (1)')
  expect(bulkDeleteAction.disabled).toBe(false)
})

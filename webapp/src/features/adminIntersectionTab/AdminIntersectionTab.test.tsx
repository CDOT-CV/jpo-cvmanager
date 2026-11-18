import { act, render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import AdminIntersectionTab from './AdminIntersectionTab'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { BrowserRouter } from 'react-router-dom'

const mocks = vi.hoisted(() => ({
  tableProps: { current: null as any },
}))

vi.mock('../../components/AdminTable', () => ({
  default: (props: any) => {
    mocks.tableProps.current = props
    return <div data-testid="admin-table" />
  },
}))

vi.mock('../api/adminIntersectionApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/adminIntersectionApiSlice')>()),
  useGetIntersectionsQuery: () => ({ data: { content: [] }, isFetching: false }),
  useLazyGetIntersectionsQuery: () => [vi.fn()],
}))

test('supports cross-page selection and bulk delete for the intersection table', () => {
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <BrowserRouter>
          <AdminIntersectionTab />
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

  expect(screen.getByTestId('admin-table')).toBeTruthy()
  expect(mocks.tableProps.current.columns.map((column: any) => column.field)).toEqual([
    'intersection_id',
    'intersection_name',
    'origin_ip',
    'rsus',
  ])
  expect(typeof mocks.tableProps.current.handleQueryChange).toBe('function')
  expect(mocks.tableProps.current.tableRef).toBeTruthy()
  expect(mocks.tableProps.current.selectionKeyField).toBe('intersection_id')
  expect(mocks.tableProps.current.selectedRows).toEqual([])

  const selectedIntersection = {
    intersection_id: '123',
    intersection_name: 'Main & First',
    origin_ip: '192.0.2.1',
    rsus: '192.0.2.2',
  }
  act(() => mocks.tableProps.current.onSelectionChange([selectedIntersection]))

  expect(mocks.tableProps.current.selectedRows).toEqual([selectedIntersection])
  const bulkDeleteAction = mocks.tableProps.current.actions.find(
    (action: any) => action.position === 'toolbarOnSelect'
  )
  expect(bulkDeleteAction.iconProps.title).toBe('Delete Selected (1)')
  expect(bulkDeleteAction.disabled).toBe(false)
})

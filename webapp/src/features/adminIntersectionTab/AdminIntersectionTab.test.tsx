import { render, screen } from '@testing-library/react'
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

test('uses server-side paging for the intersection table', () => {
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
})

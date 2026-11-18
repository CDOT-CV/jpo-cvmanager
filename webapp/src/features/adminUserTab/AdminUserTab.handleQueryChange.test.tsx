import { render, act } from '@testing-library/react'
import { vi } from 'vitest'
import AdminUserTab from './AdminUserTab'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { BrowserRouter } from 'react-router-dom'
import toast from 'react-hot-toast'

let capturedHandleQueryChange: ((query: any) => Promise<any>) | undefined
const mockTrigger = vi.fn()
const mockMarkTableRenderedData = vi.fn()
const mockQueryRef: { current: any } = { current: null }

vi.mock('../../components/AdminTable', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../components/AdminTable')>()),
  default: (props: any) => {
    capturedHandleQueryChange = props.handleQueryChange
    return <div data-testid="admin-table" />
  },
}))

vi.mock('../../hooks/useAdminTableQuerySync', () => ({
  useAdminTableQuerySync: () => ({
    currentQueryRef: mockQueryRef,
    markTableRenderedData: mockMarkTableRenderedData,
    handleRefresh: vi.fn(),
  }),
}))

vi.mock('../api/userApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/userApiSlice')>()),
  useLazyGetUsersQuery: () => [mockTrigger],
  useGetUsersQuery: () => ({ data: undefined }),
  useDeleteUserMutation: () => [vi.fn()],
  useDeleteMultipleUsersMutation: () => [vi.fn()],
}))

vi.mock('react-hot-toast', () => ({
  default: Object.assign(vi.fn(), { error: vi.fn(), success: vi.fn(), loading: vi.fn() }),
}))

const renderTab = (organization = 'Test Org') =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({ user: { value: { organization: { name: organization } } } } as any)}>
        <BrowserRouter>
          <AdminUserTab />
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

const makeQuery = (overrides = {}) => ({ page: 2, pageSize: 10, search: 'abc', orderByCollection: [], ...overrides })

const callHandler = async (query: any) => {
  let result: any
  await act(async () => {
    result = await capturedHandleQueryChange!(query)
  })
  return result
}

describe('handleQueryChange', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockQueryRef.current = null
    capturedHandleQueryChange = undefined
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  it('returns fetched data and stores the query when successful', async () => {
    const response = { content: [{ email: 'a@b.c' }], totalElements: 7 }
    mockTrigger.mockReturnValue({ unwrap: () => Promise.resolve(response) })
    renderTab()

    const result = await callHandler(makeQuery())

    const expectedParams = { page: 2, size: 10, sort: 'first_name,asc', search: 'abc', organization: 'Test Org' }
    expect(mockTrigger).toHaveBeenCalledWith(expectedParams)
    expect(mockQueryRef.current).toEqual(expectedParams)
    expect(mockMarkTableRenderedData).toHaveBeenCalledWith(expectedParams, response)
    expect(result).toEqual({ data: [{ email: 'a@b.c' }], page: 2, totalCount: 7 })
  })

  it('falls back to empty data and zero count when the result has no content or totalElements', async () => {
    mockTrigger.mockReturnValue({ unwrap: () => Promise.resolve({}) })
    renderTab()

    const result = await callHandler(makeQuery())

    expect(result).toEqual({ data: [], page: 2, totalCount: 0 })
  })

  it('keeps the requested page when the organization is unchanged', async () => {
    mockQueryRef.current = { organization: 'Test Org' }
    mockTrigger.mockReturnValue({ unwrap: () => Promise.resolve({ content: [], totalElements: 0 }) })
    renderTab()

    const query = makeQuery()
    const result = await callHandler(query)

    expect(query.page).toBe(2)
    expect(mockTrigger).toHaveBeenCalledWith(expect.objectContaining({ page: 2 }))
    expect(result.page).toBe(2)
  })

  it('resets to page 0 when the organization changed', async () => {
    mockQueryRef.current = { organization: 'Previous Org' }
    mockTrigger.mockReturnValue({ unwrap: () => Promise.resolve({ content: [], totalElements: 0 }) })
    renderTab()

    const query = makeQuery()
    const result = await callHandler(query)

    expect(query.page).toBe(0)
    expect(mockTrigger).toHaveBeenCalledWith(expect.objectContaining({ page: 0, organization: 'Test Org' }))
    expect(mockQueryRef.current.organization).toBe('Test Org')
    expect(result.page).toBe(0)
  })

  it('shows an error toast and returns empty data when the fetch fails', async () => {
    mockTrigger.mockReturnValue({ unwrap: () => Promise.reject(new Error('boom')) })
    renderTab()

    const result = await callHandler(makeQuery({ page: 3 }))

    expect(toast.error).toHaveBeenCalledWith('Failed to fetch Users')
    expect(mockMarkTableRenderedData).not.toHaveBeenCalled()
    expect(result).toEqual({ data: [], page: 3, totalCount: 0 })
  })

  it('sorts by the first order in orderByCollection', async () => {
    mockTrigger.mockReturnValue({ unwrap: () => Promise.resolve({ content: [], totalElements: 0 }) })
    renderTab()

    await callHandler(makeQuery({ orderByCollection: [{ orderBy: { field: 'email' }, orderDirection: 'desc' }] }))

    expect(mockTrigger).toHaveBeenCalledWith(expect.objectContaining({ sort: 'email,desc' }))
  })
})

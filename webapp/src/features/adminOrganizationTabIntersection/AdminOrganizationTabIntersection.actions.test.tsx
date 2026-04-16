import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { vi } from 'vitest'
import fetchMock from 'jest-fetch-mock'
import toast from 'react-hot-toast'
import AdminOrganizationTabIntersection from './AdminOrganizationTabIntersection'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import EnvironmentVars from '../../EnvironmentVars'
import { AdminOrgIntersection } from '../adminOrganizationTab/adminOrganizationTabSlice'

vi.mock('react-confirm-alert', () => ({
  confirmAlert: (options: { buttons: { label: string; onClick: () => void }[] }) =>
    options.buttons[0].onClick(),
}))

vi.mock('../../components/AdminTable', () => ({
  default: ({ actions, data }: any) => (
    <div>
      {actions.map((action: any, i: number) => (
        <button
          key={i}
          data-testid={`action-${action.position}`}
          onClick={() => action.onClick(null, action.position === 'row' ? data[0] : data)}
        >
          {`action-${action.position}`}
        </button>
      ))}
    </div>
  ),
}))

const ADMIN_INT_URL = `${EnvironmentVars.CVIZ_API_SERVER_URL}/admin/intersections`

const preloadedAuth = {
  user: {
    loading: false,
    value: {
      authLoginData: { token: 'test-token' },
      organization: { name: 'selectedOrg', role: 'admin' },
    },
  },
}

const row = (id: string, name = `Intersection ${id}`): AdminOrgIntersection => ({
  intersection_id: id,
  intersection_name: name,
  ref_pt: { latitude: '0', longitude: '0' },
})

const renderWithRows = (tableData: AdminOrgIntersection[]) => {
  const updateTableData = vi.fn()
  const utils = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore(preloadedAuth)}>
        <AdminOrganizationTabIntersection
          selectedOrg="selectedOrg"
          selectedOrgEmail="email@test.com"
          tableData={tableData}
          updateTableData={updateTableData}
        />
      </Provider>
    </ThemeProvider>
  )
  return { ...utils, updateTableData }
}

const getIntersectionBody = (organizations: string[]) =>
  JSON.stringify({
    intersection_data: { organizations },
    allowed_selections: {},
  })

const findPatchCall = () =>
  fetchMock.mock.calls.find(([, opts]: any) => opts?.method === 'PATCH')

describe('AdminOrganizationTabIntersection — delete actions', () => {
  beforeEach(() => {
    fetchMock.resetMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('single delete', () => {
    it('dispatches editOrg and shows success toast when intersection has multiple orgs', async () => {
      const toastSuccess = vi.spyOn(toast, 'success').mockReturnValue('id' as any)
      fetchMock.mockResponseOnce(getIntersectionBody(['org1', 'org2']))
      fetchMock.mockResponseOnce(JSON.stringify({ message: 'ok' }), { status: 200 })

      const { updateTableData } = renderWithRows([row('1')])

      fireEvent.click(screen.getByTestId('action-row'))

      await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))

      const getReq = fetchMock.mock.calls[0][0] as Request
      expect(getReq.url).toBe(`${ADMIN_INT_URL}/1`)
      expect(getReq.method).toBe('GET')

      const patchCall = findPatchCall()
      expect(patchCall).toBeDefined()
      const body = JSON.parse((patchCall![1] as any).body)
      expect(body).toMatchObject({
        name: 'selectedOrg',
        email: 'email@test.com',
        intersections_to_remove: ['1'],
      })

      await waitFor(() => expect(updateTableData).toHaveBeenCalledWith('selectedOrg'))
      await waitFor(() =>
        expect(toastSuccess).toHaveBeenCalledWith('Intersection deleted successfully')
      )
    })

    it('alerts and skips editOrg when intersection belongs to only one org', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
      fetchMock.mockResponseOnce(getIntersectionBody(['only-org']))

      const { updateTableData } = renderWithRows([row('1')])

      fireEvent.click(screen.getByTestId('action-row'))

      await waitFor(() =>
        expect(alertSpy).toHaveBeenCalledWith(
          'Cannot remove Intersection 1 from selectedOrg because it must belong to at least one organization.'
        )
      )
      expect(findPatchCall()).toBeUndefined()
      expect(updateTableData).not.toHaveBeenCalled()
    })

    it('shows error toast when editOrg fails', async () => {
      const toastError = vi.spyOn(toast, 'error').mockReturnValue('id' as any)
      fetchMock.mockResponseOnce(getIntersectionBody(['org1', 'org2']))
      fetchMock.mockResponseOnce(JSON.stringify({ message: 'nope' }), { status: 500 })

      renderWithRows([row('1')])

      fireEvent.click(screen.getByTestId('action-row'))

      await waitFor(() =>
        expect(toastError).toHaveBeenCalledWith('Failed to delete Intersection')
      )
    })
  })

  describe('multi delete', () => {
    it('dispatches editOrg with all ids when all intersections have multiple orgs', async () => {
      const toastSuccess = vi.spyOn(toast, 'success').mockReturnValue('id' as any)
      fetchMock.mockResponse(async (req) => {
        if (req.method === 'GET') {
          return getIntersectionBody(['org1', 'org2'])
        }
        return JSON.stringify({ message: 'ok' })
      })

      const { updateTableData } = renderWithRows([row('1'), row('2'), row('3')])

      fireEvent.click(screen.getByTestId('action-toolbarOnSelect'))

      await waitFor(() =>
        expect(toastSuccess).toHaveBeenCalledWith('Intersection(s) deleted successfully')
      )

      const patchCall = findPatchCall()
      expect(patchCall).toBeDefined()
      const body = JSON.parse((patchCall![1] as any).body)
      expect(body.intersections_to_remove).toEqual(['1', '2', '3'])
      expect(body.name).toBe('selectedOrg')
      expect(body.email).toBe('email@test.com')
      expect(updateTableData).toHaveBeenCalledWith('selectedOrg')
    })

    it('alerts with invalid ids and skips editOrg when any intersection has only one org', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
      fetchMock.mockResponse(async (req) => {
        if (req.method !== 'GET') {
          return JSON.stringify({ message: 'ok' })
        }
        if (req.url.endsWith('/1')) {
          return getIntersectionBody(['org1', 'org2'])
        }
        return getIntersectionBody(['only-org'])
      })

      const { updateTableData } = renderWithRows([row('1'), row('2'), row('3')])

      fireEvent.click(screen.getByTestId('action-toolbarOnSelect'))

      await waitFor(() =>
        expect(alertSpy).toHaveBeenCalledWith(
          'Cannot remove Intersection(s) 2, 3 from selectedOrg because they must belong to at least one organization.'
        )
      )
      expect(findPatchCall()).toBeUndefined()
      expect(updateTableData).not.toHaveBeenCalled()
    })
  })
})

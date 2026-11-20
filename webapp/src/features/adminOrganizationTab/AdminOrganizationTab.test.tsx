import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminOrganizationTab from './AdminOrganizationTab'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { replaceChaoticIds } from '../../utils/test-utils'
import { BrowserRouter } from 'react-router-dom'

const mocks = vi.hoisted(() => ({
  dispatch: vi.fn(),
  state: {
    user: {
      value: {
        organization: { organization: 1 },
      },
    },
  },
  organizations: [] as { id: number; name: string }[],
  deleteOrganization: vi.fn(),
  confirmAlert: vi.fn(),
  invalidateCaches: vi.fn(),
  toast: {
    loading: vi.fn(() => 'toast-id'),
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('react-redux', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-redux')>()),
  useDispatch: () => mocks.dispatch,
  useSelector: (selector: (state: typeof mocks.state) => unknown) => selector(mocks.state),
}))

vi.mock('react-hot-toast', () => ({ default: mocks.toast }))
vi.mock('react-confirm-alert', () => ({ confirmAlert: mocks.confirmAlert }))

vi.mock('../api/organizationApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/organizationApiSlice')>()),
  useGetOrganizationsQuery: () => ({ data: mocks.organizations }),
  useDeleteOrganizationMutation: () => [mocks.deleteOrganization],
}))

vi.mock('../api/organizationPatchInvalidation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/organizationPatchInvalidation')>()),
  invalidateCachesForOrganizationDelete: mocks.invalidateCaches,
}))

vi.mock('../../feature-flags', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../feature-flags')>()),
  ConditionalRenderRsu: ({ children }: any) => children,
  ConditionalRenderIntersection: ({ children }: any) => children,
}))

vi.mock('../adminOrganizationTabRsu/AdminOrganizationTabRsu', () => ({
  default: () => null,
}))
vi.mock('../adminOrganizationTabIntersection/AdminOrganizationTabIntersection', () => ({
  default: () => null,
}))
vi.mock('../adminOrganizationTabUser/AdminOrganizationTabUser', () => ({
  default: ({ selectedOrgId }: { selectedOrgId: number }) => (
    <div data-testid="selected-organization">{selectedOrgId}</div>
  ),
}))

const renderComponent = () =>
  render(
    <ThemeProvider theme={testTheme}>
      <BrowserRouter>
        <AdminOrganizationTab />
      </BrowserRouter>
    </ThemeProvider>
  )

const ok = () => ({ unwrap: () => Promise.resolve(undefined) })
const fail = (error: string) => ({ unwrap: () => Promise.reject(error) })

describe('AdminOrganizationTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.organizations = []
    mocks.state.user.value.organization = { organization: 1 }
    mocks.deleteOrganization.mockReturnValue(ok())
  })

  it('should take a snapshot', () => {
    const { container } = renderComponent()
    expect(replaceChaoticIds(container)).toMatchSnapshot()
  })

  it('refreshes organization and related data caches', () => {
    renderComponent()
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(mocks.dispatch).toHaveBeenCalledTimes(4)
    const actions = mocks.dispatch.mock.calls.map(([action]) => action)
    expect(actions.map((action) => action.payload)).toEqual([
      ['Organization', 'RsuList', 'AvailableRsuList', 'Rsu', 'UserList', 'AvailableUserList', 'User'],
      [
        { type: 'AdminIntersection', id: 'LIST' },
        { type: 'AdminIntersection', id: 'AVAILABLE_LIST' },
      ],
      [{ type: 'User', id: 'LIST' }],
      [{ type: 'Rsu', id: 'LIST' }],
    ])
  })

  describe('organization deletion', () => {
    const confirmDeletion = async () => {
      fireEvent.click(screen.getByTitle('Delete Organization'))
      const options = mocks.confirmAlert.mock.calls.at(-1)![0]
      await act(async () => {
        await options.buttons.find((button: any) => button.label === 'Yes').onClick()
      })
      return options
    }

    it('deletes the organization, invalidates affected caches, and selects another organization', async () => {
      mocks.organizations = [
        { id: 1, name: 'Alpha' },
        { id: 2, name: 'Beta' },
      ]
      renderComponent()

      const options = await confirmDeletion()

      expect(options.title).toBe('Delete Organization')
      expect(options.message).toBe('Are you sure you want to delete the "Alpha" organization?')
      expect(mocks.deleteOrganization).toHaveBeenCalledWith(1)
      expect(mocks.invalidateCaches).toHaveBeenCalledWith(mocks.dispatch)
      expect(mocks.dispatch).toHaveBeenCalledWith({
        type: 'user/deleteOrgAssociationByOrgId',
        payload: 1,
      })
      expect(await screen.findByTestId('selected-organization')).toHaveTextContent('2')
      expect(mocks.toast.success).toHaveBeenCalledWith('Successfully deleted organization Alpha', { id: 'toast-id' })
      expect(mocks.toast.error).not.toHaveBeenCalled()
    })

    it('shows an error and does not invalidate caches when deletion fails', async () => {
      mocks.organizations = [{ id: 1, name: 'Alpha' }]
      mocks.deleteOrganization.mockReturnValue(fail('delete failed'))
      renderComponent()

      await confirmDeletion()

      await waitFor(() => {
        expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete organization due to error: delete failed', {
          id: 'toast-id',
        })
      })
      expect(mocks.invalidateCaches).not.toHaveBeenCalled()
      expect(mocks.dispatch).not.toHaveBeenCalled()
      expect(mocks.toast.success).not.toHaveBeenCalled()
    })
  })
})

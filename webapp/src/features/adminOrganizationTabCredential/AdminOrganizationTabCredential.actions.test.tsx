import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { vi } from 'vitest'
import toast from 'react-hot-toast'
import AdminOrganizationTabCredential, { canViewOrganizationCredentials } from './AdminOrganizationTabCredential'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { useDeleteCredentialMutation, useListCredentialsQuery } from '../api/credentialApiSlice'

vi.mock('../api/credentialApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/credentialApiSlice')>()),
  useListCredentialsQuery: vi.fn(),
  useDeleteCredentialMutation: vi.fn(),
  useCreateCredentialMutation: () => [vi.fn(), { isLoading: false }],
  useUpdateCredentialMutation: () => [vi.fn(), { isLoading: false }],
}))

vi.mock('react-confirm-alert', () => ({
  confirmAlert: (options: { buttons: { label: string; onClick: () => void }[] }) => options.buttons[0].onClick(),
}))

// Renders each row's visible actions and the toolbar actions as plain buttons
vi.mock('../../components/AdminTable', () => ({
  default: ({ title, actions, data }: any) => (
    <div>
      {data.map((row: any) => (
        <div key={row.nickname}>
          {actions
            .filter((action: any) => typeof action === 'function')
            .map((action: any) => action(row))
            .filter((action: any) => !action.hidden)
            .map((action: any) => (
              <button key={action.tooltip} onClick={() => action.onClick(null, row)}>
                {`${action.tooltip}: ${row.nickname}`}
              </button>
            ))}
        </div>
      ))}
      {actions
        .filter((action: any) => typeof action !== 'function')
        .map((action: any) => (
          <button key={action.iconProps.title} onClick={action.onClick}>
            {`${title}: ${action.iconProps.title}`}
          </button>
        ))}
    </div>
  ),
}))

const authState = (organizations: { name: string; role: string }[], superUser = false) =>
  ({
    user: {
      loading: false,
      value: {
        authLoginData: {
          token: 'testToken',
          data: {
            super_user: superUser,
            organizations: organizations.map((org, i) => ({ organization: i + 1, ...org })),
          },
        },
        organization: organizations[0] ? { organization: 1, ...organizations[0] } : undefined,
      },
    },
  }) as any

const renderSection = (preloadedState: any, selectedOrg = 'OrgA') =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore(preloadedState)}>
        <AdminOrganizationTabCredential selectedOrg={selectedOrg} />
      </Provider>
    </ThemeProvider>
  )

const deleteCredential = vi.fn()
const refetch = vi.fn()

beforeEach(() => {
  vi.clearAllMocks()
  deleteCredential.mockReturnValue({ unwrap: () => Promise.resolve() })
  vi.mocked(useDeleteCredentialMutation).mockReturnValue([deleteCredential, { isLoading: false }] as any)
  vi.mocked(useListCredentialsQuery).mockImplementation(
    ({ type }: any) =>
      ({
        data:
          type === 'rsu'
            ? [
                { nickname: 'owned-ssh', username: 'admin', ownerOrganization: 'OrgA', rsuIps: ['10.0.0.1'] },
                { nickname: 'foreign-ssh', username: 'root', ownerOrganization: 'OrgB', rsuIps: ['10.0.0.9'] },
              ]
            : [],
        isFetching: false,
        refetch,
      }) as any
  )
})

describe('visibility', () => {
  it('is not rendered for a user who is not an admin of the selected organization', () => {
    const { container } = renderSection(authState([{ name: 'OrgA', role: 'OPERATOR' }]))

    expect(container).toBeEmptyDOMElement()
    expect(useListCredentialsQuery).not.toHaveBeenCalled()
  })

  it('is not rendered for an admin of a different organization', () => {
    const { container } = renderSection(authState([{ name: 'OrgB', role: 'ADMIN' }]))

    expect(container).toBeEmptyDOMElement()
    expect(useListCredentialsQuery).not.toHaveBeenCalled()
  })

  it('is rendered for an admin of the selected organization', () => {
    renderSection(authState([{ name: 'OrgA', role: 'admin' }]))

    expect(screen.getByText('RSU Credentials')).toBeInTheDocument()
    expect(useListCredentialsQuery).toHaveBeenCalledWith({ type: 'rsu', organization: 'OrgA' }, expect.anything())
    expect(useListCredentialsQuery).toHaveBeenCalledWith({ type: 'snmp', organization: 'OrgA' }, expect.anything())
  })

  it('is rendered for a super user who is not a member of the selected organization', () => {
    renderSection(authState([], true))

    expect(screen.getByText('RSU Credentials')).toBeInTheDocument()
  })

  it('canViewOrganizationCredentials handles missing login data and organization', () => {
    expect(canViewOrganizationCredentials(null, 'OrgA')).toBe(false)
    expect(canViewOrganizationCredentials(authState([], true).user.value.authLoginData, '')).toBe(false)
  })
})

describe('actions', () => {
  it('only offers edit and delete for credentials the selected organization owns', () => {
    renderSection(authState([{ name: 'OrgA', role: 'ADMIN' }]))

    expect(screen.getByText('Edit SSH Credential: owned-ssh')).toBeInTheDocument()
    expect(screen.getByText('Delete SSH Credential: owned-ssh')).toBeInTheDocument()
    expect(screen.queryByText('Edit SSH Credential: foreign-ssh')).not.toBeInTheDocument()
    expect(screen.queryByText('Delete SSH Credential: foreign-ssh')).not.toBeInTheDocument()
  })

  it('lets a super user edit and delete every credential, whichever organization owns it', () => {
    renderSection(authState([], true), 'OrgB')

    expect(screen.getByText('Edit SSH Credential: owned-ssh')).toBeInTheDocument()
    expect(screen.getByText('Delete SSH Credential: owned-ssh')).toBeInTheDocument()
    expect(screen.getByText('Edit SSH Credential: foreign-ssh')).toBeInTheDocument()
    expect(screen.getByText('Delete SSH Credential: foreign-ssh')).toBeInTheDocument()
  })

  it('keeps a credential read-only for an admin of an organization that does not own it', () => {
    renderSection(authState([{ name: 'OrgB', role: 'ADMIN' }]), 'OrgB')

    expect(screen.queryByText('Edit SSH Credential: owned-ssh')).not.toBeInTheDocument()
    expect(screen.queryByText('Delete SSH Credential: owned-ssh')).not.toBeInTheDocument()
    expect(screen.getByText('Edit SSH Credential: foreign-ssh')).toBeInTheDocument()
  })

  it('deletes a credential after confirmation', async () => {
    const success = vi.spyOn(toast, 'success')
    renderSection(authState([{ name: 'OrgA', role: 'ADMIN' }]))

    fireEvent.click(screen.getByText('Delete SSH Credential: owned-ssh'))

    expect(deleteCredential).toHaveBeenCalledWith({ type: 'rsu', nickname: 'owned-ssh' })
    await waitFor(() => expect(success).toHaveBeenCalledWith('SSH Credential Deleted Successfully', expect.anything()))
  })

  it('shows the API error when a credential cannot be deleted', async () => {
    const error = vi.spyOn(toast, 'error')
    deleteCredential.mockReturnValue({
      unwrap: () =>
        Promise.reject({ data: { detail: 'This item cannot be deleted because it is being used by other records.' } }),
    })
    renderSection(authState([{ name: 'OrgA', role: 'ADMIN' }]))

    fireEvent.click(screen.getByText('Delete SSH Credential: owned-ssh'))

    await waitFor(() =>
      expect(error).toHaveBeenCalledWith(
        'Failed to delete SSH credential due to error: This item cannot be deleted because it is being used by other records.',
        expect.anything()
      )
    )
  })

  it('opens the add dialog for the table whose New button was clicked', () => {
    renderSection(authState([{ name: 'OrgA', role: 'ADMIN' }]))

    fireEvent.click(screen.getByText('SNMP Credentials: New'))

    expect(screen.getByText('Add SNMP Credential')).toBeInTheDocument()
  })

  it('opens the edit dialog prefilled from the row, without a password', () => {
    renderSection(authState([{ name: 'OrgA', role: 'ADMIN' }]))

    fireEvent.click(screen.getByText('Edit SSH Credential: owned-ssh'))

    expect(screen.getByText('Edit SSH Credential')).toBeInTheDocument()
    expect(screen.getByLabelText(/Nickname/)).toHaveValue('owned-ssh')
    expect(screen.getByLabelText(/Username/)).toHaveValue('admin')
    expect(screen.getByLabelText(/Password/)).toHaveValue('')
  })

  it('says so when the credentials cannot be loaded, instead of showing an empty list', () => {
    vi.mocked(useListCredentialsQuery).mockImplementation(
      ({ type }: any) =>
        ({
          data: undefined,
          isFetching: false,
          isError: type === 'rsu',
          error: type === 'rsu' ? { status: 500, data: { detail: 'No endpoint GET /credentials/rsu/list.' } } : undefined,
          refetch,
        }) as any
    )
    renderSection(authState([{ name: 'OrgA', role: 'ADMIN' }]))

    expect(
      screen.getByText('Failed to load SSH credentials: No endpoint GET /credentials/rsu/list.')
    ).toBeInTheDocument()
    expect(screen.queryByText(/Failed to load SNMP credentials/)).not.toBeInTheDocument()
  })

  it('refreshes the list', () => {
    renderSection(authState([{ name: 'OrgA', role: 'ADMIN' }]))

    fireEvent.click(screen.getByText('SSH Credentials: Refresh'))

    expect(refetch).toHaveBeenCalled()
  })
})

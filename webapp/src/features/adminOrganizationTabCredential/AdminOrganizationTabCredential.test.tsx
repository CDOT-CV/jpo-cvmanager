import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { vi } from 'vitest'
import AdminOrganizationTabCredential from './AdminOrganizationTabCredential'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { useListCredentialsQuery } from '../api/credentialApiSlice'

vi.mock('../api/credentialApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/credentialApiSlice')>()),
  useListCredentialsQuery: vi.fn(),
  useDeleteCredentialMutation: () => [vi.fn(), { isLoading: false }],
  useCreateCredentialMutation: () => [vi.fn(), { isLoading: false }],
  useUpdateCredentialMutation: () => [vi.fn(), { isLoading: false }],
}))

const adminOfOrgA = {
  user: {
    loading: false,
    value: {
      authLoginData: {
        token: 'testToken',
        data: { super_user: false, organizations: [{ organization: 1, name: 'OrgA', role: 'ADMIN' }] },
      },
      organization: { organization: 1, name: 'OrgA', role: 'ADMIN' },
    },
  },
} as any

beforeEach(() => {
  vi.mocked(useListCredentialsQuery).mockImplementation(
    ({ type }: any) =>
      ({
        data:
          type === 'rsu'
            ? [
                { nickname: 'owned-ssh', username: 'admin', ownerOrganization: 'OrgA', rsuIps: ['10.0.0.1', '10.0.0.2'] },
                { nickname: 'foreign-ssh', username: 'root', ownerOrganization: 'OrgB', rsuIps: ['10.0.0.9'] },
              ]
            : [{ nickname: 'spare-snmp', username: 'snmpuser', ownerOrganization: 'OrgA', rsuIps: [] }],
        isFetching: false,
        refetch: vi.fn(),
      }) as any
  )
})

const renderSection = () =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore(adminOfOrgA)}>
        <AdminOrganizationTabCredential selectedOrg="OrgA" />
      </Provider>
    </ThemeProvider>
  )

it('should take a snapshot', () => {
  const { container } = renderSection()

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('offers edit and delete only on credentials owned by the selected organization', () => {
  renderSection()

  // Both SSH credentials are listed, but only the one OrgA owns can be managed
  expect(screen.getByText('owned-ssh')).toBeInTheDocument()
  expect(screen.getByText('foreign-ssh')).toBeInTheDocument()
  expect(screen.getByText('OrgB')).toBeInTheDocument()
  expect(screen.getAllByLabelText('Edit SSH Credential')).toHaveLength(1)
  expect(screen.getAllByLabelText('Delete SSH Credential')).toHaveLength(1)
  expect(screen.getAllByLabelText('Edit SNMP Credential')).toHaveLength(1)
  expect(screen.getAllByLabelText('Delete SNMP Credential')).toHaveLength(1)
})

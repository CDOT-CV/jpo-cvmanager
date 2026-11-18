import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import AdminAddOrganization from './AdminAddOrganization'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { BrowserRouter, Route, Routes } from 'react-router-dom'

const mocks = vi.hoisted(() => ({
  createOrganization: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
  navigate: vi.fn(),
  updateToken: vi.fn(),
  authLogin: vi.fn(),
}))

vi.mock('../../keycloak-config', () => ({
  default: {
    updateToken: mocks.updateToken,
    get token() {
      return 'refreshed-token'
    },
  },
}))

vi.mock('../../apis/auth-api', () => ({
  default: {
    logIn: mocks.authLogin,
  },
}))

vi.mock('../api/organizationApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/organizationApiSlice')>()),
  useCreateOrganizationMutation: () => [mocks.createOrganization, { isLoading: false }],
}))

vi.mock('react-hot-toast', () => ({
  default: {
    success: mocks.toastSuccess,
    error: mocks.toastError,
  },
}))

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mocks.navigate,
}))

const renderComponent = () =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <BrowserRouter>
          <Routes>
            <Route path="*" element={<AdminAddOrganization />} />
          </Routes>
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

beforeEach(() => {
  mocks.createOrganization.mockReset()
  mocks.toastSuccess.mockReset()
  mocks.toastError.mockReset()
  mocks.navigate.mockReset()
  mocks.updateToken.mockReset().mockResolvedValue(true)
  mocks.authLogin.mockReset().mockResolvedValue({
    token: 'refreshed-token',
    expires_at: Date.now() + 60_000,
    data: { organizations: [{ organization: 123, name: 'New Organization', role: 'ADMIN' }] },
  })
})

it('should take a snapshot', () => {
  const { container } = renderComponent()

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

describe('create organization', () => {
  const fillForm = () => {
    fireEvent.change(screen.getByPlaceholderText('Enter Organization Name'), {
      target: { value: 'New Organization' },
    })
    fireEvent.change(screen.getByPlaceholderText('Enter Organization Email'), {
      target: { value: 'new@example.com' },
    })
    fireEvent.submit(document.getElementById('add-organization-form')!)
  }

  it('submits the form and closes on success', async () => {
    mocks.createOrganization.mockReturnValue({ unwrap: () => Promise.resolve(123) })
    renderComponent()

    fillForm()

    await waitFor(() => expect(mocks.createOrganization).toHaveBeenCalledWith({
      name: 'New Organization',
      email: 'new@example.com',
    }))
    expect(mocks.toastSuccess).toHaveBeenCalledWith('Organization Creation is successful.')
    expect(mocks.toastError).not.toHaveBeenCalled()
    expect(mocks.navigate).toHaveBeenCalledWith('/dashboard/admin/organizations')
    expect(mocks.updateToken).toHaveBeenCalledWith(-1)
    expect(mocks.authLogin).toHaveBeenCalledWith('refreshed-token')
  })

  it('shows API errors and keeps the form open', async () => {
    mocks.createOrganization.mockReturnValue({
      unwrap: () => Promise.reject({ data: { detail: 'Organization already exists' } }),
    })
    renderComponent()

    fillForm()

    await waitFor(() =>
      expect(mocks.toastError).toHaveBeenCalledWith(
        'Failed to add organization due to error: Organization already exists'
      )
    )
    expect(screen.getByPlaceholderText('Enter Organization Name')).toBeInTheDocument()
    expect(mocks.toastSuccess).not.toHaveBeenCalled()
  })

  it('keeps the successful creation result when session refresh fails', async () => {
    mocks.createOrganization.mockReturnValue({ unwrap: () => Promise.resolve(123) })
    mocks.updateToken.mockRejectedValue(new Error('refresh failed'))
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    renderComponent()

    fillForm()

    await waitFor(() =>
      expect(mocks.toastError).toHaveBeenCalledWith(
        'Organization created, but your session could not be refreshed. Please sign out and sign in again.'
      )
    )
    expect(mocks.toastSuccess).toHaveBeenCalledWith('Organization Creation is successful.')
    expect(mocks.navigate).toHaveBeenCalledWith('/dashboard/admin/organizations')
    expect(mocks.authLogin).not.toHaveBeenCalled()
    consoleError.mockRestore()
  })
})

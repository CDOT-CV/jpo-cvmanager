import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminEditUser from './AdminEditUser'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { BrowserRouter } from 'react-router-dom'

const mocks = vi.hoisted(() => ({
  userInfo: null as any,
  allowedSelections: null as any,
  isLoading: true,
  toastError: vi.fn(),
}))

vi.mock('react-hot-toast', () => ({
  default: {
    error: mocks.toastError,
    success: vi.fn(),
  },
}))

vi.mock('../api/userApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/userApiSlice')>()),
  useGetUserQuery: () => ({ data: mocks.userInfo, isLoading: mocks.isLoading }),
  useGetUserAllowedSelectionsQuery: () => ({ data: mocks.allowedSelections, isLoading: mocks.isLoading }),
  usePatchUserMutation: () => [vi.fn(), { isLoading: false }],
}))

const renderComponent = () =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <BrowserRouter>
          <AdminEditUser />
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

const loadUser = (organizations = [
  { organization: 'Alpha', role: 'USER' },
  { organization: 'Beta', role: 'ADMIN' },
]) => {
  mocks.isLoading = false
  mocks.userInfo = {
    email: 'user@example.com',
    first_name: 'Test',
    last_name: 'User',
    super_user: false,
    organizations,
  }
  mocks.allowedSelections = {
    organizations: ['Alpha', 'Beta', 'Gamma'],
    roles: ['USER', 'ADMIN'],
  }
}

const openSelect = (index: number) => {
  fireEvent.mouseDown(screen.getAllByRole('combobox')[index])
  return screen.getByRole('listbox')
}

describe('AdminEditUser', () => {
  beforeEach(() => {
    mocks.userInfo = null
    mocks.allowedSelections = null
    mocks.isLoading = true
    mocks.toastError.mockReset()
  })

  it('should take a snapshot', () => {
    const { container } = renderComponent()
    expect(replaceChaoticIds(container)).toMatchSnapshot()
  })

  it('removes an organization while keeping at least one organization selected', async () => {
    loadUser()
    renderComponent()

    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(4))
    fireEvent.click(screen.getAllByRole('button', { name: 'delete' })[0])

    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(2))
    expect(screen.getAllByRole('combobox')[0]).toHaveTextContent('Beta')
    expect(screen.queryByRole('button', { name: 'delete' })).not.toBeInTheDocument()
    expect(mocks.toastError).not.toHaveBeenCalled()
  })

  it('changes the selected organization and role', async () => {
    loadUser()
    renderComponent()

    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(4))
    fireEvent.mouseDown(screen.getAllByRole('combobox')[2])
    fireEvent.click(await screen.findByRole('option', { name: 'Gamma' }))

    expect(screen.getAllByRole('combobox')[2]).toHaveTextContent('Gamma')

    fireEvent.mouseDown(screen.getAllByRole('combobox')[1])
    fireEvent.click(await screen.findByRole('option', { name: 'ADMIN' }))

    expect(screen.getAllByRole('combobox')[1]).toHaveTextContent('ADMIN')
    expect(mocks.toastError).not.toHaveBeenCalled()
  })

  it('only offers organizations not selected in another row', async () => {
    loadUser()
    renderComponent()

    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(4))
    const firstOrganizationOptions = within(openSelect(0))
    expect(firstOrganizationOptions.getByRole('option', { name: 'Alpha' })).toBeInTheDocument()
    expect(firstOrganizationOptions.getByRole('option', { name: 'Gamma' })).toBeInTheDocument()
    expect(firstOrganizationOptions.queryByRole('option', { name: 'Beta' })).not.toBeInTheDocument()

    fireEvent.keyDown(screen.getByRole('listbox'), { key: 'Escape' })
    const secondOrganizationOptions = within(openSelect(2))
    expect(secondOrganizationOptions.getByRole('option', { name: 'Beta' })).toBeInTheDocument()
    expect(secondOrganizationOptions.getByRole('option', { name: 'Gamma' })).toBeInTheDocument()
    expect(secondOrganizationOptions.queryByRole('option', { name: 'Alpha' })).not.toBeInTheDocument()
  })
})

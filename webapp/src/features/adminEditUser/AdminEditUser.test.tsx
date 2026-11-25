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
  toastSuccess: vi.fn(),
  patchUser: vi.fn(),
  navigate: vi.fn(),
}))

vi.mock('react-hot-toast', () => ({
  default: {
    error: mocks.toastError,
    success: mocks.toastSuccess,
  },
}))

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mocks.navigate,
}))

vi.mock('../api/userApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/userApiSlice')>()),
  useGetUserQuery: () => ({ data: mocks.userInfo, isLoading: mocks.isLoading }),
  useGetUserAllowedSelectionsQuery: () => ({ data: mocks.allowedSelections, isLoading: mocks.isLoading }),
  usePatchUserMutation: () => [mocks.patchUser, { isLoading: false }],
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

const loadNumericUser = (organizations = [
  { organization: 101, role: 'USER' },
  { organization: 202, role: 'ADMIN' },
]) => {
  mocks.isLoading = false
  mocks.userInfo = {
    email: 'user@example.com',
    first_name: 'Test',
    last_name: 'User',
    super_user: false,
    organizations,
  }
  mocks.allowedSelections = { organizations: [101, 202, 303], roles: ['USER', 'ADMIN'] }
}

const submitForm = () => {
  const form = document.getElementById('edit-user-form')
  expect(form).not.toBeNull()
  fireEvent.submit(form!)
}

describe('AdminEditUser', () => {
  beforeEach(() => {
    mocks.userInfo = null
    mocks.allowedSelections = null
    mocks.isLoading = true
    mocks.toastError.mockReset()
    mocks.toastSuccess.mockReset()
    mocks.navigate.mockReset()
    mocks.patchUser.mockReset()
    mocks.patchUser.mockReturnValue({ unwrap: () => Promise.resolve() })
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

  it('adds an empty organization row with default USER role and disables adding beyond the available count', async () => {
    loadNumericUser()
    renderComponent()

    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(4))
    fireEvent.click(screen.getByRole('button', { name: 'Add Organization' }))
    const selects = screen.getAllByRole('combobox')
    expect(selects).toHaveLength(6)
    expect(selects[5]).toHaveTextContent('USER')
    expect(selects[5]).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByRole('button', { name: 'Add Organization' })).toBeDisabled()
    const options = within(openSelect(4))
    expect(options.getByRole('option', { name: '303' })).toBeInTheDocument()
    expect(options.queryByRole('option', { name: '101' })).not.toBeInTheDocument()
    expect(options.queryByRole('option', { name: '202' })).not.toBeInTheDocument()
  })

  it('rejects submission when a newly added organization has not been selected', async () => {
    loadNumericUser()
    renderComponent()
    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(4))
    fireEvent.click(screen.getByRole('button', { name: 'Add Organization' }))
    submitForm()

    await waitFor(() => expect(mocks.toastError).toHaveBeenCalledWith('Must select at least one organization'))
    expect(mocks.patchUser).not.toHaveBeenCalled()
    expect(mocks.navigate).not.toHaveBeenCalled()
  })

  it('rejects submission when the user has no organizations', async () => {
    loadNumericUser([])
    renderComponent()
    await screen.findByDisplayValue('user@example.com')
    expect(screen.getByRole('button', { name: 'Apply Changes' })).toBeDisabled()
    submitForm()

    await waitFor(() => expect(mocks.toastError).toHaveBeenCalledWith('Must select at least one organization'))
    expect(mocks.patchUser).not.toHaveBeenCalled()
    expect(mocks.navigate).not.toHaveBeenCalled()
  })

  it('sends changed fields and added, removed, and modified organizations on successful submission', async () => {
    loadNumericUser()
    renderComponent()
    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(4))

    fireEvent.change(screen.getByPlaceholderText('Enter User Email'), { target: { value: 'updated@example.com' } })
    fireEvent.change(screen.getByPlaceholderText('Enter First Name'), { target: { value: 'Updated' } })
    fireEvent.mouseDown(screen.getAllByRole('combobox')[1])
    fireEvent.click(screen.getByRole('option', { name: 'ADMIN' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'delete' })[1])
    fireEvent.click(screen.getByRole('button', { name: 'Add Organization' }))
    fireEvent.mouseDown(screen.getAllByRole('combobox')[2])
    fireEvent.click(screen.getByRole('option', { name: '303' }))
    submitForm()

    await waitFor(() =>
      expect(mocks.patchUser).toHaveBeenCalledWith({
        email: 'user@example.com',
        patch: {
          email: 'updated@example.com',
          first_name: 'Updated',
          organizations_to_add: [{ organization: 303, role: 'USER' }],
          organizations_to_remove: [202],
          organizations_to_modify: [{ organization: 101, role: 'ADMIN' }],
        },
      })
    )
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/dashboard/admin/users'))
    expect(mocks.toastSuccess).toHaveBeenCalledWith('User updated successfully')
    expect(mocks.toastError).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('submits an empty patch when nothing changed', async () => {
    loadNumericUser()
    renderComponent()
    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(4))
    submitForm()

    await waitFor(() =>
      expect(mocks.patchUser).toHaveBeenCalledWith({ email: 'user@example.com', patch: {} })
    )
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/dashboard/admin/users'))
  })

  it.each([
    [{ data: { message: 'Cannot update user' } }, 'Cannot update user'],
    [{ message: 'Network error' }, 'Network error'],
    [{ data: { detail: 'Details from server' } }, 'Details from server'],
    [{}, 'Unknown error'],
  ])('reports patch errors and keeps the dialog open', async (error, message) => {
    loadNumericUser()
    mocks.patchUser.mockReturnValue({ unwrap: () => Promise.reject(error) })
    renderComponent()
    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(4))
    submitForm()

    await waitFor(() => expect(mocks.toastError).toHaveBeenCalledWith(`Failed to update user: ${message}`))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(mocks.toastSuccess).not.toHaveBeenCalled()
    expect(mocks.navigate).not.toHaveBeenCalled()
  })
})

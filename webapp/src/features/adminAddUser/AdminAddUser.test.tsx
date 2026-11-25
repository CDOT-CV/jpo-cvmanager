import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminAddUser from './AdminAddUser'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { BrowserRouter, Routes, Route } from 'react-router-dom'

const mocks = vi.hoisted(() => ({
  allowedSelections: null as any,
  isLoading: true,
  isSuperUser: true,
  createUser: vi.fn(),
  navigate: vi.fn(),
  toastError: vi.fn(),
  toastSuccess: vi.fn(),
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
  useGetUserAllowedSelectionsQuery: () => ({
    data: mocks.allowedSelections,
    isLoading: mocks.isLoading,
  }),
  useCreateUserMutation: () => [mocks.createUser, { isLoading: false }],
}))

const renderComponent = () =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          user: {
            loading: false,
            value: {
              authLoginData: {
                data: {
                  super_user: mocks.isSuperUser,
                  organizations: [
                    { organization: 101, name: 'Org One' },
                    { organization: 202, name: 'Org Two' },
                    { organization: 303, name: 'Org Three' },
                  ],
                },
              },
              organization: null,
              loginFailure: false,
              loginMessage: '',
              routeNotFound: false,
            },
          },
        })}
      >
        <BrowserRouter>
          <Routes>
            <Route path="*" element={<AdminAddUser />} />
          </Routes>
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

const loadSelections = () => {
  mocks.isLoading = false
  mocks.allowedSelections = {
    organizations: [101, 202, 303],
    roles: ['USER', 'ADMIN'],
  }
}

const chooseOption = async (select: HTMLElement, option: string) => {
  fireEvent.mouseDown(select)
  fireEvent.click(await screen.findByRole('option', { name: option }))
}

const fillUserDetails = () => {
  fireEvent.change(screen.getByPlaceholderText('Enter User Email'), {
    target: { value: 'new.user@example.com' },
  })
  fireEvent.change(screen.getByPlaceholderText('Enter First Name'), { target: { value: 'New' } })
  fireEvent.change(screen.getByPlaceholderText('Enter Last Name'), { target: { value: 'User' } })
}

it('should take a snapshot', () => {
  const { container } = renderComponent()
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

describe('AdminAddUser', () => {
  beforeEach(() => {
    mocks.allowedSelections = null
    mocks.isLoading = true
    mocks.isSuperUser = true
    mocks.createUser.mockReset()
    mocks.createUser.mockReturnValue({ unwrap: () => Promise.resolve(undefined) })
    mocks.navigate.mockReset()
    mocks.toastError.mockReset()
    mocks.toastSuccess.mockReset()
  })

  it('removes an organization and prevents removing the last one', async () => {
    loadSelections()
    renderComponent()

    await screen.findByText('Organizations & Roles')
    const organizationSelects = screen.getAllByRole('combobox')
    await chooseOption(organizationSelects[0], 'Org One')

    fireEvent.click(screen.getByRole('button', { name: 'Add Organization' }))
    const selectsWithSecondOrganization = screen.getAllByRole('combobox')
    await chooseOption(selectsWithSecondOrganization[2], 'Org Two')

    fireEvent.click(screen.getAllByRole('button', { name: 'delete' })[1])

    expect(screen.getAllByRole('combobox')).toHaveLength(2)
    expect(screen.getAllByRole('combobox')[0]).toHaveTextContent('Org One')
    expect(screen.queryByRole('button', { name: 'delete' })).not.toBeInTheDocument()
    expect(mocks.toastError).not.toHaveBeenCalled()
  })

  it('changes organization and role selections', async () => {
    loadSelections()
    renderComponent()

    await screen.findByText('Organizations & Roles')
    await chooseOption(screen.getAllByRole('combobox')[0], 'Org One')
    fireEvent.click(screen.getByRole('button', { name: 'Add Organization' }))

    const selects = screen.getAllByRole('combobox')
    await chooseOption(selects[2], 'Org Two')
    await chooseOption(screen.getAllByRole('combobox')[3], 'ADMIN')

    expect(screen.getAllByRole('combobox')[0]).toHaveTextContent('Org One')
    expect(screen.getAllByRole('combobox')[1]).toHaveTextContent('USER')
    expect(screen.getAllByRole('combobox')[2]).toHaveTextContent('Org Two')
    expect(screen.getAllByRole('combobox')[3]).toHaveTextContent('ADMIN')
    expect(mocks.toastError).not.toHaveBeenCalled()
  })

  it('offers only organizations not selected in another row', async () => {
    loadSelections()
    renderComponent()

    await screen.findByText('Organizations & Roles')
    await chooseOption(screen.getAllByRole('combobox')[0], 'Org One')
    fireEvent.click(screen.getByRole('button', { name: 'Add Organization' }))

    fireEvent.mouseDown(screen.getAllByRole('combobox')[2])
    const options = within(await screen.findByRole('listbox'))
    expect(options.getByRole('option', { name: 'Org Two' })).toBeInTheDocument()
    expect(options.getByRole('option', { name: 'Org Three' })).toBeInTheDocument()
    expect(options.queryByRole('option', { name: 'Org One' })).not.toBeInTheDocument()
  })

  it('does not create a user until every organization row is selected', async () => {
    loadSelections()
    renderComponent()

    await screen.findByText('Organizations & Roles')
    await chooseOption(screen.getAllByRole('combobox')[0], 'Org One')
    fireEvent.click(screen.getByRole('button', { name: 'Add Organization' }))
    fillUserDetails()
    fireEvent.submit(document.getElementById('add-user-form')!)

    await waitFor(() => expect(mocks.toastError).toHaveBeenCalledWith('Must select at least one organization'))
    expect(mocks.createUser).not.toHaveBeenCalled()
    expect(mocks.navigate).not.toHaveBeenCalled()
  })

  it('creates a user with selected organization roles and closes on success', async () => {
    loadSelections()
    renderComponent()

    await screen.findByText('Organizations & Roles')
    await chooseOption(screen.getAllByRole('combobox')[0], 'Org One')
    await chooseOption(screen.getAllByRole('combobox')[1], 'ADMIN')
    fireEvent.click(screen.getByLabelText('Super User'))
    fillUserDetails()
    fireEvent.submit(document.getElementById('add-user-form')!)

    await waitFor(() =>
      expect(mocks.createUser).toHaveBeenCalledWith({
        email: 'new.user@example.com',
        first_name: 'New',
        last_name: 'User',
        super_user: true,
        organizations: [{ organization: 101, role: 'ADMIN' }],
      })
    )
    expect(mocks.toastSuccess).toHaveBeenCalledWith('User Created Successfully')
    expect(mocks.navigate).toHaveBeenCalledWith('/dashboard/admin/users')
    expect(mocks.toastError).not.toHaveBeenCalled()
  })

  it('reports the API error when user creation fails', async () => {
    loadSelections()
    mocks.createUser.mockReturnValue({
      unwrap: () => Promise.reject({ data: { detail: 'Email already exists' } }),
    })
    renderComponent()

    await screen.findByText('Organizations & Roles')
    await chooseOption(screen.getAllByRole('combobox')[0], 'Org One')
    fillUserDetails()
    fireEvent.submit(document.getElementById('add-user-form')!)

    await waitFor(() =>
      expect(mocks.toastError).toHaveBeenCalledWith('Failed to add User due to error: Email already exists')
    )
    expect(mocks.toastSuccess).not.toHaveBeenCalled()
    expect(mocks.navigate).not.toHaveBeenCalled()
  })
})

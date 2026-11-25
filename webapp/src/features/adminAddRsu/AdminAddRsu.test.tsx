import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, it, vi } from 'vitest'
import AdminAddRsu from './AdminAddRsu'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { BrowserRouter, Routes, Route } from 'react-router-dom'

const mocks = vi.hoisted(() => ({
  loading: true,
  allowed: {
    organizations: [101, 202, 303],
    primary_routes: [],
    rsu_models: [],
    ssh_credential_groups: [],
    snmp_credential_groups: [],
    snmp_version_groups: [],
  },
}))

vi.mock('../api/rsuApiSlice', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api/rsuApiSlice')>()
  return {
    ...original,
    useGetRsuAllowedSelectionsQuery: () =>
      mocks.loading ? original.useGetRsuAllowedSelectionsQuery() : { data: mocks.allowed, isLoading: false },
  }
})

beforeEach(() => {
  mocks.loading = true
})

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <BrowserRouter>
          <Routes>
            <Route path="*" element={<AdminAddRsu />} />
          </Routes>
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('shows allowed organizations by name or ID and supports multiple selections', async () => {
  mocks.loading = false
  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          user: {
            loading: false,
            value: {
              authLoginData: {
                data: {
                  organizations: [
                    { organization: 101, name: 'Org One' },
                    { organization: 202, name: 'Org Two' },
                    { organization: 404, name: 'Not Allowed' },
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
          <AdminAddRsu />
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

  const select = document.body.querySelector<HTMLElement>('#organizations')
  expect(select).not.toBeNull()
  fireEvent.mouseDown(select!)
  const options = within(await screen.findByRole('listbox'))
  expect(options.getByRole('option', { name: 'Org One' })).toHaveAttribute('aria-selected', 'false')
  expect(options.getByRole('option', { name: 'Org Two' })).toHaveAttribute('aria-selected', 'false')
  expect(options.getByRole('option', { name: '303' })).toBeInTheDocument()
  expect(options.queryByRole('option', { name: 'Not Allowed' })).not.toBeInTheDocument()

  fireEvent.click(options.getByRole('option', { name: 'Org One' }))
  fireEvent.click(options.getByRole('option', { name: 'Org Two' }))
  expect(options.getByRole('option', { name: 'Org One' })).toHaveAttribute('aria-selected', 'true')
  expect(options.getByRole('option', { name: 'Org Two' })).toHaveAttribute('aria-selected', 'true')
  expect(select).toHaveTextContent('Org One, Org Two')

  fireEvent.click(options.getByRole('option', { name: 'Org One' }))
  expect(options.getByRole('option', { name: 'Org One' })).toHaveAttribute('aria-selected', 'false')
  expect(select).toHaveTextContent('Org Two')
  expect(select).not.toHaveTextContent('Org One')
})

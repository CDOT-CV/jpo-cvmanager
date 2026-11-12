import { render, screen } from '@testing-library/react'
import AdminEditRsu from './AdminEditRsu'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { BrowserRouter, MemoryRouter, Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import { useGetRsuQuery } from '../api/rsuApiSlice'

// Passes through to the real hook unless a test overrides it
vi.mock('../api/rsuApiSlice', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/rsuApiSlice')>()
  return { ...actual, useGetRsuQuery: vi.fn(actual.useGetRsuQuery) }
})

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <BrowserRouter>
          <AdminEditRsu />
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

const renderEditPage = (ownerOrganization: string) => {
  vi.mocked(useGetRsuQuery).mockReturnValue({
    data: {
      ip: '192.0.2.10',
      geo_position: { latitude: 39.7, longitude: -105.0 },
      milepost: 1,
      organizations: ['Test Org'],
      owner_organization: ownerOrganization,
    },
    isLoading: false,
  } as any)
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({ user: { value: { organization: { name: 'Test Org' } } } })}>
        <MemoryRouter initialEntries={['/editRsu/192.0.2.10']}>
          <Routes>
            <Route path="/editRsu/:rsuIp" element={<AdminEditRsu />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    </ThemeProvider>
  )
}

describe('AdminEditRsu ownership guard', () => {
  afterEach(() => {
    vi.mocked(useGetRsuQuery).mockReset()
  })

  it('blocks the edit form for an RSU owned by another organization', () => {
    renderEditPage('Owner Org')
    expect(screen.getByText(/You do not have permission to edit this RSU/)).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows the edit dialog for an RSU owned by the current organization', () => {
    renderEditPage('Test Org')
    expect(screen.queryByText(/You do not have permission to edit this RSU/)).not.toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})

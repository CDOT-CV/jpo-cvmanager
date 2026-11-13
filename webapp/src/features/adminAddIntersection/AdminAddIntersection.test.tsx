import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminAddIntersection from './AdminAddIntersection'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { BrowserRouter, Routes, Route } from 'react-router-dom'

const mocks = vi.hoisted(() => ({
  selections: { organizations: [101, 202], rsus: ['192.0.2.1/32', '192.0.2.2/32'] },
  createIntersection: vi.fn(),
  navigate: vi.fn(),
  error: vi.fn(),
  success: vi.fn(),
}))

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mocks.navigate,
}))

vi.mock('react-hot-toast', () => ({
  default: { error: mocks.error, success: mocks.success },
}))

vi.mock('../api/adminIntersectionApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/adminIntersectionApiSlice')>()),
  useGetIntersectionAllowedSelectionsQuery: () => ({ data: mocks.selections }),
  useCreateIntersectionMutation: () => [mocks.createIntersection],
}))

const renderComponent = () =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <BrowserRouter>
          <Routes>
            <Route path="*" element={<AdminAddIntersection />} />
          </Routes>
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

it('should take a snapshot', () => {
  const { container } = renderComponent()
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

const fillForm = () => {
  fireEvent.change(screen.getByPlaceholderText('Enter Intersection ID'), { target: { value: '123' } })
  fireEvent.change(screen.getByPlaceholderText('Enter Reference Point Latitude'), { target: { value: '39.75' } })
  fireEvent.change(screen.getByPlaceholderText('Enter Reference Point Longitude'), { target: { value: '-104.99' } })
  fireEvent.change(screen.getByPlaceholderText('Enter Intersection Name'), { target: { value: 'Main Street' } })
  fireEvent.change(screen.getByPlaceholderText('Enter Origin IP'), { target: { value: '192.0.2.10' } })
}

const selectItems = (index: number, labels: string[]) => {
  fireEvent.mouseDown(screen.getAllByRole('combobox')[index])
  const listbox = within(screen.getByRole('listbox'))
  for (const label of labels) {
    fireEvent.click(listbox.getByRole('option', { name: label }))
  }
  fireEvent.keyDown(screen.getByRole('listbox'), { key: 'Escape' })
}

describe('AdminAddIntersection', () => {
  beforeEach(() => {
    mocks.createIntersection.mockReset()
    mocks.createIntersection.mockReturnValue({ unwrap: () => Promise.resolve() })
    mocks.navigate.mockReset()
    mocks.error.mockReset()
    mocks.success.mockReset()
  })

  it.each(['button', 'header'])('closes and navigates using the %s close action', async (action) => {
    renderComponent()
    const closeButton =
      action === 'button'
        ? screen.getByRole('button', { name: 'Close' })
        : screen.getByTestId('CloseIcon').closest('button')
    expect(closeButton).not.toBeNull()
    fireEvent.click(closeButton!)
    expect(mocks.navigate).toHaveBeenCalledWith('/dashboard/admin/intersections')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(mocks.createIntersection).not.toHaveBeenCalled()
  })

  it('requires an organization before creating an intersection', async () => {
    renderComponent()
    fillForm()
    fireEvent.submit(document.getElementById('add-intersection-form')!)

    await waitFor(() => expect(mocks.error).toHaveBeenCalledWith('Please fill out all required fields'))
    expect(screen.getByRole('alert')).toHaveTextContent('Must select an organization')
    expect(mocks.createIntersection).not.toHaveBeenCalled()
    expect(mocks.navigate).not.toHaveBeenCalled()
  })

  it('creates a numeric request with selected organizations and normalized RSUs', async () => {
    renderComponent()
    fillForm()
    selectItems(0, ['101', '202'])
    selectItems(1, ['192.0.2.1', '192.0.2.2'])
    fireEvent.submit(document.getElementById('add-intersection-form')!)

    await waitFor(() =>
      expect(mocks.createIntersection).toHaveBeenCalledWith({
        intersection_id: 123,
        ref_pt: { latitude: 39.75, longitude: -104.99 },
        intersection_name: 'Main Street',
        origin_ip: '192.0.2.10',
        organizations: [101, 202],
        rsus: ['192.0.2.1', '192.0.2.2'],
      })
    )
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/dashboard/admin/intersections'))
    expect(mocks.success).toHaveBeenCalledWith('Intersection created successfully')
    expect(mocks.error).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('submits an empty RSU list when no RSUs are selected', async () => {
    renderComponent()
    fillForm()
    selectItems(0, ['101'])
    fireEvent.submit(document.getElementById('add-intersection-form')!)

    await waitFor(() =>
      expect(mocks.createIntersection).toHaveBeenCalledWith({
        intersection_id: 123,
        ref_pt: { latitude: 39.75, longitude: -104.99 },
        intersection_name: 'Main Street',
        origin_ip: '192.0.2.10',
        organizations: [101],
        rsus: [],
      })
    )
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/dashboard/admin/intersections'))
  })

  it.each([
    [{ data: { detail: 'Duplicate ID' } }, 'Failed to add Intersection: Duplicate ID'],
    [{ message: 'Network error' }, 'Failed to add Intersection'],
  ])('reports a creation error and keeps the form open', async (error, message) => {
    mocks.createIntersection.mockReturnValue({ unwrap: () => Promise.reject(error) })
    renderComponent()
    fillForm()
    selectItems(0, ['101'])
    fireEvent.submit(document.getElementById('add-intersection-form')!)

    await waitFor(() => expect(mocks.error).toHaveBeenCalledWith(message))
    expect(mocks.success).not.toHaveBeenCalled()
    expect(mocks.navigate).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Enter Intersection ID')).toHaveValue('123')
  })
})

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminEditIntersection from './AdminEditIntersection'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { BrowserRouter } from 'react-router-dom'

const mocks = vi.hoisted(() => ({
  intersectionInfo: null as any,
  allowedSelections: null as any,
  isLoading: true,
  patchIntersection: vi.fn(),
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
  useParams: () => ({ intersectionId: '123' }),
}))

vi.mock('../api/adminIntersectionApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/adminIntersectionApiSlice')>()),
  useGetIntersectionQuery: () => ({ data: mocks.intersectionInfo, isLoading: mocks.isLoading }),
  useGetIntersectionAllowedSelectionsQuery: () => ({
    data: mocks.allowedSelections,
    isLoading: mocks.isLoading,
  }),
  usePatchIntersectionMutation: () => [mocks.patchIntersection, { isLoading: false }],
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
                  organizations: [{ organization: 101, name: 'Org One' }],
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
          <AdminEditIntersection />
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

const loadIntersection = (organizations = [101]) => {
  mocks.isLoading = false
  mocks.intersectionInfo = {
    intersection_id: '123',
    ref_pt: { latitude: '39.7', longitude: '-104.9' },
    intersection_name: 'Original Name',
    origin_ip: '10.0.0.1',
    organizations,
    rsus: ['RSU-1'],
  }
  mocks.allowedSelections = {
    organizations: [101],
    rsus: ['RSU-1'],
  }
}

it('should take a snapshot', () => {
  const { container } = renderComponent()
  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

describe('AdminEditIntersection', () => {
  beforeEach(() => {
    mocks.intersectionInfo = null
    mocks.allowedSelections = null
    mocks.isLoading = true
    mocks.patchIntersection.mockReset()
    mocks.patchIntersection.mockReturnValue({ unwrap: () => Promise.resolve(undefined) })
    mocks.navigate.mockReset()
    mocks.toastError.mockReset()
    mocks.toastSuccess.mockReset()
  })

  it('submits changed intersection fields and navigates after success', async () => {
    loadIntersection()
    renderComponent()

    await screen.findByDisplayValue('Original Name')
    fireEvent.change(screen.getByPlaceholderText('Enter Intersection ID'), { target: { value: '456' } })
    fireEvent.change(screen.getByPlaceholderText('Enter Intersection Name'), { target: { value: 'Updated Name' } })
    fireEvent.change(screen.getByPlaceholderText('Enter Origin IP'), { target: { value: '10.0.0.2' } })
    fireEvent.submit(document.getElementById('edit-intersection-form')!)

    await waitFor(() => {
      expect(mocks.patchIntersection).toHaveBeenCalledWith({
        intersection_id: '123',
        patch: {
          intersection_id: '456',
          intersection_name: 'Updated Name',
          origin_ip: '10.0.0.2',
        },
      })
    })
    expect(mocks.toastSuccess).toHaveBeenCalledWith('Intersection updated successfully')
    expect(mocks.navigate).toHaveBeenCalledWith('/dashboard/admin/intersections')
    expect(mocks.toastError).not.toHaveBeenCalled()
  })

  it('shows the server error when the patch fails', async () => {
    loadIntersection()
    mocks.patchIntersection.mockReturnValue({
      unwrap: () => Promise.reject({ data: { message: 'Patch failed' } }),
    })
    renderComponent()

    await screen.findByDisplayValue('Original Name')
    fireEvent.change(screen.getByPlaceholderText('Enter Intersection Name'), { target: { value: 'Updated Name' } })
    fireEvent.submit(document.getElementById('edit-intersection-form')!)

    await waitFor(() =>
      expect(mocks.toastError).toHaveBeenCalledWith('Failed to update intersection: Patch failed')
    )
    expect(mocks.toastSuccess).not.toHaveBeenCalled()
    expect(mocks.navigate).not.toHaveBeenCalled()
  })
})

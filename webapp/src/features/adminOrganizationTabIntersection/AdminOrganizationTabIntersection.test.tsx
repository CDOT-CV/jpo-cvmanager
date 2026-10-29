import { act, render, screen } from '@testing-library/react'
import { ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminOrganizationTabIntersection from './AdminOrganizationTabIntersection'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'

const mocks = vi.hoisted(() => ({
  tableProps: { current: null as any },
  patch: vi.fn(),
  fetchIntersection: vi.fn(),
  confirmAlert: vi.fn(),
  loadingGlobal: { value: false },
  toast: {
    loading: vi.fn(() => 'toast-id'),
    success: vi.fn(),
    error: vi.fn(),
    dismiss: vi.fn(),
  },
}))

vi.mock('react-hot-toast', () => ({ default: mocks.toast }))
vi.mock('react-confirm-alert', () => ({ confirmAlert: mocks.confirmAlert }))
vi.mock('../../components/AdminTable', () => ({
  default: (props: any) => {
    mocks.tableProps.current = props
    return <div data-testid="admin-table" />
  },
}))
vi.mock('../../generalSlices/userSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../generalSlices/userSlice')>()),
  selectLoadingGlobal: () => mocks.loadingGlobal.value,
}))
vi.mock('../api/organizationApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/organizationApiSlice')>()),
  usePatchOrganizationMutation: () => [mocks.patch],
}))
vi.mock('../api/adminIntersectionApiSlice', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/adminIntersectionApiSlice')>()),
  useGetIntersectionsNotInOrganizationQuery: () => ({
    data: {
      intersection_data: [
        { intersection_id: '30', intersection_name: 'Thirty' },
        { intersection_id: '40', intersection_name: 'Forty' },
      ],
    },
  }),
  useGetIntersectionsQuery: () => ({
    data: {
      intersection_data: [
        { intersection_id: '10', intersection_name: 'Ten' },
        { intersection_id: '20', intersection_name: 'Twenty' },
      ],
    },
  }),
  useLazyGetIntersectionQuery: () => [mocks.fetchIntersection],
}))

const ok = (value: any = undefined) => ({ unwrap: () => Promise.resolve(value) })
const fail = (err: any = 'boom') => ({ unwrap: () => Promise.reject(err) })
const intersectionResult = (orgCount: number) =>
  ok({ intersection_data: { organizations: Array.from({ length: orgCount }, (_, i) => ({ id: i })) } })
const row = (id: string) => ({ intersection_id: id, intersection_name: `Name ${id}` }) as any

const renderComponent = (orgName = 'Org') =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <AdminOrganizationTabIntersection selectedOrgId={1} selectedOrgName={orgName} />
      </Provider>
    </ThemeProvider>
  )

const actions = () => mocks.tableProps.current.actions as any[]

const confirmButton = (label: string) => {
  const options = mocks.confirmAlert.mock.calls.at(-1)![0]
  return { options, button: options.buttons.find((b: any) => b.label === label) }
}

// Triggers a table action and presses "Yes" in the resulting confirm dialog
const confirmYes = async (actionIndex: number, rowData: any) => {
  act(() => {
    actions()[actionIndex].onClick({}, rowData)
  })
  const { options, button } = confirmButton('Yes')
  await act(async () => {
    await button.onClick()
  })
  return options
}

const selectIntersections = (list: any[]) => {
  const multiselect = actions()[2].icon() as ReactElement<any>
  act(() => {
    multiselect.props.onChange(list)
  })
}

const pressAdd = async () => {
  await act(async () => {
    await actions()[3].onClick()
  })
}

describe('AdminOrganizationTabIntersection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.loadingGlobal.value = false
    mocks.patch.mockReturnValue(ok())
    mocks.fetchIntersection.mockReturnValue(intersectionResult(2))
    vi.spyOn(window, 'alert').mockImplementation(() => {})
  })

  it('should take a snapshot', () => {
    const { container } = renderComponent()
    expect(replaceChaoticIds(container)).toMatchSnapshot()
  })

  it('does not render the table while global loading', () => {
    mocks.loadingGlobal.value = true
    renderComponent()
    expect(screen.queryByTestId('admin-table')).toBeNull()
  })

  it('passes table data and columns to the table', () => {
    renderComponent()
    expect(mocks.tableProps.current.data).toHaveLength(2)
    expect(mocks.tableProps.current.columns.map((c: any) => c.field)).toEqual([
      'intersection_id',
      'intersection_name',
    ])
    expect(actions()).toHaveLength(4)
  })

  describe('delete buttons', () => {
    it('row delete with a single row builds the dialog', () => {
      renderComponent()
      act(() => {
        actions()[0].onClick({}, row('10'))
      })
      const options = mocks.confirmAlert.mock.calls[0][0]
      expect(options.title).toBe('Delete Intersection')
      expect(options.message).toBe('Are you sure you want to delete "10" from Org organization?')
    })

    it('row delete with an array lists all ids', () => {
      renderComponent()
      act(() => {
        actions()[0].onClick({}, [row('10'), row('20')])
      })
      expect(mocks.confirmAlert.mock.calls[0][0].message).toBe(
        'Are you sure you want to delete "10, 20" from Org organization?'
      )
    })

    it('toolbar delete with an array shows the count', () => {
      renderComponent()
      act(() => {
        actions()[1].onClick({}, [row('10'), row('20')])
      })
      const options = mocks.confirmAlert.mock.calls[0][0]
      expect(options.title).toBe('Delete Selected Intersections')
      expect(options.message).toBe('Are you sure you want to delete 2 Intersection(s) from Org organization?')
    })

    it('toolbar delete with a single row shows a count of 1', () => {
      renderComponent()
      act(() => {
        actions()[1].onClick({}, row('10'))
      })
      expect(mocks.confirmAlert.mock.calls[0][0].message).toBe(
        'Are you sure you want to delete 1 Intersection(s) from Org organization?'
      )
    })

    it.each([0, 1])('action %i: Yes with a single row runs intersectionOnDelete', async (index) => {
      renderComponent()
      await confirmYes(index, row('10'))
      expect(mocks.fetchIntersection).toHaveBeenCalledWith('10')
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, intersections_to_remove: [10] })
      expect(mocks.toast.success).toHaveBeenCalledWith('Intersection deleted successfully', { id: 'toast-id' })
    })

    it.each([0, 1])('action %i: Yes with an array runs intersectionMultiDelete', async (index) => {
      renderComponent()
      await confirmYes(index, [row('10'), row('20')])
      expect(mocks.fetchIntersection).toHaveBeenCalledTimes(2)
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, intersections_to_remove: [10, 20] })
      expect(mocks.toast.success).toHaveBeenCalledWith('Intersection(s) deleted successfully', { id: 'toast-id' })
    })

    it.each([0, 1])('action %i: No does nothing', async (index) => {
      renderComponent()
      act(() => {
        actions()[index].onClick({}, row('10'))
      })
      const { button } = confirmButton('No')
      await act(async () => {
        await button.onClick()
      })
      expect(mocks.fetchIntersection).not.toHaveBeenCalled()
      expect(mocks.patch).not.toHaveBeenCalled()
    })
  })

  describe('intersectionOnDelete', () => {
    it('shows a loading toast with the id', async () => {
      renderComponent()
      await confirmYes(0, row('10'))
      expect(mocks.toast.loading).toHaveBeenCalledWith('Deleting Intersection 10...')
    })

    it('alerts and does not patch when it belongs to only one organization', async () => {
      mocks.fetchIntersection.mockReturnValue(intersectionResult(1))
      renderComponent()
      await confirmYes(0, row('10'))
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(mocks.toast.dismiss).toHaveBeenCalledWith('toast-id')
      expect(window.alert).toHaveBeenCalledWith(
        'Cannot remove Intersection 10 from Org because it must belong to at least one organization.'
      )
    })

    it('treats a missing organizations list as invalid', async () => {
      mocks.fetchIntersection.mockReturnValue(ok({}))
      renderComponent()
      await confirmYes(0, row('10'))
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(window.alert).toHaveBeenCalled()
    })

    it('shows an error when the lookup fails', async () => {
      mocks.fetchIntersection.mockReturnValue(fail('lookup failed'))
      renderComponent()
      await confirmYes(0, row('10'))
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete Intersection due to error: lookup failed', {
        id: 'toast-id',
      })
    })

    it('shows an error when the patch fails', async () => {
      mocks.patch.mockReturnValue(fail('patch failed'))
      renderComponent()
      await confirmYes(0, row('10'))
      expect(mocks.toast.success).not.toHaveBeenCalled()
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete Intersection due to error: patch failed', {
        id: 'toast-id',
      })
    })
  })

  describe('intersectionMultiDelete', () => {
    it('shows a loading toast with the count', async () => {
      renderComponent()
      await confirmYes(0, [row('10'), row('20')])
      expect(mocks.toast.loading).toHaveBeenCalledWith('Deleting 2 Intersection(s)...')
    })

    it('blocks the whole deletion when all rows are invalid', async () => {
      mocks.fetchIntersection.mockReturnValue(intersectionResult(1))
      renderComponent()
      await confirmYes(0, [row('10'), row('20')])
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(mocks.toast.dismiss).toHaveBeenCalledWith('toast-id')
      expect(window.alert).toHaveBeenCalledWith(
        'Cannot remove Intersection(s) 10, 20 from Org because they must belong to at least one organization.'
      )
    })

    it('blocks the whole deletion when only some rows are invalid', async () => {
      mocks.fetchIntersection.mockImplementation((id: string) => (id === '20' ? intersectionResult(1) : intersectionResult(2)))
      renderComponent()
      await confirmYes(0, [row('10'), row('20')])
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(window.alert).toHaveBeenCalledWith(
        'Cannot remove Intersection(s) 20 from Org because they must belong to at least one organization.'
      )
    })

    it('shows an error when a lookup fails', async () => {
      mocks.fetchIntersection.mockReturnValue(fail('lookup failed'))
      renderComponent()
      await confirmYes(0, [row('10'), row('20')])
      expect(mocks.patch).not.toHaveBeenCalled()
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete Intersection(s) due to error: lookup failed', {
        id: 'toast-id',
      })
    })

    it('shows an error when the patch fails', async () => {
      mocks.patch.mockReturnValue(fail('patch failed'))
      renderComponent()
      await confirmYes(0, [row('10'), row('20')])
      expect(mocks.toast.success).not.toHaveBeenCalled()
      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to delete Intersection(s) due to error: patch failed', {
        id: 'toast-id',
      })
    })
  })

  describe('intersectionMultiAdd', () => {
    it('shows an error and does not patch when nothing is selected', async () => {
      renderComponent()
      await pressAdd()
      expect(mocks.toast.error).toHaveBeenCalledWith('Please select Intersections to add')
      expect(mocks.toast.loading).not.toHaveBeenCalled()
      expect(mocks.patch).not.toHaveBeenCalled()
    })

    it('adds the selected intersections and clears the selection', async () => {
      renderComponent()
      selectIntersections([row('30'), row('40')])
      expect((actions()[2].icon() as ReactElement<any>).props.value).toHaveLength(2)

      await pressAdd()

      expect(mocks.toast.loading).toHaveBeenCalledWith('Adding 2 Intersection(s)...')
      expect(mocks.patch).toHaveBeenCalledWith({ id: 1, intersections_to_add: [30, 40] })
      expect(mocks.toast.success).toHaveBeenCalledWith('Intersection(s) added successfully', { id: 'toast-id' })
      expect((actions()[2].icon() as ReactElement<any>).props.value).toHaveLength(0)
    })

    it('keeps the selection and shows an error when the patch fails', async () => {
      mocks.patch.mockReturnValue(fail('patch failed'))
      renderComponent()
      selectIntersections([row('30')])

      await pressAdd()

      expect(mocks.toast.error).toHaveBeenCalledWith('Failed to add Intersection(s) due to error: patch failed', {
        id: 'toast-id',
      })
      expect(mocks.toast.success).not.toHaveBeenCalled()
      expect((actions()[2].icon() as ReactElement<any>).props.value).toHaveLength(1)
    })

    it('clears the selection when the organization changes', () => {
      const ui = (name: string) => (
        <ThemeProvider theme={testTheme}>
          <Provider store={setupStore({})}>
            <AdminOrganizationTabIntersection selectedOrgId={1} selectedOrgName={name} />
          </Provider>
        </ThemeProvider>
      )
      const { rerender } = render(ui('Org'))
      selectIntersections([row('30')])
      expect((actions()[2].icon() as ReactElement<any>).props.value).toHaveLength(1)

      rerender(ui('Other'))
      expect((actions()[2].icon() as ReactElement<any>).props.value).toHaveLength(0)
    })
  })
})

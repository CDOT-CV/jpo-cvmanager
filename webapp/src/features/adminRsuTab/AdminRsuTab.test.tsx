import { render } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { BrowserRouter, MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { confirmAlert } from 'react-confirm-alert'
import AdminRsuTab from './AdminRsuTab'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('react-confirm-alert', () => ({ confirmAlert: vi.fn() }))

// Capture the props AdminRsuTab hands to the table (while still rendering it) so row actions can be exercised directly
let tableProps: any
vi.mock('../../components/AdminTable', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../components/AdminTable')>()
  return {
    ...actual,
    default: (props: any) => {
      tableProps = props
      return <actual.default {...props} />
    },
  }
})

const ORG = 'Owner Org'
const ownedRow = { ip: '10.0.0.1', owner_organization: ORG } as any
const viewerRow = { ip: '10.0.0.2', owner_organization: 'Other Org' } as any

const renderTab = (superUser = false) =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          user: { value: {authLoginData: { token: 'testToken', data: { super_user: superUser } }, organization: { name: ORG },},},
        })}
      >
        <MemoryRouter>
          <AdminRsuTab />
        </MemoryRouter>
      </Provider>
    </ThemeProvider>
  )

const resolveRowAction = (tooltip: string, row: any) =>
  tableProps.actions
    .filter((a: any) => typeof a.action === 'function')
    .map((a: any) => a.action(row))
    .find((a: any) => a.tooltip === tooltip)

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          adminRsuTab: { loading: false, value: { activeDiv: 'rsu_table' } },
          user: { value: { authLoginData: { token: 'testToken' } } },
        })}
      >
        <BrowserRouter>
          <AdminRsuTab />
        </BrowserRouter>
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

describe('AdminRsuTab owner-only row actions', () => {
  beforeEach(() => {
    mockNavigate.mockClear()
    vi.mocked(confirmAlert).mockClear()
    renderTab()
  })

  it('hides edit and delete for RSUs owned by another organization', () => {
    expect(resolveRowAction('Edit RSU', viewerRow).hidden).toBe(true)
    expect(resolveRowAction('Delete RSU', viewerRow).hidden).toBe(true)
    expect(tableProps.selectionProps(viewerRow)).toEqual({ disabled: true })
  })

  it('shows edit and delete for RSUs owned by the current organization', () => {
    expect(resolveRowAction('Edit RSU', ownedRow).hidden).toBe(false)
    expect(resolveRowAction('Delete RSU', ownedRow).hidden).toBe(false)
    expect(tableProps.selectionProps(ownedRow)).toEqual({ disabled: false })
  })

  it('navigates to the edit page when editing an owned RSU', () => {
    const edit = resolveRowAction('Edit RSU', ownedRow)
    render(edit.icon())
    edit.onClick(null, ownedRow)
    expect(mockNavigate).toHaveBeenCalledWith('editRsu/10.0.0.1')
  })

  it('asks for confirmation before deleting an owned RSU', () => {
    const del = resolveRowAction('Delete RSU', ownedRow)
    render(del.icon())
    del.onClick(null, ownedRow)
    const options = vi.mocked(confirmAlert).mock.calls[0][0] as any
    expect(options.title).toBe('Delete RSU')
    expect(options.message).toBe('Are you sure you want to delete "10.0.0.1"?')
  })
})

describe('AdminRsuTab super user row actions', () => {
  it('shows edit and delete for RSUs owned by another organization', () => {
    renderTab(true)
    expect(resolveRowAction('Edit RSU', viewerRow).hidden).toBe(false)
    expect(resolveRowAction('Delete RSU', viewerRow).hidden).toBe(false)
    expect(tableProps.selectionProps(viewerRow)).toEqual({ disabled: false })
  })
})

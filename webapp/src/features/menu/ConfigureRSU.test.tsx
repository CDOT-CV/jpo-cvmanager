import { fireEvent, render, screen } from '@testing-library/react'
import ConfigureRSU from './ConfigureRSU'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import { replaceChaoticIds } from '../../utils/test-utils'
import { vi } from 'vitest'
import { RsuInfo } from '../../models/RsuApi'
import { useGetRsuCountsByIpQuery } from '../api/rsuCountsApiSlice'
import { useGetAllRsusQuery } from '../api/rsuApiSlice'

vi.mock('../../components/SnmpwalkMenu', () => ({ default: () => null }))
vi.mock('../../components/RsuRebootMenu', () => ({
  default: ({ isOwnerOrg }: any) => <div data-testid="reboot">{`owner=${isOwnerOrg}`}</div>,
}))
// Surface the ownership props each multi-RSU menu receives
vi.mock('../../components/SnmpsetMenu', () => ({
  default: ({ isOwnerOrg, ownerOrganization }: any) => (
    <div data-testid="snmpset">{`owner=${isOwnerOrg} others=${ownerOrganization ?? 'none'}`}</div>
  ),
}))
vi.mock('../../components/RsuFirmwareMenu', () => ({
  default: ({ isOwnerOrg, ownerOrganization }: any) => (
    <div data-testid="firmware">{`owner=${isOwnerOrg} others=${ownerOrganization ?? 'none'}`}</div>
  ),
}))

// Passes through to the real hook unless a test overrides it
vi.mock('../api/rsuApiSlice', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/rsuApiSlice')>()
  return { ...actual, useGetAllRsusQuery: vi.fn(actual.useGetAllRsusQuery) }
})

vi.mock('../api/rsuCountsApiSlice', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/rsuCountsApiSlice')>()
  return {
    ...actual,
    useGetRsuCountsByIpQuery: vi.fn(),
  }
})

const mockUseGetRsuCountsByIpQuery = vi.mocked(useGetRsuCountsByIpQuery)

const mockQueryResult = <T,>(result: T): ReturnType<typeof useGetRsuCountsByIpQuery> =>
  result as unknown as ReturnType<typeof useGetRsuCountsByIpQuery>

const successCountsQuery = {
  data: [
    {
      message_type: 'BSM',
      rsu_ip: '10.0.0.16',
      ode_input_count: 327817,
      ode_output_count: 327745,
      road: 'I70',
    },
    {
      message_type: 'MAP',
      rsu_ip: '10.0.0.16',
      ode_input_count: 12,
      ode_output_count: 11,
      road: 'I70',
    },
  ],
  isFetching: false,
  isError: false,
  error: undefined,
}

const selectedRsu: RsuInfo = {
  id: 1,
  type: 'Feature',
  geometry: {
    type: 'Point',
    coordinates: [-104.99, 39.74],
  },
  properties: {
    rsu_id: 1,
    milepost: 6,
    geography: '',
    model_name: 'Kapsch',
    ipv4_address: '10.0.0.16',
    primary_route: 'I70',
    serial_number: 'E0006',
    manufacturer_name: 'Kapsch',
    tim_deposit: false,
  },
}

it('should take a snapshot', () => {
  mockUseGetRsuCountsByIpQuery.mockReturnValue(mockQueryResult(successCountsQuery))
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <ConfigureRSU />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it.each(['Commsignia', 'COMMSIGNIA', 'Kapsch', 'Yunex', undefined])(
  'shows the reboot option only for Commsignia (%s)',
  (manufacturer) => {
    mockUseGetRsuCountsByIpQuery.mockReturnValue(mockQueryResult(successCountsQuery))
    render(
      <ThemeProvider theme={testTheme}>
        <Provider
          store={setupStore({
            rsu: {
              value: {
                selectedRsu: {
                  ...selectedRsu,
                  properties: { ...selectedRsu.properties, manufacturer_name: manufacturer },
                },
              },
            },
          })}
        >
          <ConfigureRSU />
        </Provider>
      </ThemeProvider>
    )

    if (manufacturer?.toLowerCase() === 'commsignia') {
      expect(screen.getByRole('button', { name: 'Reboot' })).toBeInTheDocument()
    } else {
      expect(screen.queryByRole('button', { name: 'Reboot' })).not.toBeInTheDocument()
    }
    expect(screen.getByRole('button', { name: 'Firmware' })).toBeInTheDocument()
  }
)

it('lets a super user reboot a Commsignia RSU owned by another organization without an ownership lookup', () => {
  mockUseGetRsuCountsByIpQuery.mockReturnValue(mockQueryResult(successCountsQuery))
  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          user: { value: { authLoginData: { data: { super_user: true } }, organization: { name: 'Some Other Org' } } },
          rsu: {
            value: {
              selectedRsu: {
                ...selectedRsu,
                properties: { ...selectedRsu.properties, manufacturer_name: 'Commsignia' },
              },
            },
          },
        })}
      >
        <ConfigureRSU />
      </Provider>
    </ThemeProvider>
  )

  expect(screen.getByTestId('reboot')).toHaveTextContent('owner=true')
  expect(screen.queryByLabelText('Checking RSU ownership')).not.toBeInTheDocument()
  expect(screen.queryByText('Failed to verify RSU ownership. Please try again.')).not.toBeInTheDocument()
})

it('shows counts for all configured message types, not just the selected type', () => {
  mockUseGetRsuCountsByIpQuery.mockReturnValue(mockQueryResult(successCountsQuery))
  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          rsu: {
            value: {
              selectedRsu,
            },
          },
        })}
      >
        <ConfigureRSU />
      </Provider>
    </ThemeProvider>
  )

  expect(screen.getByText('Message Counts')).toBeInTheDocument()
  expect(screen.getByText('BSM')).toBeInTheDocument()
  expect(screen.getByText('MAP')).toBeInTheDocument()
  expect(screen.getByText('SPAT')).toBeInTheDocument()
  expect(screen.getByText('327,817')).toBeInTheDocument()
  expect(screen.getByText('327,745')).toBeInTheDocument()
  expect(screen.getByText('12')).toBeInTheDocument()
  expect(screen.queryByText('BSM Input')).not.toBeInTheDocument()
})

it('shows the Intersection API ProblemDetail when message counts fail', () => {
  mockUseGetRsuCountsByIpQuery.mockReturnValue(
    mockQueryResult({
      data: undefined,
      isFetching: false,
      isError: true,
      error: {
        status: 400,
        data: { detail: 'The message counts query ran out of memory. Please select a shorter time range.' },
      },
    })
  )

  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          rsu: {
            value: {
              selectedRsu,
            },
          },
        })}
      >
        <ConfigureRSU />
      </Provider>
    </ThemeProvider>
  )

  fireEvent.click(screen.getByRole('button', { name: 'Message Counts' }))
  expect(screen.getByRole('alert')).toHaveTextContent(
    'The message counts query ran out of memory. Please select a shorter time range.'
  )
})

const ORG = 'Owner Org'

const renderMultiRsu = (
  owners: Record<string, string>,
  isFetching = false,
  { superUser = false, isError = false }: { superUser?: boolean; isError?: boolean } = {}
) => {
  vi.mocked(useGetAllRsusQuery).mockReturnValue({
    currentData:
      isFetching || isError
        ? undefined
        : { content: Object.entries(owners).map(([ip, owner_organization]) => ({ ip, owner_organization })) },
    isFetching,
    isError,
  } as any)
  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          user: { value: { authLoginData: { data: { super_user: superUser } }, organization: { name: ORG } } },
          config: { value: { configList: Object.keys(owners) } },
        })}
      >
        <ConfigureRSU />
      </Provider>
    </ThemeProvider>
  )
}

describe('ConfigureRSU multi-RSU ownership', () => {
  afterEach(() => {
    vi.mocked(useGetAllRsusQuery).mockReset()
  })

  it('allows configuration when every selected RSU is owned by the organization', () => {
    renderMultiRsu({ '10.0.0.1': ORG, '10.0.0.2': ORG })
    expect(screen.getByTestId('snmpset')).toHaveTextContent('owner=true others=none')
    expect(screen.getByTestId('firmware')).toHaveTextContent('owner=true others=none')
  })

  it('blocks configuration and names the other owners when any RSU is owned elsewhere', () => {
    renderMultiRsu({ '10.0.0.1': ORG, '10.0.0.2': 'Other Org', '10.0.0.3': 'Other Org' })
    expect(screen.getByTestId('snmpset')).toHaveTextContent('owner=false others=Other Org')
    expect(screen.getByTestId('firmware')).toHaveTextContent('owner=false others=Other Org')
  })

  it('shows a loading indicator while ownership is being checked', () => {
    renderMultiRsu({ '10.0.0.1': ORG }, true)
    expect(screen.getAllByLabelText('Checking RSU ownership').length).toBeGreaterThan(0)
    expect(screen.queryByTestId('snmpset')).not.toBeInTheDocument()
  })

  it('reports a failed ownership lookup as an error instead of an ownership restriction', () => {
    renderMultiRsu({ '10.0.0.1': ORG }, false, { isError: true })
    expect(screen.getAllByText('Failed to verify RSU ownership. Please try again.').length).toBeGreaterThan(0)
    expect(screen.queryByTestId('snmpset')).not.toBeInTheDocument()
  })

  it('lets a super user configure RSUs owned by other organizations', () => {
    renderMultiRsu({ '10.0.0.1': 'Other Org' }, false, { superUser: true })
    expect(screen.getByTestId('snmpset')).toHaveTextContent('owner=true')
    expect(screen.getByTestId('firmware')).toHaveTextContent('owner=true')
    expect(screen.queryByText('Only RSUs owned by your organization will be selected.')).not.toBeInTheDocument()
  })
})

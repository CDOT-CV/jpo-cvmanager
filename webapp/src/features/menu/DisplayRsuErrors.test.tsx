import '@testing-library/jest-dom'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import { setupStore } from '../../store'
import DisplayRsuErrors from './DisplayRsuErrors'
import { RsuInfo, RsuProperties } from '../../models/RsuApi'
import { replaceChaoticIds } from '../../utils/test-utils'

vi.mock('../api/scmsApiSlice', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/scmsApiSlice')>()
  return {
    ...actual,
    useGetScmsStatusQuery: vi.fn(),
  }
})

vi.mock('../api/rsuOnlineStatusApiSlice', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/rsuOnlineStatusApiSlice')>()
  return {
    ...actual,
    useGetRsuOnlineStatusesQuery: vi.fn(),
    useGetRsuLastOnlineQuery: vi.fn(),
  }
})

import { useSendRsuErrorSummaryEmailMutation } from '../api/emailApiSlice'

vi.mock('../api/emailApiSlice', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/emailApiSlice')>(),
  useSendRsuErrorSummaryEmailMutation: vi.fn(() => [vi.fn()]),
}))

import { useGetScmsStatusQuery } from '../api/scmsApiSlice'
import { useGetRsuLastOnlineQuery, useGetRsuOnlineStatusesQuery } from '../api/rsuOnlineStatusApiSlice'
const mockUseGetScmsStatusQuery = vi.mocked(useGetScmsStatusQuery)
const mockUseGetRsuOnlineStatusesQuery = vi.mocked(useGetRsuOnlineStatusesQuery)
const mockUseGetRsuLastOnlineQuery = vi.mocked(useGetRsuLastOnlineQuery)

const TEST_IP = '10.0.0.1'
const VALID_EXPIRATION_ISO = '2026-12-31T23:59:59Z'

const testRsu: RsuInfo = {
  id: 1,
  type: 'Feature',
  geometry: { type: 'Point', coordinates: [-104.9903, 39.7392] },
  properties: {
    rsu_id: 1,
    milepost: 1,
    geography: 'POINT (39.7392 -104.9903)',
    model_name: 'model',
    ipv4_address: TEST_IP,
    primary_route: 'I-25',
    serial_number: 'SN001',
    manufacturer_name: 'Acme',
    tim_deposit: false,
  } as RsuProperties,
}

const rsuPreloadedState = {
  rsu: {
    value: {
      selectedRsu: null,
      rsuData: [testRsu],
      geoMsgType: 'BSM',
      rsuMapData: {},
      mapList: [],
      mapDate: '',
      displayMap: false,
      geoMsgStart: '',
      geoMsgEnd: '',
      addGeoMsgPoint: false,
      geoMsgCoordinates: [],
      geoMsgData: [],
      geoMsgDateError: false,
      geoMsgFilter: false,
      geoMsgFilterStep: 60,
      geoMsgFilterOffset: 0,
      ssmDisplay: false,
      srmSsmList: [],
      selectedSrm: [],
    },
  },
  user: {
    value: {
      authLoginData: { token: 'test-token' },
      organization: { organization: 'test-org', role: 'admin' },
    },
  },
}

const mockRsuOnline = () => {
  mockUseGetRsuOnlineStatusesQuery.mockReturnValue({ currentData: { [TEST_IP]: { current_status: 'online' } } } as any)
  mockUseGetRsuLastOnlineQuery.mockReturnValue({ currentData: { ip: TEST_IP, last_online: '2026-04-10T10:00:00Z' } } as any)
}

function renderComponent(preloadedState = rsuPreloadedState, initialSelectedRsu?: RsuInfo) {
  return render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore(preloadedState)}>
        <DisplayRsuErrors initialSelectedRsu={initialSelectedRsu} />
      </Provider>
    </ThemeProvider>
  )
}

describe('DisplayRsuErrors table view (no RSU selected)', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    mockUseGetScmsStatusQuery.mockClear()
    mockRsuOnline()
  })

  it('shows Healthy status when health === true', () => {
    mockUseGetScmsStatusQuery.mockReturnValue({
      currentData: { [TEST_IP]: { health: true, expiration: VALID_EXPIRATION_ISO } },
    } as any)

    renderComponent()

    expect(screen.getAllByText('Healthy').length).toBeGreaterThan(0)
  })

  it('shows Unhealthy status when health === false', () => {
    mockUseGetScmsStatusQuery.mockReturnValue({
      currentData: { [TEST_IP]: { health: false, expiration: '2025-01-01T00:00:00Z' } },
    } as any)

    renderComponent()

    expect(screen.getAllByText('Unhealthy').length).toBeGreaterThan(0)
  })

  it('shows Unknown status when the SCMS record is null', () => {
    mockUseGetScmsStatusQuery.mockReturnValue({
      currentData: { [TEST_IP]: null },
    } as any)

    renderComponent()

    expect(screen.getAllByText('Unknown').length).toBeGreaterThan(0)
    expect(screen.queryByText('Unhealthy')).not.toBeInTheDocument()
  })

  it('shows Unknown expiration when no SCMS record exists', () => {
    mockUseGetScmsStatusQuery.mockReturnValue({ currentData: {} } as any)

    renderComponent()

    expect(screen.getAllByText('Unknown').length).toBeGreaterThan(0)
  })

  it('calls useGetScmsStatusQuery with skip:true when no organization is selected', () => {
    mockUseGetScmsStatusQuery.mockReturnValue({ currentData: {} } as any)

    const stateWithoutOrg = {
      ...rsuPreloadedState,
      user: {
        value: {
          authLoginData: { token: 'test-token' },
          organization: { organization: '', role: 'admin' },
        },
      },
    }
    renderComponent(stateWithoutOrg)

    // The hook is called with skip: !organization — empty string → skip: true
    const lastCallArgs = mockUseGetScmsStatusQuery.mock.calls.at(-1)!
    expect(lastCallArgs[1]).toMatchObject({ skip: true })
  })
})

describe('DisplayRsuErrors detail view (RSU selected)', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    mockUseGetScmsStatusQuery.mockClear()
    mockRsuOnline()
  })

  const renderDetail = (overrideState = rsuPreloadedState) =>
    render(
      <ThemeProvider theme={testTheme}>
        <Provider store={setupStore(overrideState)}>
          <DisplayRsuErrors initialSelectedRsu={testRsu} />
        </Provider>
      </ThemeProvider>
    )

  it('renders Healthy in SCMS status accordion when health === true', () => {
    mockUseGetScmsStatusQuery.mockReturnValue({
      currentData: { [TEST_IP]: { health: true, expiration: VALID_EXPIRATION_ISO } },
    } as any)

    renderDetail()

    expect(screen.getAllByText('Healthy').length).toBeGreaterThan(0)
  })

  it('renders Unhealthy in SCMS status accordion when health === false', () => {
    mockUseGetScmsStatusQuery.mockReturnValue({
      currentData: { [TEST_IP]: { health: false, expiration: '2025-01-01T00:00:00Z' } },
    } as any)

    renderDetail()

    expect(screen.getAllByText('Unhealthy').length).toBeGreaterThan(0)
  })

  it('renders Unknown expiration when no SCMS record exists', () => {
    mockUseGetScmsStatusQuery.mockReturnValue({ currentData: {} } as any)

    renderDetail()

    expect(screen.getAllByText('Unknown').length).toBeGreaterThan(0)
  })
})

describe('DisplayRsuErrors snapshot', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-04-20T00:00:00Z'))
    mockUseGetScmsStatusQuery.mockClear()
    mockRsuOnline()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should match snapshot', () => {
    mockUseGetScmsStatusQuery.mockReturnValue({
      currentData: { [TEST_IP]: { health: true, expiration: VALID_EXPIRATION_ISO } },
    } as any)

    const { container } = renderComponent()
    expect(replaceChaoticIds(container)).toMatchSnapshot()
  })
})

describe('DisplayRsuErrors health-state regressions', () => {
  beforeEach(() => {
    mockRsuOnline()
    mockUseGetScmsStatusQuery.mockReturnValue({ currentData: {} } as any)
  })

  it.each([
    [{ isLoading: true, isFetching: true }, 'Loading'],
    [{ isError: true }, 'Unavailable'],
    [{ currentData: {} }, 'Unknown'],
    [{ isUninitialized: true }, 'Unknown'],
  ])('does not report missing online data as Offline (%j)', (query, label) => {
    mockUseGetRsuOnlineStatusesQuery.mockReturnValue(query as any)
    renderComponent()
    expect(screen.getAllByText(label).length).toBeGreaterThan(0)
    expect(screen.queryByText('Offline')).not.toBeInTheDocument()
  })

  it('offers a retry for failed status requests', () => {
    const retryOnline = vi.fn()
    const retryScms = vi.fn()
    mockUseGetRsuOnlineStatusesQuery.mockReturnValue({ isError: true, refetch: retryOnline } as any)
    mockUseGetScmsStatusQuery.mockReturnValue({ isError: true, refetch: retryScms } as any)
    renderComponent()
    fireEvent.click(within(screen.getByText('Online status unavailable.').closest('[role="alert"]')!).getByText('Retry'))
    fireEvent.click(within(screen.getByText('SCMS status unavailable.').closest('[role="alert"]')!).getByText('Retry'))
    expect(retryOnline).toHaveBeenCalledOnce()
    expect(retryScms).toHaveBeenCalledOnce()
    expect(screen.queryByText('Offline')).not.toBeInTheDocument()
    expect(screen.queryByText('Unhealthy')).not.toBeInTheDocument()
  })

  it('does not turn failed or missing history into failure claims in email summaries', async () => {
    const sendSummary = vi.fn<(contents: RsuErrorSummaryEmailContents) => { unwrap: () => Promise<void> }>()
    sendSummary.mockReturnValue({ unwrap: () => Promise.resolve() })
    vi.mocked(useSendRsuErrorSummaryEmailMutation).mockReturnValue([sendSummary] as any)
    mockUseGetRsuOnlineStatusesQuery.mockReturnValue({ isError: true } as any)
    mockUseGetRsuLastOnlineQuery.mockReturnValue({ isError: true } as any)
    renderComponent(rsuPreloadedState, testRsu)
    fireEvent.click(screen.getByRole('button', { name: 'Send Error Summary Email' }))
    await waitFor(() => expect(sendSummary).toHaveBeenCalledOnce())
    const message = sendSummary.mock.calls[0][0].message
    expect(message).toContain('RSU Unavailable (Last Online: Unavailable)')
    expect(message).toContain('SCMS Unknown (Certificate expiration: Unknown)')
    expect(message).not.toMatch(/Never Online|Never downloaded|Unhealthy|expired/)
  })

  it('prints lowercase offline, unstable, and unhealthy devices, excluding healthy and unknown-only devices', () => {
    const ips = ['10.0.0.1', '10.0.0.2', '10.0.0.3', '10.0.0.4', '10.0.0.5']
    mockUseGetRsuOnlineStatusesQuery.mockReturnValue({ currentData: {
      [ips[0]]: { current_status: 'offline' },
      [ips[1]]: { current_status: 'unstable' },
      [ips[2]]: { current_status: 'online' },
      [ips[3]]: { current_status: 'online' },
    } } as any)
    mockUseGetScmsStatusQuery.mockReturnValue({ currentData: {
      [ips[0]]: { health: true, expiration: VALID_EXPIRATION_ISO },
      [ips[1]]: { health: true, expiration: VALID_EXPIRATION_ISO },
      [ips[2]]: { health: false, expiration: null },
      [ips[3]]: { health: true, expiration: VALID_EXPIRATION_ISO },
      [ips[4]]: null,
    } } as any)
    renderComponent({ ...rsuPreloadedState, rsu: { value: {
      ...rsuPreloadedState.rsu.value,
      rsuData: ips.map((ip, index) => ({ ...testRsu, id: index, properties: { ...testRsu.properties, ipv4_address: ip } })),
    } } })
    const errorReport = within(screen.getByText('RSU Error Summary').parentElement!)
    const fullReport = within(screen.getByText('RSU Summary').parentElement!)
    for (const ip of ips.slice(0, 3)) expect(errorReport.getByText(ip)).toBeInTheDocument()
    for (const ip of ips.slice(3)) expect(errorReport.queryByText(ip)).not.toBeInTheDocument()
    for (const ip of ips) expect(fullReport.getByText(ip)).toBeInTheDocument()
    expect(errorReport.getByText(/Only confirmed offline/)).toBeInTheDocument()
  })
})

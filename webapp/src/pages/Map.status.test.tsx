import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MapPage from './Map'
import EnvironmentVars from '../EnvironmentVars'
import { setupStore } from '../store'
import { testTheme } from '../styles'
import RsuApi from '../apis/rsu-api'
import { selectRsu } from '../generalSlices/rsuSlice'
import { RsuInfo } from '../models/RsuApi'
import { useGetScmsStatusQuery } from '../features/api/scmsApiSlice'
import { useGetRsuLastOnlineQuery, useGetRsuOnlineStatusesQuery } from '../features/api/rsuOnlineStatusApiSlice'

vi.mock('react-map-gl', async () => {
  const { forwardRef } = await import('react')
  return {
    default: forwardRef(({ children }: any, ref) => <div data-map-ref={Boolean(ref)}>{children}</div>),
    Popup: ({ children }: any) => <div data-testid="rsu-popup">{children}</div>,
    Source: ({ id, data, children }: any) => <div data-testid={id} data-source={JSON.stringify(data)}>{children}</div>,
    Layer: () => null,
  }
})

vi.mock('../features/api/scmsApiSlice', async (importOriginal) => ({
  ...await importOriginal<typeof import('../features/api/scmsApiSlice')>(),
  useGetScmsStatusQuery: vi.fn(),
}))

vi.mock('../features/api/rsuOnlineStatusApiSlice', async (importOriginal) => ({
  ...await importOriginal<typeof import('../features/api/rsuOnlineStatusApiSlice')>(),
  useGetRsuOnlineStatusesQuery: vi.fn(),
  useGetRsuLastOnlineQuery: vi.fn(),
}))

vi.mock('../features/api/rsuCountsApiSlice', async (importOriginal) => ({
  ...await importOriginal<typeof import('../features/api/rsuCountsApiSlice')>(),
  useGetRsuCountsQuery: () => ({ data: [] }),
}))

const ip = '10.0.0.1'
const originalRsuFeatureFlag = EnvironmentVars.ENABLE_RSU_FEATURES
const rsu: RsuInfo = {
  id: 1,
  type: 'Feature',
  geometry: { type: 'Point', coordinates: [-104.9, 39.7] },
  properties: {
    rsu_id: 1,
    milepost: 1,
    geography: 'POINT (-104.9 39.7)',
    model_name: 'model',
    ipv4_address: ip,
    primary_route: 'I-25',
    serial_number: 'SN001',
    manufacturer_name: 'Acme',
    tim_deposit: false,
  },
}

const renderMap = async () => {
  const store = setupStore({ user: { value: {
    authLoginData: { token: 'test-token' },
    organization: { organization: 'test-org', role: 'admin' },
  } } })
  render(<ThemeProvider theme={testTheme}><Provider store={store}><MapPage /></Provider></ThemeProvider>)
  await waitFor(() => expect(store.getState().rsu.value.rsuData).toHaveLength(1))
  return store
}

const markerColor = () => JSON.parse(screen.getByTestId('rsu-source').getAttribute('data-source')!)
  .features[0].properties.display_color

describe('main map device health', () => {
  beforeEach(() => {
    EnvironmentVars.ENABLE_RSU_FEATURES = true
    vi.spyOn(RsuApi, 'getRsuInfo').mockResolvedValue({ rsuList: [rsu] })
    vi.spyOn(RsuApi, 'getWzdxData').mockResolvedValue({ features: [] } as any)
    vi.mocked(useGetRsuOnlineStatusesQuery).mockReturnValue({ currentData: {} } as any)
    vi.mocked(useGetScmsStatusQuery).mockReturnValue({ currentData: {} } as any)
    vi.mocked(useGetRsuLastOnlineQuery).mockReturnValue({ isError: true } as any)
  })

  afterEach(() => {
    EnvironmentVars.ENABLE_RSU_FEATURES = originalRsuFeatureFlag
    vi.restoreAllMocks()
  })

  it.each([
    [{ isLoading: true }, 'Loading', '#B0B0B0'],
    [{ isError: true }, 'Unavailable', '#B0B0B0'],
    [{ currentData: {} }, 'Unknown', '#B0B0B0'],
    [{ currentData: { [ip]: { current_status: 'offline' } } }, 'Offline', '#E94F37'],
    [{ currentData: { [ip]: { current_status: 'unstable' } } }, 'Unstable', '#D1A711'],
    [{ currentData: { [ip]: { current_status: 'online' } } }, 'Online', '#A1D363'],
  ])('uses the same status for markers and popups (%s)', async (query, label, color) => {
    vi.mocked(useGetRsuOnlineStatusesQuery).mockReturnValue(query as any)
    const store = await renderMap()
    expect(markerColor()).toBe(color)
    // Selection is driven by Redux, as it is after clicking an RSU on the map.
    act(() => { store.dispatch(selectRsu(rsu)) })
    const popup = within(screen.getByTestId('rsu-popup'))
    expect(popup.getAllByText(label).length).toBeGreaterThan(0)
    expect(popup.queryByText('Unhealthy')).not.toBeInTheDocument()
    expect(popup.queryByText(/not enrolled/)).not.toBeInTheDocument()
    expect(popup.queryByText(/Never/)).not.toBeInTheDocument()
  })

  it.each([
    [true, 'Healthy', '#A1D363'],
    [false, 'Unhealthy', '#E94F37'],
    [null, 'Unknown', '#B0B0B0'],
  ])('uses explicit SCMS health %s for markers and popup text', async (health, label, color) => {
    vi.mocked(useGetScmsStatusQuery).mockReturnValue({ currentData: { [ip]: { health, expiration: null } } } as any)
    const store = await renderMap()
    fireEvent.click(screen.getByLabelText('SCMS Status'))
    expect(markerColor()).toBe(color)
    act(() => { store.dispatch(selectRsu(rsu)) })
    expect(within(screen.getByTestId('rsu-popup')).getAllByText(label).length).toBeGreaterThan(0)
    expect(screen.getByText('No certificate expiration recorded')).toBeInTheDocument()
  })

  it('allows a failed online-status request to be retried', async () => {
    const refetch = vi.fn()
    vi.mocked(useGetRsuOnlineStatusesQuery).mockReturnValue({ isError: true, refetch } as any)
    await renderMap()
    const alert = screen.getByText('Online status unavailable.').closest<HTMLElement>('[role="alert"]')!
    fireEvent.click(within(alert).getByText('Retry'))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('hides RSU status errors when RSU features are disabled', async () => {
    EnvironmentVars.ENABLE_RSU_FEATURES = false
    vi.mocked(useGetRsuOnlineStatusesQuery).mockReturnValue({ isError: true } as any)
    vi.mocked(useGetScmsStatusQuery).mockReturnValue({ isError: true } as any)
    await renderMap()
    expect(screen.queryByText('Online status unavailable.')).not.toBeInTheDocument()
    expect(screen.queryByText('SCMS status unavailable.')).not.toBeInTheDocument()
  })
})

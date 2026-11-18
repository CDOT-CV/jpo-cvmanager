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
import { selectActiveLayers } from './mapSlice'
import { updateConfigPoints } from '../generalSlices/configSlice'
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
    organization: { name: 'test-org', organization: 'test-org', role: 'admin' },
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
    [{ currentData: { [ip]: { current_status: 'unknown' } } }, 'Unknown', '#B0B0B0'],
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
    fireEvent.click(screen.getByRole('button', { name: 'Show health data details' }))
    const alert = screen.getByRole('alert', { name: 'Online health warning' })
    fireEvent.click(within(alert).getByText('Retry'))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('keeps health coloring and unrelated layers intact when switching modes or inspecting fetch details', async () => {
    const refreshOnline = vi.fn()
    const refreshScms = vi.fn()
    vi.mocked(useGetRsuOnlineStatusesQuery).mockReturnValue({
      currentData: { [ip]: { current_status: 'online' } }, refetch: refreshOnline,
    } as any)
    vi.mocked(useGetScmsStatusQuery).mockReturnValue({
      currentData: { [ip]: { health: false, expiration: null } }, refetch: refreshScms,
    } as any)
    const store = await renderMap()
    const healthControls = within(screen.getByRole('region', { name: 'RSU Health' }))
    expect(healthControls.getByRole('radiogroup', { name: 'RSU status view' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('checkbox', { name: 'Heatmap' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'RSU Viewer' }))
    expect(screen.queryByTestId('rsu-source')).not.toBeInTheDocument()

    expect(screen.queryByRole('region', { name: 'RSU Health' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('checkbox', { name: 'RSU Viewer' }))
    fireEvent.click(within(screen.getByRole('region', { name: 'RSU Health' })).getByRole('radio', { name: 'SCMS Status' }))
    expect(selectActiveLayers(store.getState())).toEqual(expect.arrayContaining(['rsu-layer', 'heatmap-layer']))
    expect(markerColor()).toBe('#E94F37')
    fireEvent.click(within(screen.getByRole('region', { name: 'RSU Health' })).getByRole('radio', { name: 'Online Status' }))
    expect(markerColor()).toBe('#A1D363')

    fireEvent.click(screen.getByRole('checkbox', { name: 'RSU Viewer' }))
    expect(screen.queryByTestId('rsu-source')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('checkbox', { name: 'RSU Viewer' }))
    fireEvent.click(within(screen.getByRole('region', { name: 'RSU Health' })).getByRole('radio', { name: 'Online Status' }))
    expect(markerColor()).toBe('#A1D363')

    const restoredControls = within(screen.getByRole('region', { name: 'RSU Health' }))
    fireEvent.click(restoredControls.getByRole('button', { name: 'Show health data details' }))
    expect(restoredControls.queryByRole('button', { name: 'Refresh health data' })).not.toBeInTheDocument()
    expect(refreshOnline).not.toHaveBeenCalled()
    expect(refreshScms).not.toHaveBeenCalled()
    expect(restoredControls.getByRole('radio', { name: 'Online Status' })).toBeChecked()
    expect(selectActiveLayers(store.getState())).toEqual(expect.arrayContaining(['rsu-layer', 'heatmap-layer']))
    expect(markerColor()).toBe('#A1D363')
  })

  it('hides RSU status errors when RSU features are disabled', async () => {
    EnvironmentVars.ENABLE_RSU_FEATURES = false
    vi.mocked(useGetRsuOnlineStatusesQuery).mockReturnValue({ isError: true } as any)
    vi.mocked(useGetScmsStatusQuery).mockReturnValue({ isError: true } as any)
    await renderMap()
    expect(screen.queryByRole('region', { name: 'RSU Health' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Show health data details' })).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('hides map controls and stops placement while preserving configuration and fleet access', async () => {
    const store = await renderMap()
    fireEvent.click(screen.getByRole('radio', { name: 'SCMS Status' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Add Points' }))
    const coordinates = [[-105, 39], [-104, 39], [-104, 40], [-105, 39]]
    act(() => {
      store.dispatch(updateConfigPoints(coordinates))
      store.dispatch(selectRsu(rsu))
    })
    fireEvent.click(screen.getByRole('checkbox', { name: 'RSU Viewer' }))
    expect(screen.queryByRole('region', { name: 'RSU Health' })).not.toBeInTheDocument()
    expect(screen.queryByText('RSU Configuration')).not.toBeInTheDocument()
    expect(screen.queryByTestId('rsu-popup')).not.toBeInTheDocument()
    expect(store.getState().config.value.configCoordinates).toEqual(coordinates)
    expect(screen.getByRole('button', { name: 'Display RSU Status' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('checkbox', { name: 'RSU Viewer' }))
    expect(screen.getByRole('checkbox', { name: 'Add Points' })).not.toBeChecked()
    expect(screen.getByRole('radio', { name: 'SCMS Status' })).toBeChecked()
    expect(screen.getByRole('button', { name: 'Clear configuration points' })).toBeEnabled()
  })

  it('filters RSUs from the health controls without a separate filter section', async () => {
    await renderMap()
    const healthControls = within(screen.getByRole('region', { name: 'RSU Health' }))
    expect(healthControls.getByText('Status View')).toBeInTheDocument()
    expect(screen.queryByText('Filter RSUs')).not.toBeInTheDocument()
    fireEvent.mouseDown(healthControls.getByRole('combobox', { name: 'Vendor' }))
    fireEvent.click(screen.getByRole('option', { name: 'Yunex' }))
    expect(JSON.parse(screen.getByTestId('rsu-source').getAttribute('data-source')!).features).toHaveLength(0)
    fireEvent.mouseDown(healthControls.getByRole('combobox', { name: 'Vendor' }))
    fireEvent.click(screen.getByRole('option', { name: 'All Vendors' }))
    expect(JSON.parse(screen.getByTestId('rsu-source').getAttribute('data-source')!).features).toHaveLength(1)
    expect(healthControls.getByRole('radio', { name: 'Online Status' })).toBeChecked()
  })

  it('preserves polygon-selection and clear actions in the configuration controls', async () => {
    const store = await renderMap()
    const addPoints = screen.getByRole('checkbox', { name: 'Add Points' })
    const clear = screen.getByRole('button', { name: 'Clear configuration points' })
    expect(clear).toBeDisabled()
    fireEvent.click(addPoints)
    expect(addPoints).toBeChecked()
    act(() => { store.dispatch(updateConfigPoints([[-105, 39], [-104, 39], [-104, 40], [-105, 39]])) })
    expect(clear).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Configure RSUs' })).toBeEnabled()
    fireEvent.click(clear)
    expect(store.getState().config.value.configCoordinates).toEqual([])
    expect(clear).toBeDisabled()
  })
})

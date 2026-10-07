import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { Toaster } from 'react-hot-toast'
import fetchMock from 'jest-fetch-mock'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MapPage from './Map'
import EnvironmentVars from '../EnvironmentVars'
import RsuApi from '../apis/rsu-api'
import { setupStore } from '../store'
import { testTheme } from '../styles'
import { setGeoMsgDataResult, setGeoMsgFilterOffset, setGeoMsgFilterStep, updateGeoMsgDate, updateGeoMsgPoints, toggleGeoMsgPointSelect } from '../generalSlices/rsuSlice'
import { toggleLayerActive } from './mapSlice'

const originalRsuFeatureFlag = EnvironmentVars.ENABLE_RSU_FEATURES
const originalMatchMedia = window.matchMedia

const geoMsgFeatures = [
  {
    type: 'Feature',
    properties: { id: 'vehicle-1', timeStamp: '2024-04-01T07:00:00Z' },
    geometry: { type: 'Point', coordinates: [-104.9, 39.7] },
  },
  {
    type: 'Feature',
    properties: { id: 'vehicle-2', timeStamp: '2024-04-01T07:10:00Z' },
    geometry: { type: 'Point', coordinates: [-104.8, 39.7] },
  },
  {
    type: 'Feature',
    properties: { id: 'vehicle-1', timeStamp: '2024-04-01T07:20:00Z' },
    geometry: { type: 'Point', coordinates: [-104.7, 39.7] },
  },
]

const makeStore = () => {
  const store = setupStore()
  store.dispatch(toggleLayerActive('msg-viewer-layer'))
  store.dispatch(updateGeoMsgPoints([[-105, 39], [-104, 39], [-104, 40], [-105, 39]]))
  store.dispatch(updateGeoMsgDate({ type: 'start', date: '2024-04-01T00:00:00-06:00' }))
  store.dispatch(updateGeoMsgDate({ type: 'end', date: '2024-04-01T02:00:00-06:00' }))
  store.dispatch(setGeoMsgFilterStep(15))
  store.dispatch(setGeoMsgFilterOffset(2))
  return store
}

const renderMapPage = (store = makeStore()) => {
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={store}>
        <MapPage />
      </Provider>
      <Toaster />
    </ThemeProvider>
  )
  return store
}

describe('Map geo-message query', () => {
  beforeEach(() => {
    fetchMock.resetMocks()
    fetchMock.doMock()
    vi.spyOn(RsuApi, 'getWzdxData').mockResolvedValue({ features: [] } as any)
    EnvironmentVars.ENABLE_RSU_FEATURES = true
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((media: string) => ({
        matches: false,
        media,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    EnvironmentVars.ENABLE_RSU_FEATURES = originalRsuFeatureFlag
    Object.defineProperty(window, 'matchMedia', { configurable: true, value: originalMatchMedia })
  })

  it('posts UTC timestamps and a closed polygon, colors returned IDs, resets the filter, and blocks duplicate submits', async () => {
    const store = makeStore()
    let finishResponse: (response: string) => void = () => undefined
    fetchMock.mockResponseOnce(() => new Promise<string>((resolve) => (finishResponse = resolve)))

    renderMapPage(store)
    const submitButton = screen.getByRole('button', { name: 'Submit' })
    fireEvent.click(submitButton)

    await waitFor(() => expect(submitButton).toBeDisabled())
    fireEvent.click(submitButton)
    expect(fetchMock.mock.calls).toHaveLength(1)

    finishResponse(JSON.stringify(geoMsgFeatures))
    await waitFor(() => expect(store.getState().rsu.value.geoMsgData).toHaveLength(3))

    const request = fetchMock.mock.calls[0][0] as Request
    expect(request.url).toBe(`${EnvironmentVars.CVIZ_API_SERVER_URL}/rsu-geo-msg-data`)
    expect(request.method).toBe('POST')
    expect(await request.json()).toEqual({
      msg_type: 'BSM',
      start: '2024-04-01T06:00:00Z',
      end: '2024-04-01T08:00:00Z',
      geometry: [[-105, 39], [-104, 39], [-104, 40], [-105, 39]],
    })
    expect(store.getState().rsu.value.geoMsgData.map((feature) => feature.properties?.colorIndex)).toEqual([0, 1, 0])
    expect(store.getState().rsu.value.geoMsgFilter).toBe(true)
    expect(store.getState().rsu.value.geoMsgFilterStep).toBe(60)
    expect(store.getState().rsu.value.geoMsgFilterOffset).toBe(0)
    expect(submitButton).not.toBeDisabled()
  })

  it('clears results and initializes the empty-result message for an empty response', async () => {
    const store = makeStore()
    store.dispatch(setGeoMsgDataResult(geoMsgFeatures as any))
    fetchMock.mockResponseOnce(JSON.stringify([]))

    renderMapPage(store)
    fireEvent.click(screen.getByRole('button', { name: 'New Search' }))
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }))

    await waitFor(() => expect(store.getState().rsu.value.geoMsgData).toEqual([]))
    expect(await screen.findByText(/No data found for the selected date range/)).toBeInTheDocument()
  })

  it('clears previous results after a network failure', async () => {
    const store = makeStore()
    store.dispatch(setGeoMsgDataResult(geoMsgFeatures as any))
    fetchMock.mockRejectOnce(new Error('network down'))
    vi.spyOn(console, 'error').mockImplementation(() => undefined)

    renderMapPage(store)
    fireEvent.click(screen.getByRole('button', { name: 'New Search' }))
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }))

    await waitFor(() => expect(store.getState().rsu.value.geoMsgData).toEqual([]))
    expect(await screen.findByText(/No data found for the selected date range/)).toBeInTheDocument()
  })

  it('shows the Problem Details message for an API error and clears previous results', async () => {
    const store = makeStore()
    store.dispatch(setGeoMsgDataResult(geoMsgFeatures as any))
    fetchMock.mockResponseOnce(
      JSON.stringify({
        type: 'about:blank',
        title: 'Service Unavailable',
        status: 503,
        detail: 'Processed message storage is unavailable',
      }),
      { status: 503, headers: { 'Content-Type': 'application/problem+json' } }
    )

    renderMapPage(store)
    fireEvent.click(screen.getByRole('button', { name: 'New Search' }))
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }))

    expect(
      await screen.findByText('Query failed: Processed message storage is unavailable')
    ).toBeInTheDocument()
    await waitFor(() => expect(store.getState().rsu.value.geoMsgData).toEqual([]))
  })

  it('does not submit while the polygon is still being drawn', () => {
    const store = makeStore()
    store.dispatch(toggleGeoMsgPointSelect())
    renderMapPage(store)

    fireEvent.click(screen.getByRole('button', { name: 'Submit' }))

    expect(fetchMock.mock.calls).toHaveLength(0)
  })

  it('does not submit when RSU features are disabled', () => {
    EnvironmentVars.ENABLE_RSU_FEATURES = false
    renderMapPage()

    fireEvent.click(screen.getByRole('button', { name: 'Submit' }))

    expect(fetchMock.mock.calls).toHaveLength(0)
  })
})

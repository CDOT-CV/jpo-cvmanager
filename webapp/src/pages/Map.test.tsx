import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import fetchMock from 'jest-fetch-mock'
import Map from './Map'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { setupStore } from '../store'
import { replaceChaoticIds } from '../utils/test-utils'
import EnvironmentVars from '../EnvironmentVars'
import { toggleLayerActive } from './mapSlice'
import { WZDX_LINE_LAYER_ID } from '../components/map-layers/WzdxMapLayer'

const WZDX_FEED_URL = `${EnvironmentVars.CVIZ_API_SERVER_URL}/wzdx-feed`

const mockWzdxFeed = {
  type: 'FeatureCollection',
  features: [
    {
      properties: {
        core_details: {
          road_names: ['road_name_1'],
          direction: 'direction',
          description: 'description',
        },
        vehicle_impact: 'vehicle_impact',
        workers_present: 'workers_present',
        start_date: 'start_date',
        end_date: 'end_date',
      },
      geometry: {
        coordinates: [
          [-104.9903, 39.7392],
          [-104.9908, 39.7398],
          [-104.9909, 39.7399],
        ],
      },
    },
  ],
}

// Requests from RTK Query are Request objects; requests from apiHelper are url strings
const getWzdxFeedCalls = () =>
  fetchMock.mock.calls.filter(([input]) => {
    const url = typeof input === 'string' ? input : (input as Request).url
    return url === WZDX_FEED_URL
  })

const renderMap = (store: ReturnType<typeof setupStore>) =>
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={store}>
        <Map />
      </Provider>
    </ThemeProvider>
  )

beforeEach(() => {
  fetchMock.resetMocks()
  // Only mock the WZDx feed; all other requests keep the default (real fetch) behavior from setupTests
  fetchMock.dontMock()
  fetchMock.doMockIf(WZDX_FEED_URL, JSON.stringify(mockWzdxFeed))
})

afterEach(() => {
  EnvironmentVars.ENABLE_WZDX_FEATURES = true
})

it('snapshot bsmCoordinates wzdx', async () => {
  const initialState = {
    rsu: {
      loading: false,
      value: {
        rsuCounts: {},
        mapList: [],
        geoMsgData: [],
        geoMsgStart: '2023-05-10T03:24:00',
        geoMsgEnd: '2023-05-10T03:25:00',
        geoMsgCoordinates: [
          [-104.9903, 39.7392],
          [-104.9904, 39.7393],
          [-104.9905, 39.7391],
        ],
      },
    },
    user: { value: { authLoginData: { token: 'testToken' } } },
  }
  const store = setupStore(initialState)
  const { container } = renderMap(store)

  const rsuCheckbox = screen.getByRole('checkbox', { name: 'RSU Viewer' })
  const heatmapCheckbox = screen.getByRole('checkbox', { name: 'Heatmap' })
  const wzdxCheckbox = screen.getByRole('checkbox', { name: 'WZDx Viewer' })

  fireEvent.click(screen.getByText('RSU Viewer'))
  fireEvent.click(screen.getByText('Heatmap'))
  fireEvent.click(screen.getByText('WZDx Viewer'))

  expect(rsuCheckbox).not.toBeChecked()
  expect(heatmapCheckbox).toBeChecked()
  expect(wzdxCheckbox).toBeChecked()

  // Enabling the WZDx layer fetches the feed from the intersection-api
  await waitFor(() => expect(getWzdxFeedCalls()).toHaveLength(1))
  const request = getWzdxFeedCalls()[0][0] as Request
  expect(request.method).toBe('GET')
  expect(request.headers.get('Authorization')).toBe('Bearer testToken')
  await waitFor(() =>
    expect(store.getState().wzdxFeedApi.queries['getWzdxFeed(undefined)']?.data).toEqual(mockWzdxFeed)
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('snapshot bsmData clicked', () => {
  const initialState = {
    rsu: {
      value: {
        bsmCoordinates: [],
        rsuCounts: {},
        mapList: [],
        rsuData: [],
        bsmStart: '2023-05-10T03:24:00',
        bsmFilterStep: 60, // 1 hour
        bsmFilterOffset: 24 * 4, // 4 days
        bsmData: [
          {
            properties: {
              time: new Date('2023-05-12T03:24:00'),
            },
          },
          {
            properties: {
              time: new Date('2023-05-17T03:24:00'),
            },
          },
        ],
      },
    },
    config: {
      value: {
        configCoordinates: [],
        configList: ['1.1.1.1', '2.2.2.2', '3.3.3.3'],
        addConfigPoint: false,
      },
    },
    user: { value: { authLoginData: { token: 'testToken' } } },
  }
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore(initialState)}>
        <Map />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('does not fetch the WZDx feed while the WZDx layer is inactive', async () => {
  const store = setupStore({ user: { value: { authLoginData: { token: 'testToken' } } } })
  renderMap(store)

  expect(screen.getByRole('checkbox', { name: 'WZDx Viewer' })).not.toBeChecked()
  // Let any mount-time effects and pending requests flush
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
  expect(getWzdxFeedCalls()).toHaveLength(0)
})

it('refetches the WZDx feed each time the layer is toggled on', async () => {
  const store = setupStore({ user: { value: { authLoginData: { token: 'testToken' } } } })
  renderMap(store)

  fireEvent.click(screen.getByText('WZDx Viewer'))
  await waitFor(() => expect(getWzdxFeedCalls()).toHaveLength(1))

  fireEvent.click(screen.getByText('WZDx Viewer'))
  fireEvent.click(screen.getByText('WZDx Viewer'))
  await waitFor(() => expect(getWzdxFeedCalls()).toHaveLength(2))
})

it('does not fetch the WZDx feed when WZDx features are disabled', async () => {
  EnvironmentVars.ENABLE_WZDX_FEATURES = false
  const store = setupStore({ user: { value: { authLoginData: { token: 'testToken' } } } })
  renderMap(store)

  // The legend hides the WZDx toggle when the feature is disabled, so activate the layer via the store
  expect(screen.queryByRole('checkbox', { name: 'WZDx Viewer' })).not.toBeInTheDocument()
  act(() => {
    store.dispatch(toggleLayerActive(WZDX_LINE_LAYER_ID))
  })
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
  expect(getWzdxFeedCalls()).toHaveLength(0)
})

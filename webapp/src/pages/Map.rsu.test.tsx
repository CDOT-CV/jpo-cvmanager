import { act, render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { beforeEach, expect, it, vi } from 'vitest'
import fetchMock from 'jest-fetch-mock'
import MapPage from './Map'
import { setupStore } from '../store'
import { selectRsu } from '../generalSlices/rsuSlice'
import { testTheme } from '../styles'

// Exercise the popup content without requiring a WebGL map or Mapbox requests.
vi.mock('react-map-gl', async () => {
  const React = await import('react')
  return {
    default: React.forwardRef(({ children }: any, _ref) => <div>{children}</div>),
    Popup: ({ children }: any) => <div role="dialog">{children}</div>,
    Source: ({ children }: any) => <>{children}</>,
    Layer: () => null,
  }
})

beforeEach(() => {
  fetchMock.resetMocks()
  fetchMock.mockResponse(JSON.stringify({}))
})

it.each(['y20.48.2', null, undefined])('shows installed firmware %s in the RSU popup', (version) => {
  const store = setupStore()
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={store}><MapPage /></Provider>
    </ThemeProvider>
  )
  act(() => { store.dispatch(selectRsu({
    id: 1,
    type: 'Feature',
    geometry: { type: 'Point', coordinates: [-104.9, 39.7] },
    properties: {
      rsu_id: 1, ipv4_address: '192.0.2.1', manufacturer_name: 'Commsignia',
      serial_number: 'SN-1', primary_route: 'I-25', milepost: 1,
      firmware_version: version,
    },
  } as any)) })

  expect(screen.getByText(`Firmware: ${version || 'Unknown'}`)).toBeVisible()
})

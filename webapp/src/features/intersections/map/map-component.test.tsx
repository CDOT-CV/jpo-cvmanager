import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { testTheme } from '../../../styles'
import { setupStore } from '../../../store'
import IntersectionMap, { getTimestamp } from './map-component'

global.ResizeObserver = class {
  observe() {}
  unobserve() {}
  disconnect() {}
} as any

const mockZoomIn = vi.fn()
const mockZoomOut = vi.fn()

vi.mock('react-map-gl', async () => {
  const React = await import('react')
  const Map = React.forwardRef((props: any, ref: any) => {
    React.useImperativeHandle(ref, () => ({
      getMap: () => ({
        zoomIn: mockZoomIn,
        zoomOut: mockZoomOut,
        isStyleLoaded: () => true,
        getLayer: () => undefined,
        setLayoutProperty: vi.fn(),
      }),
      flyTo: vi.fn(),
      queryRenderedFeatures: () => [],
    }))
    return <div data-testid="mock-map">{props.children}</div>
  })
  return {
    __esModule: true,
    default: Map,
    Map,
    Source: (props: any) => <div>{props.children}</div>,
    Layer: () => <div />,
    Popup: (props: any) => <div>{props.children}</div>,
  }
})

const renderMap = () =>
  render(
    <MemoryRouter>
      <ThemeProvider theme={testTheme}>
        <Provider store={setupStore()}>
          <IntersectionMap sourceData={undefined} sourceDataType={undefined} intersectionId={undefined} />
        </Provider>
      </ThemeProvider>
    </MemoryRouter>
  )

describe('IntersectionMap', () => {
  beforeEach(() => {
    mockZoomIn.mockClear()
    mockZoomOut.mockClear()
  })

  it('renders the map', () => {
    renderMap()
    expect(screen.getByTestId('mock-map')).toBeInTheDocument()
  })

  it('zooms in when the plus button is clicked', () => {
    const { container } = renderMap()
    fireEvent.click(container.querySelector('#plus-button')!)
    expect(mockZoomIn).toHaveBeenCalledTimes(1)
  })

  it('zooms out when the minus button is clicked', () => {
    const { container } = renderMap()
    fireEvent.click(container.querySelector('#minus-button')!)
    expect(mockZoomOut).toHaveBeenCalledTimes(1)
  })
})

describe('getTimestamp', () => {
  it('parses ISO date strings to milliseconds', () => {
    expect(getTimestamp('2024-01-01T00:00:00Z')).toBe(Date.parse('2024-01-01T00:00:00Z'))
  })

  it('returns millisecond values unchanged', () => {
    expect(getTimestamp(1700000000000)).toBe(1700000000000)
  })

  it('converts second values to milliseconds', () => {
    expect(getTimestamp(1700000000)).toBe(1700000000000)
  })
})

import { ThemeProvider } from '@mui/material'
import { fireEvent, render, screen } from '@testing-library/react'
import { Children, isValidElement } from 'react'
import { Provider } from 'react-redux'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RsuApi, { type RsuState } from '../../apis/intersections/rsu-api'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import RsuStatusDialog from './RsuStatusDialog'

// Only ReferenceArea output matters here; jsdom gives the real charts zero size
vi.mock('recharts', () => {
  const ReferenceArea = ({ label }: { label: { value: string } }) => <div data-testid="reboot-zone">{label.value}</div>
  const Empty = () => null
  return {
    ReferenceArea,
    ResponsiveContainer: ({ children }: any) => <div>{children}</div>,
    ScatterChart: ({ children }: any) => (
      <div>{Children.toArray(children).filter((child) => isValidElement(child) && child.type === ReferenceArea)}</div>
    ),
    Scatter: Empty,
    XAxis: Empty,
    YAxis: Empty,
    CartesianGrid: Empty,
    Tooltip: Empty,
  }
})

const BASE = new Date('2025-12-04T00:00:00Z').getTime()
const HOUR = 3600

const state = (timestamp: number, uptime: number): RsuState => ({
  timestamp,
  intersectionID: '12109',
  rsuIP: '10.0.0.180',
  temperature: 40,
  uptime,
  mode: 1,
})

// Builds a reboot (uptime drop) where the RSU was offline for `offlineSec` seconds
const rebootAfter = (previous: RsuState, offlineSec: number, uptimeAfter = 100): RsuState =>
  state(previous.timestamp + (offlineSec + uptimeAfter) * 1000, uptimeAfter)

const renderAndGenerate = async (data: RsuState[]) => {
  vi.spyOn(RsuApi, 'getLatestRsuStatus').mockResolvedValue(undefined)
  vi.spyOn(RsuApi, 'getAggregatedRsuStatus').mockResolvedValue(data)

  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({ user: { value: { authLoginData: { token: '' } } } })}>
        <RsuStatusDialog open={true} onClose={() => {}} rsuIp="10.0.0.180" token="test-token" />
      </Provider>
    </ThemeProvider>
  )

  fireEvent.click(await screen.findByRole('button', { name: 'Generate' }))
  await screen.findAllByRole('heading', { name: /Uptime for/ })
}

const zoneLabels = () => screen.queryAllByTestId('reboot-zone').map((zone) => zone.textContent)

beforeEach(() => {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockReturnValue({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })
  )
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('reboot zones', () => {
  it('renders no zones when uptime only increases', async () => {
    await renderAndGenerate([state(BASE, 100), state(BASE + 1000 * 100, 200), state(BASE + 1000 * 200, 300)])

    expect(zoneLabels()).toEqual([])
  })

  it('renders no zones when uptime is unchanged between points', async () => {
    await renderAndGenerate([state(BASE, 500), state(BASE + 1000 * 100, 500)])

    expect(zoneLabels()).toEqual([])
  })

  it('labels offline periods under an hour in minutes', async () => {
    const first = state(BASE, 10 * HOUR)
    await renderAndGenerate([first, rebootAfter(first, 30 * 60)])

    expect(zoneLabels()).toEqual(['Offline 30 min'])
  })

  it('labels a zero-length offline period as 0 min', async () => {
    const first = state(BASE, 10 * HOUR)
    await renderAndGenerate([first, rebootAfter(first, 0)])

    expect(zoneLabels()).toEqual(['Offline 0 min'])
  })

  it('labels offline periods of an hour or more in hours and minutes', async () => {
    const first = state(BASE, 10 * HOUR)
    await renderAndGenerate([first, rebootAfter(first, 2 * HOUR + 5 * 60)])

    expect(zoneLabels()).toEqual(['Offline 2 hr 5 min'])
  })

  it('labels an exact hour with 0 minutes', async () => {
    const first = state(BASE, 10 * HOUR)
    await renderAndGenerate([first, rebootAfter(first, HOUR)])

    expect(zoneLabels()).toEqual(['Offline 1 hr 0 min'])
  })

  it('uses the singular for a single day', async () => {
    const first = state(BASE, 10 * HOUR)
    await renderAndGenerate([first, rebootAfter(first, 24 * HOUR)])

    expect(zoneLabels()).toEqual(['Offline 1 day'])
  })

  it('uses the plural for multiple days and ignores leftover hours', async () => {
    const first = state(BASE, 10 * HOUR)
    await renderAndGenerate([first, rebootAfter(first, 3 * 24 * HOUR + 5 * HOUR)])

    expect(zoneLabels()).toEqual(['Offline 3 days'])
  })

  it('renders one zone per reboot', async () => {
    const first = state(BASE, 10 * HOUR)
    const second = rebootAfter(first, 30 * 60)
    const third = state(second.timestamp + 1000 * 1000, second.uptime + 1000)
    const fourth = rebootAfter(third, 2 * HOUR)
    await renderAndGenerate([first, second, third, fourth])

    expect(zoneLabels()).toEqual(['Offline 30 min', 'Offline 2 hr 0 min'])
  })
})

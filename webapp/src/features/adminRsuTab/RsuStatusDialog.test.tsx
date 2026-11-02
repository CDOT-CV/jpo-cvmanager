import { ThemeProvider } from '@mui/material'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import RsuApi, { type RsuState } from '../../apis/intersections/rsu-api'
import { Provider } from 'react-redux'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import { replaceChaoticIds } from '../../utils/test-utils'
import RsuStatusDialog from './RsuStatusDialog'

beforeAll(() => {
  // jsdom doesn't implement ResizeObserver, which recharts' ResponsiveContainer requires
  global.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as any
  // Mock the Date object to always return a fixed date
  // Only fake Date so testing-library's async polling keeps working
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2025-12-05T00:00:00Z'))
})

afterAll(() => {
  // Restore the Date object to its original behavior
  vi.useRealTimers()
})

it('should take a snapshot', () => {
  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          user: { value: { authLoginData: { token: '' } } },
        })}
      >
        <RsuStatusDialog open={true} onClose={() => {}} rsuIp="10.0.0.180" token="test-token" />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(document.body)).toMatchSnapshot()
})

it('should take a snapshot with historical data', async () => {
  const base = new Date('2025-12-04T00:00:00Z').getTime()
  const historicalData: RsuState[] = [0, 1, 2, 3].map((i) => ({
    timestamp: base + i * 3600 * 1000,
    intersectionID: '12109',
    rsuIP: '10.0.0.180',
    temperature: 40 + i * 2,
    uptime: 3600 * (i + 1),
    mode: 1,
  }))
  vi.spyOn(RsuApi, 'getLatestRsuStatus').mockResolvedValue(historicalData[historicalData.length - 1])
  vi.spyOn(RsuApi, 'getAggregatedRsuStatus').mockResolvedValue(historicalData)

  render(
    <ThemeProvider theme={testTheme}>
      <Provider
        store={setupStore({
          user: { value: { authLoginData: { token: '' } } },
        })}
      >
        <RsuStatusDialog open={true} onClose={() => {}} rsuIp="10.0.0.180" token="test-token" />
      </Provider>
    </ThemeProvider>
  )

  fireEvent.click(await screen.findByRole('button', { name: 'Generate' }))
  await waitFor(() => expect(RsuApi.getAggregatedRsuStatus).toHaveBeenCalled())
  await act(async () => {})

  expect(replaceChaoticIds(document.body)).toMatchSnapshot()
})

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import SnmpwalkMenu from './SnmpwalkMenu'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { setupStore } from '../store'
import { replaceChaoticIds } from '../utils/test-utils'
import toast from 'react-hot-toast'
import EnvironmentVars from '../EnvironmentVars'
import { combineUrlPaths } from '../apis/intersections/api-helper-cviz'

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <SnmpwalkMenu />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})


it('loads cache, falls back after a failed live read, and labels a successful live refresh', async () => {
  fetchMock.mockClear()
  fetchMock.doMock()
  const row = {
    table: 'TRANSMIT', index: 1, messageType: 'MAP', destinationIp: '198.51.100.3', destinationPort: 44920,
    startDatetime: '2026-10-15T10:00:00-06:00', endDatetime: '2036-10-15T10:00:00-06:00', active: true, includeSecurity: true,
  }
  fetchMock.mockResponses(
    [JSON.stringify([row]), { status: 200 }],
    [JSON.stringify({ detail: 'Device did not respond.' }), { status: 504 }],
    [JSON.stringify([row]), { status: 200 }],
    [JSON.stringify([{ ...row, destinationIp: '198.51.100.4' }]), { status: 200 }]
  )
  const failure = jest.spyOn(toast, 'error').mockReturnValue('fixture-toast')
  const success = jest.spyOn(toast, 'success').mockReturnValue('fixture-toast')
  try {
    const store = setupStore({
      rsu: { value: { selectedRsu: { properties: { ipv4_address: '192.0.2.10' } } } },
      user: { value: { authLoginData: { token: 'fixture-token' }, organization: { name: 'Org 1' } } },
    })
    render(<ThemeProvider theme={testTheme}><Provider store={store}><SnmpwalkMenu /></Provider></ThemeProvider>)
    await screen.findByText('198.51.100.3')
    expect(screen.getByText(/Source:/)).toHaveTextContent('Source: Cached (Database)')
    fireEvent.click(screen.getByRole('button', { name: 'Refresh Message Forwarding' }))
    await waitFor(() => expect(failure).toHaveBeenCalledWith('Failed to fetch RSU message forwarding configuration, loading cached data'))
    await screen.findByText('198.51.100.3')
    expect(screen.getByText(/Source:/)).toHaveTextContent('Source: Cached (Database)')
    fireEvent.click(screen.getByRole('button', { name: 'Refresh Message Forwarding' }))
    await screen.findByText('198.51.100.4')
    expect(screen.getByText(/Source:/)).toHaveTextContent('Source: Live (RSU)')
    await waitFor(() => expect(success).toHaveBeenCalledWith('Successfully fetched RSU message forwarding configuration'))
    const base = combineUrlPaths(EnvironmentVars.CVIZ_API_SERVER_URL!, '/devices/rsus/message-forwarding/192.0.2.10')
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([base, base + '/live', base, base + '/live'])
  } finally {
    failure.mockRestore()
    success.mockRestore()
  }
})

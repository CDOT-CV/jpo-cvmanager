import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import SnmpwalkMenu from './SnmpwalkMenu'
import RsuMessageForwardingApi from '../apis/intersections/rsu-message-forwarding-api'
import { selectRsu } from '../generalSlices/rsuSlice'
import { act } from '@testing-library/react'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { setupStore } from '../store'
import { replaceChaoticIds } from '../utils/test-utils'
import toast from 'react-hot-toast'
import * as confirmation from 'react-confirm-alert'
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
    table: 'TRANSMIT',
    index: 1,
    messageType: 'MAP',
    destinationIp: '198.51.100.3',
    destinationPort: 44920,
    startDatetime: '2026-10-15T10:00:00-06:00',
    endDatetime: '2036-10-15T10:00:00-06:00',
    active: true,
    includeSecurity: true,
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
      rsu: {
        value: {
          selectedRsu: {
            properties: { ipv4_address: '192.0.2.10', snmp_protocol_code: '1218', owner_organization_id: 1 },
          },
        },
      },
      user: {
        value: {
          authLoginData: { token: 'fixture-token' },
          organization: { name: 'Org 1', organization: 1, role: 'OPERATOR' },
        },
      },
    })
    render(
      <ThemeProvider theme={testTheme}>
        <Provider store={store}>
          <SnmpwalkMenu />
        </Provider>
      </ThemeProvider>
    )
    await screen.findByText('198.51.100.3')
    expect(screen.getByText(/Source:/)).toHaveTextContent('Source: Cached (Database)')
    fireEvent.click(screen.getByRole('button', { name: 'Refresh Message Forwarding' }))
    await waitFor(() =>
      expect(failure).toHaveBeenCalledWith('Failed to fetch RSU message forwarding configuration, loading cached data')
    )
    await screen.findByText('198.51.100.3')
    expect(screen.getByText(/Source:/)).toHaveTextContent('Source: Cached (Database)')
    fireEvent.click(screen.getByRole('button', { name: 'Refresh Message Forwarding' }))
    await screen.findByText('198.51.100.4')
    expect(screen.getByText(/Source:/)).toHaveTextContent('Source: Live (RSU)')
    await waitFor(() =>
      expect(success).toHaveBeenCalledWith('Successfully fetched RSU message forwarding configuration')
    )
    const base = combineUrlPaths(EnvironmentVars.CVIZ_API_SERVER_URL!, '/devices/rsus/message-forwarding/192.0.2.10')
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([base, base + '/live', base, base + '/live'])
  } finally {
    failure.mockRestore()
    success.mockRestore()
  }
})

it.each(['41', '1218'])('shows cached %s rows to a user without delete controls', async (protocol) => {
  fetchMock.mockClear()
  fetchMock.doMock()
  fetchMock.mockResponseOnce(
    JSON.stringify([
      {
        table: protocol === '41' ? 'DSRC' : 'RECEIVE',
        index: 1,
        messageType: 'BSM',
        destinationIp: '198.51.100.3',
        destinationPort: 44900,
        active: true,
      },
    ])
  )
  const store = setupStore({
    rsu: {
      value: {
        selectedRsu: {
          properties: { ipv4_address: '192.0.2.10', snmp_protocol_code: protocol, owner_organization_id: 1 },
        },
      },
    },
    user: {
      value: {
        authLoginData: { token: 'fixture-token' },
        organization: { name: 'Org', organization: 1, role: 'USER' },
      },
    },
  })
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={store}>
        <SnmpwalkMenu />
      </Provider>
    </ThemeProvider>
  )
  await screen.findByText('198.51.100.3')
  expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  if (protocol === '41') {
    expect(screen.queryByRole('button', { name: 'Refresh Message Forwarding' })).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  } else {
    expect(screen.getByRole('button', { name: 'Refresh Message Forwarding' })).toBeInTheDocument()
  }
})

it('does not fall back to the old RSU after a late live-read failure', async () => {
  let finishRead: (response: any) => void
  const live = jest.spyOn(RsuMessageForwardingApi, 'live').mockImplementation(
    () =>
      new Promise((resolve) => {
        finishRead = resolve
      })
  )
  const cached = jest
    .spyOn(RsuMessageForwardingApi, 'cached')
    .mockResolvedValue({ status: 200, message: '', body: { RsuFwdSnmpwalk: {} } })
  const device = (ip: string) => ({
    properties: { ipv4_address: ip, snmp_protocol_code: '1218', owner_organization_id: 1 },
  })
  const first = device('192.0.2.10')
  const second = device('192.0.2.11')
  const store = setupStore({
    rsu: { value: { selectedRsu: first, rsuData: [first, second] } },
    user: {
      value: {
        authLoginData: { token: 'fixture-token' },
        organization: { name: 'Org', organization: 1, role: 'USER' },
      },
    },
  })
  try {
    render(
      <ThemeProvider theme={testTheme}>
        <Provider store={store}>
          <SnmpwalkMenu />
        </Provider>
      </ThemeProvider>
    )
    await waitFor(() => expect(cached).toHaveBeenCalledTimes(1))
    fireEvent.click(screen.getByRole('button', { name: 'Refresh Message Forwarding' }))
    act(() => {
      store.dispatch(selectRsu(second as any))
    })
    await waitFor(() => expect(cached).toHaveBeenCalledTimes(2))
    await act(async () => {
      finishRead({ status: 504, message: 'Offline', body: null })
    })
    expect(cached.mock.calls.map((call) => call[2])).toEqual(['192.0.2.10', '192.0.2.11'])
    expect(store.getState().config.value.errorState).toBe('')
  } finally {
    live.mockRestore()
    cached.mockRestore()
  }
})

it.each([
  ['RECEIVE', 'MAP'],
  ['TRANSMIT', 'OTHER'],
])('deletes displayed %s %s rows by table/index', async (table, messageType) => {
  fetchMock.mockClear()
  fetchMock.doMock()
  const row = { table, index: 7, messageType, destinationIp: '198.51.100.3', destinationPort: 44920, active: true }
  fetchMock.mockResponses([JSON.stringify([row]), { status: 200 }], ['[]', { status: 200 }])
  const remove = jest.spyOn(RsuMessageForwardingApi, 'deleteRow').mockResolvedValue({
    status: 200,
    message: '',
    body: {
      '192.0.2.10': {
        execution: {
          taskId: 'task',
          target: 'udp:192.0.2.10:161',
          status: 'SUCCEEDED',
          message: 'Change verified.',
          step: null,
          elapsedMs: 1,
          steps: [],
        },
        writeState: 'VERIFIED',
        changed: true,
        databaseSync: 'UPDATED',
      },
    },
  })
  const confirm = jest.spyOn(confirmation, 'confirmAlert').mockImplementation((options) => {
    options.buttons![0].onClick!()
  })
  const store = setupStore({
    rsu: {
      value: {
        selectedRsu: {
          properties: { ipv4_address: '192.0.2.10', snmp_protocol_code: '1218', owner_organization_id: 1 },
        },
      },
    },
    user: {
      value: {
        authLoginData: { token: 'fixture-token' },
        organization: { name: 'Org', organization: 1, role: 'OPERATOR' },
      },
    },
  })
  try {
    render(
      <ThemeProvider theme={testTheme}>
        <Provider store={store}>
          <SnmpwalkMenu />
        </Provider>
      </ThemeProvider>
    )
    await screen.findByText('198.51.100.3')
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    await waitFor(() =>
      expect(remove).toHaveBeenCalledWith('fixture-token', 'Org', '192.0.2.10', table, 7, {
        destination_ip: '198.51.100.3',
        destination_port: 44920,
      })
    )
    await waitFor(() => expect(screen.queryByText('198.51.100.3')).not.toBeInTheDocument())
  } finally {
    remove.mockRestore()
    confirm.mockRestore()
  }
})

it('shows every mixed legacy and current cached row, keeping legacy rows read only', async () => {
  fetchMock.mockClear()
  fetchMock.doMock()
  const common = { index: 1, messageType: 'MAP', destinationPort: 44920, active: true }
  fetchMock.mockResponseOnce(
    JSON.stringify([
      { ...common, table: 'DSRC', destinationIp: '198.51.100.11' },
      { ...common, table: 'TRANSMIT', destinationIp: '198.51.100.12' },
    ])
  )
  const store = setupStore({
    rsu: {
      value: {
        selectedRsu: {
          properties: { ipv4_address: '192.0.2.10', snmp_protocol_code: '1218', owner_organization_id: 1 },
        },
      },
    },
    user: {
      value: {
        authLoginData: { token: 'fixture-token' },
        organization: { name: 'Org', organization: 1, role: 'OPERATOR' },
      },
    },
  })
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={store}>
        <SnmpwalkMenu />
      </Provider>
    </ThemeProvider>
  )
  await screen.findByText('198.51.100.11')
  expect(screen.getByText('198.51.100.12')).toBeInTheDocument()
  expect(screen.getByText('Legacy RSU 4.1 Forward Table (cached, read only)')).toBeInTheDocument()
  expect(screen.getAllByRole('button', { name: 'Delete' })).toHaveLength(1)
})

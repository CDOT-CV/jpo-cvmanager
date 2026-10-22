import RsuMessageForwardingApi from './rsu-message-forwarding-api'
import EnvironmentVars from '../../EnvironmentVars'
import { combineUrlPaths } from './api-helper-cviz'

beforeEach(() => {
  fetchMock.mockClear()
  fetchMock.doMock()
})

describe.each(['create', 'delete'] as const)('%s forwarding', (operation) => {
  it('uses JSON, bearer authentication and selected organization for the new endpoint', async () => {
    const body = {
      rsu_ips: ['192.0.2.10'],
      message_type: 'MAP' as const,
      destination_ip: '198.51.100.3',
      include_security: true,
    }
    const response = { '192.0.2.10': { execution: { status: 'SUCCEEDED' }, databaseSync: 'UPDATED' } }
    fetchMock.mockResponseOnce(JSON.stringify(response))
    const actual = await RsuMessageForwardingApi[operation]('fixture-token', 'Org 1', body)
    expect(actual).toEqual({ body: response, status: 200, message: '' })
    expect(fetchMock.mock.calls[0][0]).toBe(
      combineUrlPaths(EnvironmentVars.CVIZ_API_SERVER_URL!, '/devices/rsus/message-forwarding')
    )
    expect(fetchMock.mock.calls[0][1]).toEqual(
      expect.objectContaining({
        method: operation === 'create' ? 'POST' : 'DELETE',
        headers: { Authorization: 'Bearer fixture-token', Organization: 'Org 1', 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
    )
  })

  it('preserves server problem details', async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ detail: 'NTCIP 1218 is required for all RSUs.' }), { status: 400 })
    const result = await RsuMessageForwardingApi[operation]('fixture-token', 'Org 1', {
      rsu_ips: ['192.0.2.10'],
      message_type: 'MAP',
      destination_ip: '198.51.100.3',
      include_security: false,
    })
    expect(result).toEqual({ body: null, status: 400, message: 'NTCIP 1218 is required for all RSUs.' })
  })
})

describe.each(['cached', 'live'] as const)('%s forwarding read', (source) => {
  const row = {
    table: 'TRANSMIT',
    index: 2,
    messageType: 'MAP',
    destinationIp: '198.51.100.3',
    destinationPort: 44920,
    startDatetime: '2026-10-15T10:00:00-06:00',
    endDatetime: '2036-10-15T10:00:00-06:00',
    active: true,
    includeSecurity: false,
  }

  it('uses the new GET route and preserves the existing RX/TX display shape and dates', async () => {
    fetchMock.mockResponseOnce(JSON.stringify([row]))
    const result = await RsuMessageForwardingApi[source]('fixture-token', 'Org 1', '192.0.2.10')
    expect(fetchMock.mock.calls[0][0]).toBe(
      combineUrlPaths(
        EnvironmentVars.CVIZ_API_SERVER_URL!,
        `/devices/rsus/message-forwarding/192.0.2.10${source === 'live' ? '/live' : ''}`
      )
    )
    expect(fetchMock.mock.calls[0][1]).toEqual(
      expect.objectContaining({
        method: 'GET',
        headers: { Authorization: 'Bearer fixture-token', Organization: 'Org 1' },
        body: undefined,
      })
    )
    expect(result.body.RsuFwdSnmpwalk).toEqual({
      rsuReceivedMsgTable: {},
      rsuXmitMsgFwdingTable: {
        2: {
          table: 'TRANSMIT',
          index: 2,
          'Message Type': 'MAP',
          IP: '198.51.100.3',
          Port: 44920,
          'Start DateTime': row.startDatetime,
          'End DateTime': row.endDatetime,
          'Config Active': 'Enabled',
          'Full WSMP': 'Disabled',
        },
      },
    })
  })

  it('distinguishes an empty snapshot from an HTTP error', async () => {
    fetchMock.mockResponseOnce('[]')
    expect((await RsuMessageForwardingApi[source]('fixture-token', 'Org 1', '192.0.2.10')).body.RsuFwdSnmpwalk).toEqual(
      {}
    )
    fetchMock.mockResponseOnce(JSON.stringify({ detail: 'Device did not respond.' }), { status: 504 })
    expect(await RsuMessageForwardingApi[source]('fixture-token', 'Org 1', '192.0.2.10')).toEqual({
      status: 504,
      body: null,
      message: 'Device did not respond.',
    })
  })

  it('rejects malformed data rather than showing an empty successful snapshot', async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ unexpected: true }))
    await expect(RsuMessageForwardingApi[source]('fixture-token', 'Org 1', '192.0.2.10')).rejects.toThrow(
      'Invalid forwarding configuration response'
    )
  })
})

it('keeps cached DSRC rows viewable without turning them into NTCIP tables', async () => {
  fetchMock.mockResponseOnce(
    JSON.stringify([
      {
        table: 'DSRC',
        index: 1,
        messageType: 'BSM',
        destinationIp: '198.51.100.3',
        destinationPort: 46800,
        startDatetime: '2026-10-15T10:00:00-06:00',
        endDatetime: '2036-10-15T10:00:00-06:00',
        active: false,
        includeSecurity: true,
      },
    ])
  )
  const result = await RsuMessageForwardingApi.cached('fixture-token', 'Org 1', '192.0.2.10')
  expect(result.body.RsuFwdSnmpwalk).toEqual({
    1: expect.objectContaining({
      'Message Type': 'BSM',
      'Config Active': 'Disabled',
      'Full WSMP': 'Enabled',
    }),
  })
})

it('deletes an existing row by its actual table/index without a message preset', async () => {
  fetchMock.mockResponseOnce(JSON.stringify({ '192.0.2.10': {} }))
  await RsuMessageForwardingApi.deleteRow('fixture-token', 'Org 1', '192.0.2.10', 'RECEIVE', 255, {
    destination_ip: '198.51.100.3',
    destination_port: 44920,
  })
  expect(fetchMock.mock.calls[0][0]).toBe(
    combineUrlPaths(
      EnvironmentVars.CVIZ_API_SERVER_URL!,
      '/devices/rsus/message-forwarding/192.0.2.10/rows/RECEIVE/255'
    )
  )
  expect(fetchMock.mock.calls[0][1]).toEqual(
    expect.objectContaining({
      method: 'DELETE',
      headers: { Authorization: 'Bearer fixture-token', Organization: 'Org 1', 'Content-Type': 'application/json' },
      body: JSON.stringify({ destination_ip: '198.51.100.3', destination_port: 44920 }),
    })
  )
})

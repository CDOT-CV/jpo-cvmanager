import RsuRebootApi from './rsu-reboot-api'
import EnvironmentVars from '../../EnvironmentVars'
import { combineUrlPaths } from './api-helper-cviz'

beforeEach(() => {
  fetchMock.mockClear()
  fetchMock.doMock()
})

it('posts to the reboot route with the selected organization and bearer token', async () => {
  fetchMock.mockResponseOnce(JSON.stringify({ requestId: 'test-request', status: 'ACCEPTED' }), { status: 202 })
  expect(await RsuRebootApi.reboot('fixture-token', 'Test Org', '192.0.2.10')).toEqual({
    requestId: 'test-request', status: 'ACCEPTED',
  })
  expect(fetchMock.mock.calls[0]).toEqual([
    combineUrlPaths(EnvironmentVars.CVIZ_API_SERVER_URL!, '/devices/rsus/reboot/192.0.2.10'),
    expect.objectContaining({ method: 'POST', headers: { Organization: 'Test Org', Authorization: 'Bearer fixture-token' } }),
  ])
})

it('preserves API rejection details', async () => {
  fetchMock.mockResponseOnce(JSON.stringify({ detail: 'A reboot request is already running for this RSU' }), { status: 409 })
  await expect(RsuRebootApi.reboot('fixture-token', 'Test Org', '192.0.2.10')).rejects.toThrow('already running')
})

it('does not treat an unexpected successful response as acceptance', async () => {
  fetchMock.mockResponseOnce('{}')
  await expect(RsuRebootApi.reboot('fixture-token', 'Test Org', '192.0.2.10')).rejects.toThrow('not confirmed')
})

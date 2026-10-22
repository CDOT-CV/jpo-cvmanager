import RsuRebootApi from './rsu-reboot-api'
import EnvironmentVars from '../../EnvironmentVars'
import { authApiHelper, combineUrlPaths } from './api-helper-cviz'
import toast from 'react-hot-toast'

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

it.each([401, 403])('leaves HTTP %i reboot notifications to the menu', async (status) => {
  const notification = jest.spyOn(toast, 'error')
  try {
    fetchMock.mockResponseOnce(JSON.stringify({ detail: 'Reboot access denied' }), { status })
    await expect(RsuRebootApi.reboot('fixture-token', 'Test Org', '192.0.2.10')).rejects.toThrow('Reboot access denied')
    expect(notification).not.toHaveBeenCalled()
  } finally {
    notification.mockRestore()
  }
})

it('leaves reboot network failure notifications to the menu', async () => {
  const notification = jest.spyOn(toast, 'error')
  try {
    fetchMock.mockRejectOnce(new Error('Network unavailable'))
    await expect(RsuRebootApi.reboot('fixture-token', 'Test Org', '192.0.2.10')).rejects.toThrow('not confirmed')
    expect(notification).not.toHaveBeenCalled()
  } finally {
    notification.mockRestore()
  }
})

it.each([401, 403])('keeps default HTTP %i notifications for other API callers', async (status) => {
  const notification = jest.spyOn(toast, 'error')
  try {
    fetchMock.mockResponseOnce('{}', { status })
    await authApiHelper.invokeApi({ path: '/devices/rsus', tag: 'rsu' })
    expect(notification).toHaveBeenCalledTimes(1)
  } finally {
    notification.mockRestore()
  }
})

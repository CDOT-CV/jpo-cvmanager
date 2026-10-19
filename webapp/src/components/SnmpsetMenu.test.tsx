import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import SnmpsetMenu from './SnmpsetMenu'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { setupStore } from '../store'
import { replaceChaoticIds } from '../utils/test-utils'
import { setDestIp, setMsgType } from '../generalSlices/configSlice'
import RsuMessageForwardingApi from '../apis/intersections/rsu-message-forwarding-api'
import toast from 'react-hot-toast'

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <SnmpsetMenu type={undefined} rsuIpList={undefined} />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})


describe('forwarding actions', () => {
  afterEach(() => jest.restoreAllMocks())

  const renderMenu = () => {
    const store = setupStore({})
    store.dispatch(setDestIp('198.51.100.3'))
    store.dispatch(setMsgType('map'))
    render(
      <ThemeProvider theme={testTheme}>
        <Provider store={store}>
          <SnmpsetMenu type="bulk" rsuIpList={['192.0.2.10', '192.0.2.11']} />
        </Provider>
      </ThemeProvider>
    )
    return store
  }

  describe.each(['create', 'delete'] as const)('%s', (operation) => {
    it('preserves the controls and success notification, including a delete no-op', async () => {
      const api = jest.spyOn(RsuMessageForwardingApi, operation).mockResolvedValue({
        status: 200, message: '', body: Object.fromEntries(['192.0.2.10', '192.0.2.11'].map((ip) => [ip, {
          execution: { taskId: 'task', target: `udp:${ip}:161`, status: 'SUCCEEDED', message: 'Completed.', step: null, elapsedMs: 1, steps: [] },
          writeState: operation === 'create' ? 'VERIFIED' : 'NOT_SUBMITTED',
          changed: operation === 'create', databaseSync: 'UPDATED',
        }])),
      })
      const success = jest.spyOn(toast, 'success').mockReturnValue('fixture-toast')
      renderMenu()
      expect(screen.getByLabelText('Destination IP')).toHaveValue('198.51.100.3')
      fireEvent.click(screen.getByLabelText('Security Header'))
      fireEvent.click(screen.getByRole('button', { name: operation === 'create' ? 'Add Forwarding' : 'Delete Forwarding' }))
      await waitFor(() => expect(success).toHaveBeenCalledWith(
        operation === 'create' ? 'Forwarding Changes Applied Successfully' : 'Successfully deleted SNMP forwarding'
      ))
      expect(api.mock.calls[0][2]).toEqual({
        rsu_ips: ['192.0.2.10', '192.0.2.11'], message_type: 'MAP', destination_ip: '198.51.100.3',
        ...(operation === 'create' ? { include_security: true } : {}),
      })
    })

    it('shows the failed RSU instead of a success notification for a mixed result', async () => {
      jest.spyOn(RsuMessageForwardingApi, operation).mockResolvedValue({ status: 200, message: '', body: {
        '192.0.2.10': {
          execution: { taskId: 'task', target: 'udp:192.0.2.10:161', status: 'SUCCEEDED', message: 'Change verified.', step: null, elapsedMs: 1, steps: [] },
          writeState: 'VERIFIED', changed: true, databaseSync: 'UPDATED',
        },
      } })
      const success = jest.spyOn(toast, 'success').mockReturnValue('fixture-toast')
      const error = jest.spyOn(toast, 'error').mockReturnValue('fixture-toast')
      renderMenu()
      fireEvent.click(screen.getByRole('button', { name: operation === 'create' ? 'Add Forwarding' : 'Delete Forwarding' }))
      await waitFor(() => expect(error).toHaveBeenCalledWith(expect.stringContaining('192.0.2.11')))
      expect(success).not.toHaveBeenCalled()
      expect(error).toHaveBeenCalledWith(expect.stringContaining('check RSU state before retrying'))
    })
  })

  it('handles rejected thunks without trying to read a missing payload', async () => {
    const error = jest.spyOn(toast, 'error').mockReturnValue('fixture-toast')
    const store = renderMenu()
    store.dispatch(setMsgType('Other'))
    fireEvent.click(screen.getByRole('button', { name: 'Add Forwarding' }))
    await waitFor(() => expect(error).toHaveBeenCalledWith(expect.stringContaining('Unsupported forwarding message type')))
  })
})

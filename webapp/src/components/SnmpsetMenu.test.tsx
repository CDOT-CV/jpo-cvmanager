import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import SnmpsetMenu from './SnmpsetMenu'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { setupStore } from '../store'
import { replaceChaoticIds } from '../utils/test-utils'
import { setDestIp, setMsgType } from '../generalSlices/configSlice'
import RsuMessageForwardingApi from '../apis/intersections/rsu-message-forwarding-api'
import toast from 'react-hot-toast'
import { vi } from 'vitest'

vi.mock('react-hot-toast', () => ({
  default: Object.assign(jest.fn(), { success: jest.fn(), error: jest.fn() }),
}))

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
  beforeEach(() => jest.clearAllMocks())
  afterEach(() => jest.restoreAllMocks())

  const renderMenu = (type = 'bulk', ips = ['192.0.2.10', '192.0.2.11'], skippedIps: string[] = []) => {
    const store = setupStore({})
    store.dispatch(setDestIp('198.51.100.3'))
    store.dispatch(setMsgType('map'))
    render(
      <ThemeProvider theme={testTheme}>
        <Provider store={store}>
          <SnmpsetMenu type={type} rsuIpList={ips} skippedIps={skippedIps} />
        </Provider>
      </ThemeProvider>
    )
    return store
  }

  describe.each(['create', 'delete'] as const)('%s', (operation) => {
    it('keeps successful bulk results visible, including unchanged devices', async () => {
      const api = jest.spyOn(RsuMessageForwardingApi, operation).mockResolvedValue({
        status: 200,
        message: '',
        body: Object.fromEntries(
          ['192.0.2.10', '192.0.2.11'].map((ip) => [
            ip,
            {
              execution: {
                taskId: 'task',
                target: `udp:${ip}:161`,
                status: 'SUCCEEDED',
                message: 'Completed.',
                step: null,
                elapsedMs: 1,
                steps: [],
              },
              writeState: operation === 'create' ? 'VERIFIED' : 'NOT_SUBMITTED',
              changed: operation === 'create',
              databaseSync: 'UPDATED',
            },
          ])
        ),
      })
      const success = jest.spyOn(toast, 'success').mockReturnValue('fixture-toast')
      renderMenu()
      expect(screen.getByRole('textbox', { name: /Destination IPv4/ })).toHaveValue('198.51.100.3')
      fireEvent.click(screen.getByLabelText('Security Header'))
      fireEvent.click(
        screen.getByRole('button', { name: operation === 'create' ? 'Add Forwarding' : 'Delete Forwarding' })
      )
      await screen.findByRole('region', { name: 'Forwarding results' })
      expect(screen.getByRole('status')).toHaveTextContent('2 successful \u00b7 0 need attention')
      expect(screen.getByLabelText(`192.0.2.10: ${operation === 'create' ? 'Added' : 'Already absent'}`)).toHaveAttribute('tabindex', '0')
      expect(success).not.toHaveBeenCalled()
      // Editing the form must not relabel a completed operation.
      fireEvent.change(screen.getByRole('textbox', { name: /Destination IPv4/ }), { target: { value: '198.51.100.8' } })
      expect(screen.getByRole('region', { name: 'Forwarding results' })).toHaveTextContent('198.51.100.3')
      fireEvent.click(screen.getByRole('button', { name: 'Dismiss forwarding results' }))
      expect(screen.queryByRole('region', { name: 'Forwarding results' })).not.toBeInTheDocument()
      expect(api.mock.calls[0][2]).toEqual({
        rsu_ips: ['192.0.2.10', '192.0.2.11'],
        message_type: 'MAP',
        destination_ip: '198.51.100.3',
        ...(operation === 'create' ? { include_security: true } : {}),
      })
    })

    it('lists legacy omissions separately from requested device results', async () => {
      const api = jest.spyOn(RsuMessageForwardingApi, operation).mockResolvedValue({
        status: 200, message: '', body: {
          '192.0.2.10': {
            execution: { taskId: 'task', target: 'udp:192.0.2.10:161', status: 'SUCCEEDED',
              message: 'Completed.', step: null, elapsedMs: 1, steps: [] },
            changed: true, writeState: 'VERIFIED', databaseSync: 'UPDATED',
          },
        },
      })
      renderMenu('bulk', ['192.0.2.10'], ['192.0.2.11'])
      fireEvent.click(screen.getByText('Skipped (legacy SNMP): 1'))
      expect(screen.getByText('192.0.2.11')).toBeVisible()
      fireEvent.click(screen.getByRole('button', { name: operation === 'create' ? 'Add Forwarding' : 'Delete Forwarding' }))
      await screen.findByRole('region', { name: 'Forwarding results' })
      expect(api.mock.calls[0][2].rsu_ips).toEqual(['192.0.2.10'])
      expect(screen.getByRole('status')).toHaveTextContent('1 successful \u00b7 0 need attention')
      const results = screen.getByRole('region', { name: 'Forwarding results' })
      expect(within(results).queryByText('192.0.2.11')).not.toBeInTheDocument()
      expect(within(results).getAllByRole('row')).toHaveLength(2)
      expect(screen.getByText('192.0.2.11')).toBeVisible()
    })

    it('shows the failed RSU instead of a success notification for a mixed result', async () => {
      jest.spyOn(RsuMessageForwardingApi, operation).mockResolvedValue({
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
      const success = jest.spyOn(toast, 'success').mockReturnValue('fixture-toast')
      const error = jest.spyOn(toast, 'error').mockReturnValue('fixture-toast')
      renderMenu()
      fireEvent.click(
        screen.getByRole('button', { name: operation === 'create' ? 'Add Forwarding' : 'Delete Forwarding' })
      )
      await screen.findByRole('region', { name: 'Forwarding results' })
      expect(screen.getByRole('status')).toHaveTextContent('1 successful \u00b7 1 need attention')
      expect(screen.getByRole('row', { name: /192.0.2.11/ })).toBe(screen.getAllByRole('row')[1])
      const failed = screen.getByLabelText('192.0.2.11: No response; check device before retrying')
      fireEvent.mouseOver(failed)
      expect(await screen.findByRole('tooltip')).toHaveTextContent('No response; check device before retrying')
      expect(success).not.toHaveBeenCalled()
      expect(error).not.toHaveBeenCalled()
    })
  })

  it('disables both bulk actions when every selected device is legacy', () => {
    const create = jest.spyOn(RsuMessageForwardingApi, 'create')
    const remove = jest.spyOn(RsuMessageForwardingApi, 'delete')
    renderMenu('bulk', [], ['192.0.2.11'])
    expect(screen.getByText('No NTCIP 1218 RSUs selected.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add Forwarding' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Delete Forwarding' })).toBeDisabled()
    expect(create).not.toHaveBeenCalled()
    expect(remove).not.toHaveBeenCalled()
  })

  it('uses a specific single-device duplicate message', async () => {
    jest.spyOn(RsuMessageForwardingApi, 'create').mockResolvedValue({
      status: 200, message: '', body: {
        '192.0.2.10': {
          execution: { taskId: 'task', target: 'udp:192.0.2.10:161', status: 'SUCCEEDED',
            message: 'No change needed.', step: null, elapsedMs: 1, steps: [] },
          writeState: 'NOT_SUBMITTED', changed: false, databaseSync: 'UPDATED',
        },
      },
    })
    renderMenu('single_rsu', ['192.0.2.10'])
    fireEvent.click(screen.getByRole('button', { name: 'Add Forwarding' }))
    await waitFor(() => expect(toast).toHaveBeenCalledWith('This forwarding configuration already exists.'))
    expect(toast.success).not.toHaveBeenCalled()
    expect(toast.error).not.toHaveBeenCalled()
  })

  it.each(['single_rsu', 'bulk'])('requires a valid destination and message type in %s mode', (type) => {
    const api = jest.spyOn(RsuMessageForwardingApi, 'create')
    const store = renderMenu(type)
    const destination = screen.getByRole('textbox', { name: /Destination IPv4/ })
    expect(destination).toBeRequired()
    expect(destination).toHaveAttribute('placeholder', 'Enter Valid IP Address')
    const buttons = screen.getAllByRole('button', { name: /^(Add|Delete) Forwarding$/ })
    for (const value of ['', '198.51.100', '999.1.1.1', '198.051.100.3', '2001:db8::1', 'hostname', '198.51.100.3 ']) {
      fireEvent.change(destination, { target: { value } })
      buttons.forEach((button) => expect(button).toBeDisabled())
      expect(destination).toHaveAttribute('aria-invalid', String(value.length > 0))
    }
    fireEvent.change(destination, { target: { value: '198.51.100.3' } })
    expect(destination).toHaveAttribute('aria-invalid', 'false')
    buttons.forEach((button) => expect(button).toBeEnabled())
    act(() => { store.dispatch(setMsgType('')) })
    buttons.forEach((button) => expect(button).toBeDisabled())
    expect(api).not.toHaveBeenCalled()
  })

  it('blocks unsupported message types before submitting', () => {
    const api = jest.spyOn(RsuMessageForwardingApi, 'create')
    const store = renderMenu()
    act(() => { store.dispatch(setMsgType('Other')) })
    const add = screen.getByRole('button', { name: 'Add Forwarding' })
    expect(add).toBeDisabled()
    fireEvent.click(add)
    expect(api).not.toHaveBeenCalled()
  })
})

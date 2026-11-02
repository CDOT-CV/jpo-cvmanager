import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import RsuRebootMenu from './RsuRebootMenu'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { setupStore } from '../store'
import { replaceChaoticIds } from '../utils/test-utils'
import { confirmAlert } from 'react-confirm-alert'
import toast from 'react-hot-toast'
import { vi } from 'vitest'

vi.mock('react-confirm-alert')

describe('reboot confirmation', () => {
  beforeEach(() => {
    vi.mocked(confirmAlert).mockClear()
    fetchMock.mockClear()
    fetchMock.doMock()
  })

  const showConfirmation = () => {
    const store = setupStore({
      rsu: { value: { selectedRsu: { properties: { ipv4_address: '192.0.2.10' } } } },
      user: { value: { authLoginData: { token: 'fixture-token' }, organization: { name: 'Test Org' } } },
    })
    render(
      <ThemeProvider theme={testTheme}>
        <Provider store={store}>
          <RsuRebootMenu />
        </Provider>
      </ThemeProvider>
    )
    fireEvent.click(screen.getByRole('button', { name: 'Reboot RSU' }))
    expect(confirmAlert).toHaveBeenCalledTimes(1)
    expect(fetchMock).not.toHaveBeenCalled()
    return vi.mocked(confirmAlert).mock.calls[0][0]
  }

  it('sends the reboot only after confirmation and shows acceptance for the selected RSU', async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ requestId: 'fixture-request', status: 'ACCEPTED' }), { status: 202 })
    const notification = vi.spyOn(toast, 'error')
    try {
      const confirmation = showConfirmation()
      await act(async () => confirmation.buttons!.find((button) => button.label === 'Yes')!.onClick!())
      expect(await screen.findByRole('status')).toHaveTextContent('Reboot requested')
      expect(fetchMock).toHaveBeenCalledTimes(1)
      expect(fetchMock.mock.calls[0][0]).toContain('/devices/rsus/reboot/192.0.2.10')
      expect(notification).not.toHaveBeenCalled()
    } finally {
      notification.mockRestore()
    }
  })

  it('does not send a reboot when the user declines confirmation', () => {
    const confirmation = showConfirmation()
    confirmation.buttons!.find((button) => button.label === 'No')!.onClick!()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it.each([401, 403, 409, 503])('shows one error and no acceptance notice for HTTP %i', async (status) => {
    fetchMock.mockResponseOnce(JSON.stringify({ detail: 'Reboot request rejected' }), { status })
    const notification = vi.spyOn(toast, 'error')
    try {
      const confirmation = showConfirmation()
      await act(async () => confirmation.buttons!.find((button) => button.label === 'Yes')!.onClick!())
      await waitFor(() => expect(notification).toHaveBeenCalledTimes(1))
      expect(notification).toHaveBeenCalledWith('Reboot request rejected')
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
      expect(fetchMock).toHaveBeenCalledTimes(1)
    } finally {
      notification.mockRestore()
    }
  })

  it('does not retry or show acceptance after a network failure', async () => {
    fetchMock.mockRejectOnce(new Error('Network unavailable'))
    const notification = vi.spyOn(toast, 'error')
    try {
      const confirmation = showConfirmation()
      await act(async () => confirmation.buttons!.find((button) => button.label === 'Yes')!.onClick!())
      await waitFor(() => expect(notification).toHaveBeenCalledTimes(1))
      expect(notification).toHaveBeenCalledWith(expect.stringContaining('check RSU state before retrying'))
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
      expect(fetchMock).toHaveBeenCalledTimes(1)
    } finally {
      notification.mockRestore()
    }
  })

  it('shows a useful error when a rejected request has no JSON response', async () => {
    fetchMock.mockResponseOnce('Service unavailable', { status: 503 })
    const notification = vi.spyOn(toast, 'error')
    try {
      const confirmation = showConfirmation()
      await act(async () => confirmation.buttons!.find((button) => button.label === 'Yes')!.onClick!())
      await waitFor(() => expect(notification).toHaveBeenCalledTimes(1))
      expect(notification).toHaveBeenCalledWith('Reboot request was not accepted')
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    } finally {
      notification.mockRestore()
    }
  })
})

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({})}>
        <RsuRebootMenu />
      </Provider>
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('does not show another RSU\'s accepted reboot as this device\'s result', () => {
  render(
    <ThemeProvider theme={testTheme}>
      <Provider store={setupStore({
        rsu: { value: { selectedRsu: { properties: { ipv4_address: '192.0.2.11' } } } },
        config: { value: { rebootChangeSuccess: true, rebootRequestedIp: '192.0.2.10' } },
      })}>
        <RsuRebootMenu />
      </Provider>
    </ThemeProvider>
  )
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})

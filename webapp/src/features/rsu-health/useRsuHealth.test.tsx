import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { setupListeners } from '@reduxjs/toolkit/query'
import fetchMock from 'jest-fetch-mock'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setupStore } from '../../store'
import EnvironmentVars from '../../EnvironmentVars'
import { getOnlineStatus } from '../../utils/rsu-status'
import { rsuOnlineStatusApiSlice } from '../api/rsuOnlineStatusApiSlice'
import { scmsApiSlice } from '../api/scmsApiSlice'
import { useRsuHealth } from './useRsuHealth'
import { getHealthOverview } from './refresh-policy'

const ip = '10.0.0.1'
const onlineBody = JSON.stringify({ onlineStatusByIp: { [ip]: { current_status: 'online' } } })
const scmsBody = JSON.stringify({ scmsHealthByIp: { [ip]: { health: true, expiration: null } } })
const originalFlag = EnvironmentVars.ENABLE_RSU_FEATURES
let store: ReturnType<typeof setupStore>
let removeListeners: () => void

const Probe = ({ organization = 'TestOrg', selectedIp = '', id = 'health' }) => {
  const health = useRsuHealth(organization, selectedIp)
  return <div data-testid={id} aria-busy={health.isPending}>
    <span>{getOnlineStatus(ip, health.onlineStatusQuery)}</span>
    <output>{health.summary.join(' | ')}</output>
    <span role="status" aria-label="Health freshness">{getHealthOverview(health.feeds, health.now, health.networkOnline, health.refreshing).text}</span>
  </div>
}

const advance = async (milliseconds = 0) => {
  await act(async () => { await vi.advanceTimersByTimeAsync(milliseconds) })
}

const requestCount = (path: string) => fetchMock.mock.calls.filter(([request]) => (request as Request).url.endsWith(path)).length

describe('shared RSU health refresh', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-17T12:00:00Z'))
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true })
    EnvironmentVars.ENABLE_RSU_FEATURES = true
    fetchMock.resetMocks()
    fetchMock.doMock()
    fetchMock.mockResponse(async (request) => {
      if (request.url.endsWith('/online-status')) return onlineBody
      if (request.url.endsWith(`/online-status/${ip}`)) return JSON.stringify({ ip, last_online: null })
      if (request.url.endsWith('/scms/status')) return scmsBody
      throw new Error(`Unexpected request: ${request.url}`)
    })
    store = setupStore({ user: { value: {
      authLoginData: { token: 'test-token' }, organization: { name: 'TestOrg', role: 'admin' },
    } } })
    removeListeners = setupListeners(store.dispatch)
  })

  afterEach(() => {
    cleanup()
    removeListeners()
    store.dispatch(rsuOnlineStatusApiSlice.util.resetApiState())
    store.dispatch(scmsApiSlice.util.resetApiState())
    vi.clearAllTimers()
    vi.useRealTimers()
    Reflect.deleteProperty(document, 'visibilityState')
    Reflect.deleteProperty(navigator, 'onLine')
    EnvironmentVars.ENABLE_RSU_FEATURES = originalFlag
    fetchMock.dontMock()
  })

  it('shares polling between consumers and uses separate health cadences', async () => {
    render(<Provider store={store}><Probe selectedIp={ip} /><Probe selectedIp={ip} id="second" /></Provider>)
    await advance()
    expect(fetchMock).toHaveBeenCalledTimes(3)
    await advance(59_999)
    expect(fetchMock).toHaveBeenCalledTimes(3)
    await advance(1)
    expect(requestCount('/online-status')).toBe(2)
    expect(requestCount(`/online-status/${ip}`)).toBe(2)
    expect(requestCount('/scms/status')).toBe(1)
    await advance(240_000)
    expect(requestCount('/online-status')).toBe(6)
    expect(requestCount('/scms/status')).toBe(2)
  })

  it('advances Updated after an automatic online poll even when SCMS has not polled again', async () => {
    render(<Provider store={store}><Probe /></Provider>)
    await advance()
    const initialTime = Date.now()
    expect(screen.getByRole('status', { name: 'Health freshness' })).toHaveTextContent(`Updated ${new Date(initialTime).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`)
    await advance(60_000)
    expect(requestCount('/online-status')).toBe(2)
    expect(requestCount('/scms/status')).toBe(1)
    expect(screen.getByRole('status', { name: 'Health freshness' })).toHaveTextContent(`Updated ${new Date(initialTime + 60_000).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`)
  })

  it('pauses hidden/offline polling and refreshes on return and reconnection', async () => {
    render(<Provider store={store}><Probe /></Provider>)
    await advance()
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    fireEvent(document, new Event('visibilitychange', { bubbles: true }))
    await advance(300_000)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    fireEvent(document, new Event('visibilitychange', { bubbles: true }))
    await advance()
    expect(fetchMock).toHaveBeenCalledTimes(4)
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: false })
    fireEvent(window, new Event('offline'))
    await advance(300_000)
    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(screen.getByText(/stale; showing last known data/)).toBeInTheDocument()
    fireEvent(window, new Event('focus'))
    await advance()
    expect(fetchMock).toHaveBeenCalledTimes(4)
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true })
    fireEvent(window, new Event('online'))
    await advance()
    expect(fetchMock).toHaveBeenCalledTimes(6)
  })

  it('retains last-known health after a failed poll and recovers on the next poll', async () => {
    render(<Provider store={store}><Probe /></Provider>)
    await advance()
    fetchMock.mockResponseOnce('{}', { status: 503 })
    await advance(60_000)
    expect(screen.getByText('online')).toBeInTheDocument()
    expect(screen.getByText(/refresh failed; showing last known data/)).toBeInTheDocument()
    expect(screen.getByTestId('health')).toHaveAttribute('aria-busy', 'false')
    fireEvent(window, new Event('focus'))
    await advance()
    expect(fetchMock).toHaveBeenCalledTimes(3)
    await advance(60_000)
    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(screen.queryByText(/refresh failed/)).not.toBeInTheDocument()
  })

  it('keeps the shared pending state active until the selected device history settles', async () => {
    let finishHistory: (body: string) => void
    const pending = new Promise<string>((resolve) => { finishHistory = resolve })
    fetchMock.mockResponse(async (request) => {
      if (request.url.endsWith(`/online-status/${ip}`)) return pending
      if (request.url.endsWith('/online-status')) return onlineBody
      if (request.url.endsWith('/scms/status')) return scmsBody
      throw new Error(`Unexpected request: ${request.url}`)
    })
    render(<Provider store={store}><Probe selectedIp={ip} /></Provider>)
    await advance()
    expect(screen.getByText('online')).toBeInTheDocument()
    expect(screen.getByTestId('health')).toHaveAttribute('aria-busy', 'true')
    await act(async () => { finishHistory(JSON.stringify({ ip, last_online: null })) })
    await advance()
    expect(screen.getByTestId('health')).toHaveAttribute('aria-busy', 'false')
  })

  it('refreshes only due feeds on return and ignores repeat focus events', async () => {
    render(<Provider store={store}><Probe /></Provider>)
    await advance()
    fireEvent(window, new Event('focus'))
    await advance()
    expect(fetchMock).toHaveBeenCalledTimes(2)

    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    fireEvent(document, new Event('visibilitychange'))
    await advance(60_000)
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    fireEvent(document, new Event('visibilitychange'))
    await advance()
    fireEvent(window, new Event('focus'))
    await advance()
    expect(requestCount('/online-status')).toBe(2)
    expect(requestCount('/scms/status')).toBe(1)
  })

  it('bounds a stalled health request with the shared timeout', async () => {
    fetchMock.mockImplementationOnce((request) => new Promise<Response>((_resolve, reject) => {
      (request as Request).signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    }))
    render(<Provider store={store}><Probe /></Provider>)
    await advance(29_999)
    expect(screen.getByText('loading')).toBeInTheDocument()
    await advance(1)
    expect(screen.getByText('unavailable')).toBeInTheDocument()
    // RTK Query 1.x aborts the thunk when fetchBaseQuery's timeout expires.
    expect(rsuOnlineStatusApiSlice.endpoints.getRsuOnlineStatuses.select('TestOrg')(store.getState()).error)
      .toMatchObject({ name: 'AbortError' })
  })

  it('does not display the previous organization data during an argument change', async () => {
    const view = render(<Provider store={store}><Probe /></Provider>)
    await advance()
    let finishOnline: (body: string) => void
    const pending = new Promise<string>((resolve) => { finishOnline = resolve })
    fetchMock.mockResponseOnce(async () => pending)
    view.rerender(<Provider store={store}><Probe organization="OtherOrg" /></Provider>)
    await advance()
    expect(screen.getByText('loading')).toBeInTheDocument()
    expect(screen.getByText(/Online: Loading/)).toBeInTheDocument()
    await act(async () => { finishOnline(onlineBody) })
    await advance()
    expect(screen.getByText('online')).toBeInTheDocument()
    expect((fetchMock.mock.calls[2][0] as Request).headers.get('Organization')).toBe('OtherOrg')
  })

  it('polls last-online only for the selected device and stops all polling after unmount', async () => {
    const view = render(<Provider store={store}><Probe /></Provider>)
    await advance()
    expect(fetchMock).toHaveBeenCalledTimes(2)
    view.rerender(<Provider store={store}><Probe selectedIp={ip} /></Provider>)
    await advance()
    expect(requestCount(`/online-status/${ip}`)).toBe(1)
    view.rerender(<Provider store={store}><Probe /></Provider>)
    await advance(60_000)
    expect(requestCount(`/online-status/${ip}`)).toBe(1)
    view.unmount()
    await advance()
    const requests = fetchMock.mock.calls.length
    await advance(300_000)
    expect(fetchMock).toHaveBeenCalledTimes(requests)
  })

  it('does not fetch when RSU features are disabled', async () => {
    EnvironmentVars.ENABLE_RSU_FEATURES = false
    render(<Provider store={store}><Probe /></Provider>)
    await advance(300_000)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it.each(['missing organization', 'missing authentication'])('does not fetch with %s', async (reason) => {
    if (reason === 'missing authentication') {
      store = setupStore()
    }
    render(<Provider store={store}><Probe organization={reason === 'missing organization' ? '' : 'TestOrg'} /></Provider>)
    await advance(300_000)
    expect(fetchMock).not.toHaveBeenCalled()
  })
})

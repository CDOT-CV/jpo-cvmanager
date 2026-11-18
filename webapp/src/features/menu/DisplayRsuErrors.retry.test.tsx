import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import { Provider } from 'react-redux'
import fetchMock from 'jest-fetch-mock'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import DisplayRsuErrors from './DisplayRsuErrors'
import EnvironmentVars from '../../EnvironmentVars'
import { setupStore } from '../../store'
import { testTheme } from '../../styles'
import { rsuOnlineStatusApiSlice } from '../api/rsuOnlineStatusApiSlice'
import { scmsApiSlice } from '../api/scmsApiSlice'

const ip = '10.0.0.1'
const onlineUrl = `${EnvironmentVars.CVIZ_API_SERVER_URL}/devices/rsus/online-status`
const scmsUrl = `${EnvironmentVars.CVIZ_API_SERVER_URL}/devices/scms/status`
const onlineResponse = { onlineStatusByIp: { [ip]: { current_status: 'online' } } }
const scmsResponse = { scmsHealthByIp: { [ip]: { health: true, expiration: null } } }

let store: ReturnType<typeof setupStore>

describe('RSU status retry requests', () => {
  beforeEach(() => {
    fetchMock.resetMocks()
    fetchMock.doMock()
    const initialState = setupStore().getState()
    store = setupStore({
      user: { value: {
        authLoginData: { token: 'test-token' },
        organization: { name: 'test-org', organization: 'test-org', role: 'admin' },
      } },
      rsu: { ...initialState.rsu, value: { ...initialState.rsu.value, rsuData: [{
        id: 1,
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [-104.9, 39.7] },
        properties: { ipv4_address: ip, primary_route: 'I-25', milepost: 1 },
      }] } },
    })
  })

  afterEach(() => {
    cleanup()
    store.dispatch(rsuOnlineStatusApiSlice.util.resetApiState())
    store.dispatch(scmsApiSlice.util.resetApiState())
    fetchMock.dontMock()
  })

  it.each([
    ['Online', onlineUrl, onlineResponse],
    ['SCMS', scmsUrl, scmsResponse],
  ] as const)('recovers %s status with one explicit retry and no duplicate requests', async (name, targetUrl, response) => {
    let attempts = 0
    let finishRetry: (body: string) => void
    const retryResponse = new Promise<string>((resolve) => { finishRetry = resolve })
    fetchMock.mockResponse(async (request) => {
      if (request.url === targetUrl) {
        attempts++
        if (attempts === 1) return { status: 503, body: '{}' }
        if (attempts === 2) return { body: await retryResponse }
        throw new Error('Unexpected additional retry')
      }
      if (request.url === onlineUrl) return JSON.stringify(onlineResponse)
      if (request.url === scmsUrl) return JSON.stringify(scmsResponse)
      throw new Error(`Unexpected request: ${request.url}`)
    })

    render(<ThemeProvider theme={testTheme}><Provider store={store}><DisplayRsuErrors /></Provider></ThemeProvider>)
    const alert = (await screen.findByText(`${name} update failed`)).closest<HTMLElement>('[role="alert"]')!
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const retry = within(alert).getByRole('button', { name: 'Retry' })
    act(() => {
      fireEvent.click(retry)
      fireEvent.click(retry)
    })
    await waitFor(() => expect(attempts).toBe(2))
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(screen.queryByText(`${name} update failed`)).not.toBeInTheDocument()
    await act(async () => { finishRetry(JSON.stringify(response)) })
    await waitFor(() => expect(screen.queryByText('Loading')).not.toBeInTheDocument())
    expect(screen.getAllByText(name === 'Online' ? 'Online' : 'Healthy').length).toBeGreaterThan(0)
    expect(fetchMock).toHaveBeenCalledTimes(3)
    const request = fetchMock.mock.calls.find(([value]) => (value as Request).url === targetUrl)![0] as Request
    expect(request.headers.get('Organization')).toBe('test-org')
    expect(request.headers.get('Authorization')).toBe('Bearer test-token')
  })

  it('keeps a failed retry unavailable without automatically retrying again', async () => {
    fetchMock.mockResponse(async (request) => {
      if (request.url === onlineUrl) return { status: 503, body: '{}' }
      if (request.url === scmsUrl) return JSON.stringify(scmsResponse)
      throw new Error(`Unexpected request: ${request.url}`)
    })
    render(<ThemeProvider theme={testTheme}><Provider store={store}><DisplayRsuErrors /></Provider></ThemeProvider>)
    const alert = (await screen.findByText('Online update failed')).closest<HTMLElement>('[role="alert"]')!
    fireEvent.click(within(alert).getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('Online update failed')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(screen.queryByText('Offline')).not.toBeInTheDocument()
  })
})

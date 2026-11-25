import { act, fireEvent, render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import { describe, expect, it, vi } from 'vitest'
import { THEMES } from '../../styles'
import { RsuHealthControls } from './RsuHealthControls'
import { RsuHealthSummary } from './RsuHealthSummary'

type Health = Parameters<typeof RsuHealthControls>[0]['health']
const now = Date.parse('2026-11-17T20:00:00Z')
const makeHealth = (queryOverrides = {}) => {
  const query = { currentData: {}, fulfilledTimeStamp: now - 30_000, refetch: vi.fn(), ...queryOverrides }
  return {
    feeds: [{ label: 'Online', query, interval: 60_000 }],
    summary: ['Online: Fetched at 11/17/2026, 1:59:30 PM'],
    now,
    networkOnline: true,
    refreshing: false,
    canRetry: true,
  } as unknown as Health
}

describe('RSU health controls', () => {
  it.each(Object.entries(THEMES))('keeps healthy details collapsed in the %s theme', (_name, theme) => {
    const health = makeHealth()
    render(<ThemeProvider theme={theme}><RsuHealthControls health={health} /></ThemeProvider>)
    expect(screen.getByRole('status')).toHaveTextContent(/^Updated /)
    expect(screen.queryByText(health.summary[0])).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Health data details' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Show health data details' }))
    const timestamp = new Date(health.feeds[0].query.fulfilledTimeStamp!).toLocaleString()
    expect(screen.getByText(`Online: ${timestamp}`)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hide health data details' })).toHaveAttribute('aria-expanded', 'true')
    fireEvent.click(screen.getByRole('button', { name: 'Hide health data details' }))
    expect(screen.queryByRole('region', { name: 'Health data details' })).not.toBeInTheDocument()
    expect(screen.queryByText(`Online: ${timestamp}`)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Refresh health data' })).not.toBeInTheDocument()
  })

  it('shows only freshness text in the panel title', () => {
    render(<ThemeProvider theme={THEMES.dark}><RsuHealthSummary health={makeHealth()} /></ThemeProvider>)
    expect(screen.getByRole('status')).toHaveTextContent(/^Updated /)
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('region')).not.toBeInTheDocument()
  })

  it('keeps a failed-refresh warning visible when details are closed', () => {
    const health = makeHealth({ isError: true })
    render(<ThemeProvider theme={THEMES.dark}><RsuHealthControls health={health} /></ThemeProvider>)
    expect(screen.getByRole('status')).toHaveTextContent(`Last received ${new Date(now - 30_000).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`)
    fireEvent.click(screen.getByRole('button', { name: 'Show health data details' }))
    expect(screen.getByText(`Online: ${new Date(now - 30_000).toLocaleString()}`)).toBeInTheDocument()
    expect(screen.getByText('Update failed')).toBeInTheDocument()
    expect(screen.queryByText(/Online:.*refresh failed/)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(health.feeds[0].query.refetch).toHaveBeenCalledOnce()
  })

  it('does not hide stale data behind a newer feed timestamp', () => {
    const health = makeHealth({ fulfilledTimeStamp: now - 180_000 })
    health.feeds.push({ label: 'SCMS', query: { ...health.feeds[0].query, fulfilledTimeStamp: now }, interval: 300_000 } as Health['feeds'][number])
    render(<ThemeProvider theme={THEMES.dark}><RsuHealthControls health={health} /></ThemeProvider>)
    expect(screen.getByRole('status')).toHaveTextContent('Showing stale data')
  })

  it('shows connection loss without expanding the detailed timestamps', () => {
    const health = { ...makeHealth(), networkOnline: false, canRetry: false }
    render(<ThemeProvider theme={THEMES.dark}><RsuHealthControls health={health} /></ThemeProvider>)
    expect(screen.getByRole('status')).toHaveTextContent('Network disconnected')
    expect(screen.queryByRole('button', { name: 'Refresh health data' })).not.toBeInTheDocument()
    expect(screen.queryByText(health.summary[0])).not.toBeInTheDocument()
  })

  it('explains timestamps on hover and keyboard focus without permanent explanatory text', () => {
    vi.useFakeTimers()
    const view = render(<ThemeProvider theme={THEMES.dark}><RsuHealthControls health={makeHealth()} /></ThemeProvider>)
    try {
      fireEvent.click(screen.getByRole('button', { name: 'Show health data details' }))

      const explanation = 'Fetch times show when data was received, not when the device was checked.'
      expect(screen.queryByText(explanation)).not.toBeInTheDocument()
      fireEvent.mouseOver(screen.getByRole('button', { name: 'Hide health data details' }))
      act(() => { vi.advanceTimersByTime(200) })
      expect(screen.getByRole('tooltip')).toHaveTextContent(explanation)

      const info = screen.getByRole('button', { name: 'Hide health data details' })
      fireEvent.mouseLeave(info)
      act(() => { vi.advanceTimersByTime(1000) })
      fireEvent.keyDown(document, { key: 'Tab' })
      act(() => { info.focus(); vi.advanceTimersByTime(200) })
      expect(screen.getByRole('tooltip')).toHaveTextContent(explanation)
    } finally {
      view.unmount()
      vi.clearAllTimers()
      vi.useRealTimers()
    }
  })
})

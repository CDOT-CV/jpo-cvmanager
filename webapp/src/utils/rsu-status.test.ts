import { describe, expect, it } from 'vitest'
import { getContrastRatio } from '@mui/material/styles'
import { THEMES } from '../styles'
import {
  getLastOnlineDisplay,
  getOnlineStatus,
  getScmsExpirationDisplay,
  getScmsStatus,
  getStatusMarkerColor,
  getStatusColor,
  getStatusTextColor,
  shouldIncludeInRsuErrorReport,
} from './rsu-status'

const ip = '10.0.0.1'

describe.each(Object.entries(THEMES))('neutral status contrast in the %s theme', (_name, theme) => {
  it.each(['loading', 'unknown', 'unavailable'] as const)('keeps %s text readable on panels, popups, and reports', (status) => {
    const textColor = getStatusTextColor(status, theme)
    for (const background of [theme.palette.background.paper, theme.palette.background.default]) {
      expect(getContrastRatio(textColor, background)).toBeGreaterThanOrEqual(4.5)
    }
    expect(getContrastRatio(getStatusTextColor(status, theme, 'print'), '#ffffff')).toBeGreaterThanOrEqual(4.5)

    const badgeColor = getStatusColor(status, theme)
    expect(getContrastRatio(theme.palette.getContrastText(badgeColor), badgeColor)).toBeGreaterThanOrEqual(4.5)
    expect(getStatusMarkerColor(status)).toBe('#B0B0B0')
  })
})

describe('RSU status interpretation', () => {
  it.each(['online', 'offline', 'unstable', 'unknown'] as const)('preserves API %s status', (status) => {
    expect(getOnlineStatus(ip, { currentData: { [ip]: { current_status: status } } })).toBe(status)
  })

  it.each([
    [{ isLoading: true }, 'loading'],
    [{ isFetching: true }, 'loading'],
    [{ isError: true }, 'unavailable'],
    [{ isUninitialized: true }, 'unknown'],
    [{ currentData: {} }, 'unknown'],
  ] as const)('distinguishes query state %j from device failure', (query, status) => {
    expect(getOnlineStatus(ip, query)).toBe(status)
    expect(getScmsStatus(ip, query)).toBe(status)
    expect(getStatusMarkerColor(status)).toBe('#B0B0B0')
  })

  it('does not use the previous query argument data while the current request is fetching', () => {
    const query = { data: { [ip]: { current_status: 'offline' as const } }, isFetching: true }
    expect(getOnlineStatus(ip, query)).toBe('loading')
  })

  it('shows unavailable after a failed request even when a cached value exists', () => {
    expect(getOnlineStatus(ip, { isError: true, currentData: { [ip]: { current_status: 'offline' } } }))
      .toBe('unavailable')
    expect(getScmsStatus(ip, { isError: true, currentData: { [ip]: { health: false, expiration: null } } }))
      .toBe('unavailable')
  })

  it('keeps the current result while a request is in progress', () => {
    expect(getOnlineStatus(ip, { isFetching: true, currentData: { [ip]: { current_status: 'online' } } }))
      .toBe('online')
  })

  it.each([
    [true, 'healthy'],
    [false, 'unhealthy'],
    [null, 'unknown'],
  ] as const)('interprets SCMS health %s without truthiness fallbacks', (health, status) => {
    expect(getScmsStatus(ip, { currentData: { [ip]: { health, expiration: null } } })).toBe(status)
  })

  it('treats a null or missing SCMS record as unknown', () => {
    expect(getScmsStatus(ip, { currentData: { [ip]: null } })).toBe('unknown')
    expect(getScmsStatus(ip, { currentData: {} })).toBe('unknown')
  })

  it('includes confirmed problems and unknown health in error reports', () => {
    expect(shouldIncludeInRsuErrorReport('offline', 'healthy')).toBe(true)
    expect(shouldIncludeInRsuErrorReport('unstable', 'unknown')).toBe(true)
    expect(shouldIncludeInRsuErrorReport('unavailable', 'unhealthy')).toBe(true)
    expect(shouldIncludeInRsuErrorReport('online', 'healthy')).toBe(false)
    expect(shouldIncludeInRsuErrorReport('unknown', 'unknown')).toBe(true)
    expect(shouldIncludeInRsuErrorReport('unknown', 'healthy')).toBe(true)
    expect(shouldIncludeInRsuErrorReport('online', 'unknown')).toBe(true)
    expect(shouldIncludeInRsuErrorReport('unavailable', 'healthy')).toBe(false)
    expect(shouldIncludeInRsuErrorReport('online', 'unavailable')).toBe(false)
    expect(shouldIncludeInRsuErrorReport('loading', 'unavailable')).toBe(false)
  })

  it('does not infer enrollment or certificate download history from missing expiration data', () => {
    expect(getScmsExpirationDisplay(ip, { currentData: {} })).toBe('Unknown')
    expect(getScmsExpirationDisplay(ip, { currentData: { [ip]: null } })).toBe('Unknown')
    expect(getScmsExpirationDisplay(ip, { currentData: { [ip]: { health: null, expiration: null } } }))
      .toBe('No certificate expiration recorded')
    expect(getScmsExpirationDisplay(ip, { isError: true })).toBe('Unavailable')
  })

  it('distinguishes unavailable online history from an empty successful result', () => {
    expect(getLastOnlineDisplay({ isLoading: true })).toBe('Loading')
    expect(getLastOnlineDisplay({ isError: true })).toBe('Unavailable')
    expect(getLastOnlineDisplay({ currentData: { ip, last_online: null } })).toBe('None recorded')
    expect(getLastOnlineDisplay({ currentData: { ip, last_online: 'invalid' } })).toBe('Unknown')
  })
})

import { describe, expect, it } from 'vitest'
import { describeHealthFetch, getHealthOverview, isHealthDataStale, RSU_HEALTH_REFRESH } from './refresh-policy'

describe('health fetch freshness', () => {
  const fetchedAt = Date.parse('2026-11-17T12:00:00Z')
  const query = { currentData: {}, fulfilledTimeStamp: fetchedAt }

  it.each([RSU_HEALTH_REFRESH.online, RSU_HEALTH_REFRESH.scms])('marks data stale after two %i ms intervals', (interval) => {
    expect(isHealthDataStale(query, fetchedAt + interval * 2 - 1, interval)).toBe(false)
    expect(isHealthDataStale(query, fetchedAt + interval * 2, interval)).toBe(true)
    expect(describeHealthFetch('Online', query, fetchedAt + interval * 2, interval)).toContain('stale; showing last known data')
  })

  it('separates an initial failure from a failed refresh', () => {
    expect(describeHealthFetch('Online', { isError: true }, fetchedAt, 60_000)).toBe('Online: Unavailable')
    const description = describeHealthFetch('Online', { ...query, isError: true }, fetchedAt, 60_000)
    expect(description).toContain(new Date(fetchedAt).toLocaleString())
    expect(description).toContain('refresh failed; showing last known data')
  })

  it('never associates a previous argument timestamp with missing current data', () => {
    expect(describeHealthFetch('SCMS', { fulfilledTimeStamp: fetchedAt, isFetching: true }, fetchedAt, 60_000))
      .toBe('SCMS: Loading')
    expect(isHealthDataStale({ fulfilledTimeStamp: fetchedAt }, fetchedAt + 1_000_000, 60_000)).toBe(false)
  })

  it('uses the failed feed timestamp rather than a newer successful feed', () => {
    const feeds = [
      { query: { ...query, isError: true }, interval: 60_000 },
      { query: { ...query, fulfilledTimeStamp: fetchedAt + 60_000 }, interval: 300_000 },
    ]
    expect(getHealthOverview(feeds, fetchedAt + 60_000, true, false)).toEqual({
      text: `Last received ${new Date(fetchedAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`,
      warning: true,
    })
    feeds[1].query = { ...feeds[1].query, isError: true }
    expect(getHealthOverview(feeds, fetchedAt + 60_000, true, false).text)
      .toContain(new Date(fetchedAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }))
  })

  it('does not invent a successful timestamp for initial failures or missing fetch times', () => {
    expect(getHealthOverview([{ query: { isError: true }, interval: 60_000 }], fetchedAt, true, false).text)
      .toBe('Some data unavailable')
    expect(getHealthOverview([{ query: { currentData: {}, isError: true }, interval: 60_000 }], fetchedAt, true, false).text)
      .toBe('Last received time unavailable')
  })

  it('keeps compact fetch times free of warnings repeated by alerts', () => {
    expect(describeHealthFetch('Online', { ...query, isError: true }, fetchedAt, 60_000, 'compact'))
      .toBe(`Online: ${new Date(fetchedAt).toLocaleString()}`)
    expect(describeHealthFetch('Online', query, fetchedAt + 120_000, 60_000, 'compact'))
      .toBe(`Online: ${new Date(fetchedAt).toLocaleString()}`)
  })

  it('includes the date when a failed feed was last received on a previous day', () => {
    const timestamp = new Date(2026, 10, 16, 16, 30).getTime()
    const now = new Date(2026, 10, 17, 16, 30).getTime()
    const date = new Date(timestamp)
    expect(getHealthOverview([{ query: { currentData: {}, fulfilledTimeStamp: timestamp, isError: true }, interval: 60_000 }], now, true, false).text)
      .toBe(`Last received ${date.toLocaleDateString()} ${date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`)
  })

})

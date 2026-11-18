export const RSU_HEALTH_REFRESH = {
  online: 60_000,
  scms: 5 * 60_000,
  lastOnline: 60_000,
  staleAfterIntervals: 2,
  clockTick: 15_000,
  requestTimeout: 30_000,
} as const

export type HealthQueryState = {
  currentData?: unknown
  fulfilledTimeStamp?: number
  isFetching?: boolean
  isError?: boolean
  isUninitialized?: boolean
}

export const isHealthDataStale = (query: HealthQueryState, now: number, interval: number): boolean =>
  !query.isUninitialized && query.currentData !== undefined && query.fulfilledTimeStamp !== undefined &&
  now - query.fulfilledTimeStamp >= interval * RSU_HEALTH_REFRESH.staleAfterIntervals

export const describeHealthFetch = (
  label: string,
  query: HealthQueryState,
  now: number,
  interval: number,
  format: 'full' | 'compact' = 'full'
): string => {
  if (query.isUninitialized || query.currentData === undefined) {
    return `${label}: ${query.isFetching ? 'Loading' : query.isError ? 'Unavailable' : 'Not loaded'}`
  }
  const fetched = query.fulfilledTimeStamp === undefined
    ? 'Fetch time unavailable'
    : `${format === 'full' ? 'Fetched at ' : ''}${new Date(query.fulfilledTimeStamp).toLocaleString()}`
  if (format === 'compact') return `${label}: ${fetched}`
  const warning = query.isError ? ' - refresh failed; showing last known data'
    : isHealthDataStale(query, now, interval) ? ' - stale; showing last known data' : ''
  return `${label}: ${fetched}${warning}${query.isFetching ? ' - refreshing' : ''}`
}

const formatOverviewTime = (timestamp: number, now: number): string => {
  const date = new Date(timestamp)
  const time = date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
  return date.toDateString() === new Date(now).toDateString() ? time : `${date.toLocaleDateString()} ${time}`
}

export const getHealthOverview = (
  feeds: { query: HealthQueryState; interval: number }[],
  now: number,
  networkOnline: boolean,
  refreshing: boolean
): { text: string; warning: boolean } => {
  if (!networkOnline) return { text: 'Network disconnected', warning: true }
  const failed = feeds.filter(({ query }) => query.isError)
  if (failed.length) {
    const missing = failed.some(({ query }) => query.currentData === undefined)
    if (missing) return { text: 'Some data unavailable', warning: true }
    const times = failed.map(({ query }) => query.fulfilledTimeStamp).filter((time): time is number => time !== undefined)
    if (times.length !== failed.length) {
      return { text: 'Last received time unavailable', warning: true }
    }
    // Use the oldest failed feed so a more recent success cannot mask its age.
    const time = formatOverviewTime(Math.min(...times), now)
    return { text: `Last received ${time}`, warning: true }
  }
  if (feeds.some(({ query, interval }) => isHealthDataStale(query, now, interval))) {
    return { text: 'Showing stale data', warning: true }
  }
  if (refreshing) return { text: 'Refreshing...', warning: false }
  if (feeds.some(({ query }) => query.isUninitialized || query.currentData === undefined)) {
    return { text: 'Waiting for data', warning: false }
  }
  const times = feeds.map(({ query }) => query.fulfilledTimeStamp).filter((time): time is number => time !== undefined)
  if (!times.length || times.length !== feeds.length) {
    return { text: 'Update time unavailable', warning: false }
  }
  // Freshness warnings above cover each feed; Updated reflects the latest successful fetch.
  const time = formatOverviewTime(Math.max(...times), now)
  return { text: `Updated ${time}`, warning: false }
}

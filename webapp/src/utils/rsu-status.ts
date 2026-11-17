import type { Theme } from '@mui/material'
import type { RsuLastOnlineStatus, RsuOnlineStatus, RsuOnlineStatusMap } from '../features/api/rsuOnlineStatusApiSlice'
import { formatScmsExpiration } from '../features/api/scmsApiSlice'
import type { ScmsHealthStatus } from '../features/api/scmsApiSlice'

// Presentation states extend the API types without changing their data contracts.
type UnconfirmedStatus = 'loading' | 'unknown' | 'unavailable'
export type OnlineStatus = RsuOnlineStatus | UnconfirmedStatus
export type ScmsStatus = 'healthy' | 'unhealthy' | UnconfirmedStatus
export type RsuHealthStatus = OnlineStatus | ScmsStatus

type StatusQuery<T> = {
  currentData?: T
  isLoading?: boolean
  isFetching?: boolean
  isError?: boolean
  isUninitialized?: boolean
}

const getQueryStatus = <T>(query: StatusQuery<T>): UnconfirmedStatus | undefined => {
  if (query.isUninitialized) return 'unknown'
  if (query.isError) return 'unavailable'
  // currentData prevents a previous organization's/device's cached result from being displayed.
  if (query.currentData === undefined) {
    return query.isLoading || query.isFetching ? 'loading' : 'unknown'
  }
  return undefined
}

export const getOnlineStatus = (ip: string, query: StatusQuery<RsuOnlineStatusMap>): OnlineStatus => {
  const queryStatus = getQueryStatus(query)
  if (queryStatus) return queryStatus
  const status = query.currentData?.[ip]?.current_status
  return status === 'online' || status === 'offline' || status === 'unstable' ? status : 'unknown'
}

export const getScmsStatus = (ip: string, query: StatusQuery<ScmsHealthStatus>): ScmsStatus => {
  const queryStatus = getQueryStatus(query)
  if (queryStatus) return queryStatus
  const health = query.currentData?.[ip]?.health
  return health === true ? 'healthy' : health === false ? 'unhealthy' : 'unknown'
}

export const getStatusLabel = (status: RsuHealthStatus): string =>
  status.charAt(0).toUpperCase() + status.slice(1)

export const getStatusColor = (status: RsuHealthStatus, theme: Theme, shade: 'light' | 'dark' = 'dark'): string => {
  if (status === 'online' || status === 'healthy') return theme.palette.success[shade]
  if (status === 'unstable') return theme.palette.warning.main
  if (status === 'offline' || status === 'unhealthy') return theme.palette.error[shade]
  return theme.palette.grey[600]
}

export const getStatusTextColor = (
  status: RsuHealthStatus,
  theme: Theme,
  context: 'screen' | 'print' = 'screen'
): string => {
  if (status === 'loading' || status === 'unknown' || status === 'unavailable') {
    // Reports have white backgrounds even when the application uses a dark theme.
    return context === 'print' ? theme.palette.grey[700] : theme.palette.text.secondary
  }
  return getStatusColor(status, theme, context === 'print' ? 'dark' : 'light')
}

export const getStatusMarkerColor = (status: RsuHealthStatus): string => {
  if (status === 'online' || status === 'healthy') return '#A1D363'
  if (status === 'unstable') return '#D1A711'
  if (status === 'offline' || status === 'unhealthy') return '#E94F37'
  return '#B0B0B0'
}

export const hasConfirmedRsuError = (onlineStatus: OnlineStatus, scmsStatus: ScmsStatus): boolean =>
  onlineStatus === 'offline' || onlineStatus === 'unstable' || scmsStatus === 'unhealthy'

export const getScmsExpirationDisplay = (ip: string, query: StatusQuery<ScmsHealthStatus>): string => {
  const queryStatus = getQueryStatus(query)
  if (queryStatus) return getStatusLabel(queryStatus)
  const record = query.currentData?.[ip]
  if (!record) return 'Unknown'
  if (!record.expiration) return 'No certificate expiration recorded'
  if (!Number.isFinite(new Date(record.expiration).getTime())) return 'Unknown'
  return formatScmsExpiration(record.expiration)
}

export const getLastOnlineDisplay = (query: StatusQuery<RsuLastOnlineStatus>): string => {
  const queryStatus = getQueryStatus(query)
  if (queryStatus) return getStatusLabel(queryStatus)
  const lastOnline = query.currentData?.last_online
  if (!lastOnline) return 'None recorded'
  const date = new Date(lastOnline)
  return Number.isFinite(date.getTime()) ? date.toLocaleString() : 'Unknown'
}

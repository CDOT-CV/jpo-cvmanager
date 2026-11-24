import { Alert } from '@mui/material'
import { HealthRetryButton } from '../features/rsu-health/HealthRetryButton'

type StatusRetryAlertProps = {
  statusName: string
  query: {
    isError?: boolean
    isFetching?: boolean
    refetch: () => unknown
  }
  canRetry?: boolean
  stale?: boolean
  compact?: boolean
}

export const StatusRetryAlert = ({ statusName, query, canRetry = true, stale = false, compact = false }: StatusRetryAlertProps) => {
  if (!query.isError && !stale) return null

  return (
    <Alert severity="warning" aria-label={compact ? `${statusName} health warning` : undefined} sx={compact ? {
      mt: 0.5, px: 1, py: 0, alignItems: 'center', fontSize: '0.75rem',
      '& .MuiAlert-icon': { mr: 1, fontSize: 20 },
      '& .MuiAlert-message': { py: 0.5 },
      '& .MuiAlert-action': { alignItems: 'center', pt: 0, mr: 0 },
    } : undefined} action={
      <HealthRetryButton size="small" disabled={query.isFetching || !canRetry} onClick={() => query.refetch()}>Retry</HealthRetryButton>
    }>
      {query.isError ? compact ? 'Update failed' : `${statusName} update failed` : 'Data may be outdated'}
    </Alert>
  )
}

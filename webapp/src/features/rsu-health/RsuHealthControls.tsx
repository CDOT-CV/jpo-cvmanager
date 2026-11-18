import { useState } from 'react'
import type { ReactNode } from 'react'
import { Alert, Box, IconButton, Paper, Tooltip, Typography } from '@mui/material'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import { RsuHealthSummary } from './RsuHealthSummary'
import { HealthRetryButton } from './HealthRetryButton'
import type { useRsuHealth } from './useRsuHealth'
import { describeHealthFetch, isHealthDataStale } from './refresh-policy'

type Health = ReturnType<typeof useRsuHealth>

export const RsuHealthControls = ({ health, children }: { health: Health; children?: ReactNode }) => {
  const [expanded, setExpanded] = useState(false)
  return (
    <Paper component="section" aria-label="RSU Health" elevation={0} sx={{ width: '100%', minWidth: 0, bgcolor: 'background.paper', color: 'text.primary', borderRadius: 0,
      borderBottom: 1, borderColor: 'divider' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 2, py: 1 }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography component="h3" className="museo-slab" variant="subtitle2" sx={{ fontSize: '1rem', fontWeight: 500, fontFamily: '"museo-slab", Arial, Helvetica, sans-serif' }}>RSU Health</Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <RsuHealthSummary health={health} />
            <Tooltip describeChild title="Fetch times show when data was received, not when the device was checked.">
              <IconButton size="small" aria-label={expanded ? 'Hide health data details' : 'Show health data details'}
                aria-expanded={expanded} onClick={() => setExpanded(!expanded)} sx={{ color: 'text.secondary', p: 0.5 }}>
                <InfoOutlinedIcon sx={{ fontSize: 16 }} />
              </IconButton>
            </Tooltip>
          </Box>
        </Box>
      </Box>
      {expanded && (
        <Box role="region" aria-label="Health data details" sx={{ px: 2, pb: 2, minWidth: 0, overflowWrap: 'anywhere' }}>
          {!health.networkOnline && (
            <Typography variant="caption" display="block">Refresh resumes when connected.</Typography>
          )}
          {health.feeds.map(({ label, query, interval }) => {
            const stale = isHealthDataStale(query, health.now, interval)
            return (
              <Box key={label} sx={{ mt: 0.5 }}>
                <Typography variant="caption" display="block">
                  {describeHealthFetch(label, query, health.now, interval, 'compact')}
                </Typography>
                {(query.isError || stale) && (
                  <Alert severity="warning" aria-label={`${label} health warning`} sx={{
                    mt: 0.5, px: 1, py: 0, alignItems: 'center', fontSize: '0.75rem',
                    '& .MuiAlert-icon': { mr: 1, fontSize: 20 },
                    '& .MuiAlert-message': { py: 0.5 },
                    '& .MuiAlert-action': { alignItems: 'center', pt: 0, mr: 0 },
                  }} action={
                    <HealthRetryButton size="small" disabled={query.isFetching || !health.canRetry} onClick={() => query.refetch()}>Retry</HealthRetryButton>
                  }>
                    {query.isError ? 'Update failed' : 'Data may be outdated'}
                  </Alert>
                )}
              </Box>
            )
          })}
        </Box>
      )}
      {children && <Box sx={{ px: 2, pb: 2 }}>{children}</Box>}
    </Paper>
  )
}

import { Box, Typography } from '@mui/material'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import type { useRsuHealth } from './useRsuHealth'
import { getHealthOverview } from './refresh-policy'

type Health = ReturnType<typeof useRsuHealth>

export const RsuHealthSummary = ({ health }: { health: Health }) => {
  const overview = getHealthOverview(health.feeds, health.now, health.networkOnline, health.refreshing)

  return (
    <Box role="status" aria-live="polite" sx={{ display: 'flex', alignItems: 'center', gap: 0.5, minHeight: 24 }}>
      {overview.warning && <WarningAmberIcon titleAccess="Health data warning" color="warning" sx={{ fontSize: 16, flexShrink: 0 }} />}
      <Typography component="span" variant="caption" color={overview.warning ? 'warning.main' : 'text.secondary'} sx={{ display: 'block', lineHeight: 1 }}>
        {overview.text}
      </Typography>
    </Box>
  )
}

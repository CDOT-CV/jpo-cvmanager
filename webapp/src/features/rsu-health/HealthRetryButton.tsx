import { Button, styled } from '@mui/material'

export const HealthRetryButton = styled(Button)({
  // Keep alert actions consistent inside and outside the map's scoped control styles.
  '&&&': {
    fontFamily: '"museo-slab", Arial, Helvetica, sans-serif',
    fontSize: '0.875rem',
    fontWeight: 500,
    lineHeight: 1.5,
    textTransform: 'capitalize',
  },
})

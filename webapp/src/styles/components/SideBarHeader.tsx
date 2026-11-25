import { useTheme, Stack, IconButton, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import CloseIcon from '@mui/icons-material/Close'

type SideBarHeaderProps = {
  onClick: () => void
  title: string
  children?: ReactNode
  mapHeading?: boolean
  closeButtonLabel?: string
}

export const SideBarHeader = (props: SideBarHeaderProps) => {
  const theme = useTheme()
  const closeButton = (
    <IconButton onClick={props.onClick} aria-label={props.closeButtonLabel} sx={{ color: theme.palette.text.primary }}>
      <CloseIcon />
    </IconButton>
  )
  return (
    <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
      <Typography variant={props.mapHeading ? 'subtitle2' : 'body1'} sx={{
        color: theme.palette.text.primary,
        ...(props.mapHeading && {
          fontFamily: '"museo-slab", Arial, Helvetica, sans-serif',
          fontSize: '1rem',
          fontWeight: 500,
        }),
      }}>
        {props.title}
      </Typography>
      {props.children ? (
        <Stack direction="row" alignItems="center" spacing={1} sx={{ minWidth: 0 }}>
          {props.children}
          {closeButton}
        </Stack>
      ) : (
        closeButton
      )}
    </Stack>
  )
}

import { useTheme, Stack, IconButton, Typography } from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'

type SideBarHeaderProps = {
  onClick: () => void
  title: string
  closeButtonLabel?: string
}

export const SideBarHeader = (props: SideBarHeaderProps) => {
  const theme = useTheme()
  return (
    <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
      <Typography variant="body1" sx={{ color: theme.palette.text.primary }}>
        {props.title}
      </Typography>
      <IconButton onClick={props.onClick} aria-label={props.closeButtonLabel} sx={{ color: theme.palette.text.primary }}>
        <CloseIcon />
      </IconButton>
    </Stack>
  )
}

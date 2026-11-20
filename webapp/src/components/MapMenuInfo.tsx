import { ReactNode, useState } from 'react'
import { Box, IconButton, Popover, Tooltip } from '@mui/material'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'

/** Clickable help for map menus; keeps longer explanations out of the form. */
const MapMenuInfo = ({ title, children }: { title: string; children: ReactNode }) => {
  const [anchor, setAnchor] = useState<HTMLButtonElement | null>(null)
  return (
    <>
      <Tooltip title={title}>
        <IconButton size="small" aria-label={title} aria-haspopup="dialog" aria-expanded={Boolean(anchor)}
          onClick={(event) => setAnchor(event.currentTarget)} sx={{ color: 'text.secondary' }}>
          <InfoOutlinedIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <Popover open={Boolean(anchor)} anchorEl={anchor} onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ paper: { role: 'dialog', 'aria-label': title, sx: { maxWidth: 280, p: 1.5, mt: 0.5 } } }}>
        <Box sx={{ typography: 'body2', fontWeight: 400, lineHeight: 1.5 }}>{children}</Box>
      </Popover>
    </>
  )
}

export default MapMenuInfo

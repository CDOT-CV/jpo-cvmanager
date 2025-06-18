import React from 'react'
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, Typography } from '@mui/material'

type RsuStatusDialogProps = {
  open: boolean
  onClose: () => void
  rsuIp: string | null
  // You can add more props here for status data, loading, error, etc.
}

const RsuStatusDialog: React.FC<RsuStatusDialogProps> = ({ open, onClose, rsuIp, children }) => (
  <Dialog open={open} onClose={onClose}>
    <DialogTitle>RSU Status</DialogTitle>
    <DialogContent>
      <Typography variant="subtitle2">RSU IP: {rsuIp}</Typography>
      {children}
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Close</Button>
    </DialogActions>
  </Dialog>
)

export default RsuStatusDialog

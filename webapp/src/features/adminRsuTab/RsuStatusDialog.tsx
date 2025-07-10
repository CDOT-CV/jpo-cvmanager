import React from 'react'
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, Typography } from '@mui/material'
import type { RsuState } from '../../apis/intersections/rsu-api'

type RsuStatusDialogProps = {
  open: boolean
  onClose: () => void
  rsuIp: string | null
  // You can add more props here for status data, loading, error, etc.
  rsuState?: RsuState | null
}

const RsuStatusDialog: React.FC<RsuStatusDialogProps> = ({ open, onClose, rsuIp, rsuState }) => (
  <Dialog open={open} onClose={onClose}>
    <DialogTitle>RSU Status</DialogTitle>
    <DialogContent>
      <Typography variant="subtitle2">RSU IP: {rsuIp}</Typography>
      {rsuState ? (
        <div>
          <div>Timestamp: {rsuState.timestamp}</div>
          <div>Temperature: {rsuState.temperature}</div>
          <div>Uptime: {rsuState.uptime}</div>
          <div>Mode: {rsuState.mode}</div>
        </div>
      ) : (
        <Typography variant="body2">No RSU status data.</Typography>
      )}
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Close</Button>
    </DialogActions>
  </Dialog>
)

export default RsuStatusDialog

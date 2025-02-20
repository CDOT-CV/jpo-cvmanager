import { 
    Dialog, 
    DialogTitle, 
    Container, 
    DialogActions, 
    Button, 
    Typography 
} from '@mui/material'
import React, { useEffect } from 'react'
import { PcapTables } from './pcap-tables'
import {
  setPcapDecoderDialogOpen,
  selectDialogOpen,
  updateMap,
  selectSelectedMap,
} from './pcap-decoder-slice'
import { useDispatch, useSelector } from 'react-redux'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../../store'
import { 
    selectDecoderModeEnabled,
 } from '../map/map-slice'

const PcapUploadDialog = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()

  const open = useSelector(selectDialogOpen)
  const decoderModeEnabled = useSelector(selectDecoderModeEnabled)
  const selectedMap = useSelector(selectSelectedMap)

  const handleClose = () => {
    dispatch(setPcapDecoderDialogOpen(false))
  }

  useEffect(() => {
    if (decoderModeEnabled) {
      
    }
  }, [decoderModeEnabled])

    useEffect(() => {
        console.log("useEffect.selectedMap")
        dispatch(updateMap())
    }, [selectedMap])

  return (
    <>
      <Dialog onClose={handleClose} open={open} fullWidth maxWidth={'lg'}>
        <DialogTitle>PCAP Upload</DialogTitle>
        <Container sx={{ height: '60vh' }}>
          <PcapTables/>
        </Container>     
        <DialogActions>
          <Button autoFocus onClick={handleClose} variant="contained">
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}

export default PcapUploadDialog

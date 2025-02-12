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
  updateCurrentBsms,
  selectData,
  selectSelectedBsms,
  updateAllDataOnMap,
  selectSelectedMapMessage,
  onItemSelected,
} from './pcap-decoder-slice'
import { useDispatch, useSelector } from 'react-redux'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../../store'
import { selectDecoderModeEnabled } from '../map/map-slice'

const PcapUploadDialog = () => {
    console.log("PcapUploadDialog")
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()

  const open = useSelector(selectDialogOpen)
  const data = useSelector(selectData)
  const selectedBsms = useSelector(selectSelectedBsms)
  const selectedMapMessage = useSelector(selectSelectedMapMessage)
  const decoderModeEnabled = useSelector(selectDecoderModeEnabled)

  const handleClose = () => {
    dispatch(setPcapDecoderDialogOpen(false))
  }

  useEffect(() => {
    if (decoderModeEnabled) {
      if (Object.values(data).filter((v) => v.type === 'MAP').length !== 0 && selectedMapMessage === undefined) {
        dispatch(onItemSelected(Object.values(data).filter((v) => v.type === 'MAP')[0].id))
      }
      dispatch(updateCurrentBsms(Object.values(data)))
      dispatch(updateAllDataOnMap())
    }
  }, [data, selectedBsms, decoderModeEnabled])

  useEffect(() => {
    dispatch(updateAllDataOnMap())
  }, [selectedMapMessage])

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

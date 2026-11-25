import { useState } from 'react'
import ForwardingResults, { ForwardingResultsProps } from './ForwardingResults'
import { ThunkDispatch, AnyAction } from '@reduxjs/toolkit'
import { useDispatch, useSelector } from 'react-redux'

import {
  selectDestIp,
  selectSnmpMsgType,
  selectIncludeSecurityHeader,
  forwardingMessageTypes,

  // Actions
  submitSnmpSet,
  deleteSnmpSet,
  setDestIp,
  setMsgType,
  setIncludeSecurityHeader,
} from '../generalSlices/configSlice'

import { RootState } from '../store'

import './css/SnmpwalkMenu.css'
import toast from 'react-hot-toast'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  FormControl,
  FormControlLabel,
  Grid2,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material'

import AddIcon from '@mui/icons-material/Add'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'

const messageTypes = forwardingMessageTypes.map((type) => type.toLowerCase())

export type SnmpsetMenuProps = {
  type: string
  rsuIpList: string[]
  skippedIps?: string[]
}

const SnmpsetMenu = (props: SnmpsetMenuProps) => {
  const { type, rsuIpList, skippedIps = [] } = props
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()

  const destIp = useSelector(selectDestIp)
  const snmpMsgType = useSelector(selectSnmpMsgType)
  const includeSecurity = useSelector(selectIncludeSecurityHeader)
  const validDestination = /^(0|[1-9]\d{0,2})(\.(0|[1-9]\d{0,2})){3}$/.test(destIp)
    && destIp.split('.').every((octet) => Number(octet) <= 255)
  const canSubmit = Boolean(rsuIpList?.length) && validDestination && messageTypes.includes(snmpMsgType)

  const [report, setReport] = useState<Omit<ForwardingResultsProps, 'onDismiss'> | null>(null)
  const [pending, setPending] = useState(false)
  const [submissionError, setSubmissionError] = useState('')

  const submit = async (operation: 'create' | 'delete') => {
    if (!canSubmit || pending) return
    setPending(true)
    setReport(null)
    setSubmissionError('')
    // Capture the form values so changing the form does not relabel a completed request.
    const messageType = snmpMsgType
    const destination = destIp
    try {
      const action = operation === 'create'
        ? await dispatch(submitSnmpSet(rsuIpList))
        : await dispatch(deleteSnmpSet({ ipList: rsuIpList, snmpMsgType: messageType, destIp: destination }))
      if (!submitSnmpSet.fulfilled.match(action) && !deleteSnmpSet.fulfilled.match(action)) {
        const message = action.error.message || 'Request did not complete'
        if (type === 'single_rsu') toast.error(message)
        else setSubmissionError(message)
        return
      }
      const { results, changeSuccess, errorState } = action.payload
      if (type !== 'single_rsu') {
        if (results.length) setReport({ operation, messageType, destination, results })
        else setSubmissionError(errorState)
        return
      }
      if (!changeSuccess) toast.error('Failed to add forwarding: ' + errorState)
      else if (results[0]?.result?.changed === false) toast('This forwarding configuration already exists.')
      else toast.success('Forwarding Changes Applied Successfully')
    } finally {
      setPending(false)
    }
  }

  return (
    <div>
      {skippedIps.length > 0 && (
        <Box component="details" sx={{ mb: 1 }}>
          <Typography component="summary" variant="body2">Skipped (legacy SNMP): {skippedIps.length}</Typography>
          <Box sx={{ maxHeight: 80, overflowY: 'auto' }}>
            {skippedIps.map((ip) => <Typography key={ip} variant="caption" component="div">{ip}</Typography>)}
          </Box>
        </Box>
      )}
      {rsuIpList?.length === 0 && <Alert severity="warning" sx={{ mb: 1 }}>No NTCIP 1218 RSUs selected.</Alert>}
      <form id="snmpform" onSubmit={(event) => event.preventDefault()}>
        <Grid2 container spacing={1}>
          <Grid2 size={8}>
            <FormControl fullWidth margin="dense">
              <TextField
                label="Destination IPv4"
                required
                placeholder="Enter Valid IP Address"
                id="snmpinput"
                color="info"
                variant="outlined"
                value={destIp}
                error={destIp.length > 0 && !validDestination}
                onChange={(event) => {
                  const ip = event.target.value as string
                  dispatch(setDestIp(ip))
                }}
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
            </FormControl>
          </Grid2>
          <Grid2 size={4}>
            <FormControl fullWidth margin="dense">
              <InputLabel htmlFor="msg-type-select">Message Type</InputLabel>
              <Select
                label="Message Type"
                id="msg-type-select"
                value={snmpMsgType}
                onChange={(event) => {
                  const msgType = event.target.value as string
                  dispatch(setMsgType(msgType))
                }}
              >
                {messageTypes.map((msgType) => (
                  <MenuItem key={msgType} value={msgType}>
                    {msgType === 'spat' ? 'SPaT' : msgType.toUpperCase()}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid2>
        </Grid2>
        <FormControlLabel
          control={
            <Checkbox
              checked={includeSecurity}
              onChange={(e) => {
                dispatch(setIncludeSecurityHeader(e.target.checked))
              }}
            />
          }
          label="Security Header"
        />
      </form>
      <Stack spacing={1} direction="row" alignItems="center">
        <Button
          className="museo-slab capital-case"
          aria-label="Add Forwarding"
          startIcon={<AddIcon />}
          variant="outlined"
          size="medium"
          color="info"
          disabled={pending || !canSubmit}
          onClick={() => submit('create')}
        >
          Add
        </Button>

        {type !== 'single_rsu' && (
          <Button
            className="museo-slab capital-case"
            aria-label="Delete Forwarding"
            startIcon={<DeleteOutlineIcon />}
            variant="outlined"
            color="info"
            size="medium"
            disabled={pending || !canSubmit}
            onClick={() => submit('delete')}
          >
            Delete
          </Button>
        )}
      </Stack>
      {pending && <Typography variant="caption" component="div" role="status" sx={{ mt: 1 }}>Applying forwarding...</Typography>}
      {Boolean(submissionError) && <Typography variant="body2" role="alert" sx={{ mt: 1 }}>{submissionError}</Typography>}
      {report && <ForwardingResults {...report} onDismiss={() => setReport(null)} />}


    </div>
  )
}

export default SnmpsetMenu

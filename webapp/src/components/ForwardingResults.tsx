import {
  Box, IconButton, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography,
} from '@mui/material'
import CheckIcon from '@mui/icons-material/Check'
import CloseIcon from '@mui/icons-material/Close'
import { ForwardingDeviceOutcome } from '../models/RsuMessageForwarding'

export type ForwardingResultsProps = {
  operation: 'create' | 'delete'
  messageType: string
  destination: string
  results: ForwardingDeviceOutcome[]
  onDismiss: () => void
}

/** Keeps batch outcomes reviewable within the map overlay; tooltips also open on keyboard focus. */
const ForwardingResults = ({ operation, messageType, destination, results, onDismiss }: ForwardingResultsProps) => {
  const successful = results.filter((row) => row.success).length
  const skipped = results.filter((row) => row.skipped).length
  return (
    <Box component="section" aria-label="Forwarding results" sx={{ mt: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between">
        <Typography variant="body2" sx={{ fontWeight: 'bold', overflowWrap: 'anywhere' }}>
          {operation === 'create' ? 'Add' : 'Delete'} {messageType.toUpperCase()} → {destination}
        </Typography>
        <IconButton size="small" aria-label="Dismiss forwarding results" onClick={onDismiss}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </Stack>
      <Typography variant="caption" component="div" role="status">
        {successful} successful · {results.length - successful - skipped} need attention
        {skipped > 0 && ` / ${skipped} skipped`}
      </Typography>
      <TableContainer sx={{ maxHeight: 200, overflowY: 'auto' }}>
        <Table stickyHeader size="small" aria-label="Forwarding results by RSU" sx={{ tableLayout: 'fixed' }}>
          <TableHead>
            <TableRow><TableCell align="center">RSU</TableCell><TableCell align="center">Status</TableCell></TableRow>
          </TableHead>
          <TableBody>
            {[...results].sort((a, b) => Number(a.success) - Number(b.success)).map((row) => (
              <TableRow key={row.ip}>
                <TableCell align="center">{row.ip}</TableCell>
                <TableCell align="center" sx={{ py: 0.5 }}>
                  <Tooltip title={resultDetail(row, operation)} describeChild>
                    <Box component="span" tabIndex={0} aria-label={`${row.ip}: ${resultDetail(row, operation)}`}
                      sx={{ display: 'inline-flex', verticalAlign: 'middle' }}>
                      {row.success ? <CheckIcon fontSize="small" sx={{ color: (theme) =>
                        theme.palette.mode === 'dark' ? theme.palette.success.light : theme.palette.success.main }} /> : <CloseIcon fontSize="small" sx={{ color: (theme) =>
                        theme.palette.mode === 'dark' ? theme.palette.error.light : theme.palette.error.main }} />}
                    </Box>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  )
}

function resultDetail(row: ForwardingDeviceOutcome, operation: 'create' | 'delete'): string {
  if (row.skipped) return 'Skipped: requires NTCIP 1218'
  if (row.success) {
    if (row.result?.changed === false) return operation === 'create' ? 'Already configured' : 'Already absent'
    return operation === 'create' ? 'Added' : 'Deleted'
  }
  const result = row.result
  if (result?.writeState === 'UNKNOWN' || result?.writeState === 'ACKNOWLEDGED_UNVERIFIED'
      || result?.execution.status === 'RUNNING') return 'Unconfirmed; check device before retrying'
  if (result?.changed === true && result.writeState === 'VERIFIED') return 'Change verified; cache not updated'
  if (result?.changed === false && result.databaseSync !== 'UPDATED') return 'No change needed; cache not updated'
  if (row.status === 401) return 'Sign in again'
  if (row.status === 403) return 'Not authorized'
  if (row.status === 400) return 'Invalid request'
  if (row.status === 503 || result?.execution.status === 'REJECTED') return 'Service busy'
  if (row.status === 504 || result?.execution.status === 'TIMED_OUT'
      || result?.execution.status === 'QUEUE_EXPIRED') return 'Timed out'
  if (result?.execution.status === 'CANCELLED') return 'Cancelled; check device before retrying'
  return result ? 'Failed' : 'No response; check device before retrying'
}

export default ForwardingResults

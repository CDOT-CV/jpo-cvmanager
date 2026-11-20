import {
  Box, IconButton, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography,
} from '@mui/material'
import CheckIcon from '@mui/icons-material/Check'
import CloseIcon from '@mui/icons-material/Close'
import { RsuOperationOutcome } from '../models/RsuApi'

/** Shared batch results presentation for map configuration operations. */
const RsuOperationResults = ({ title, label, results, onDismiss }: {
  title: string
  label: string
  results: RsuOperationOutcome[]
  onDismiss: () => void
}) => {
  const successful = results.filter((row) => row.success).length
  return (
    <Box component="section" aria-label={label} sx={{ mt: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between">
        <Typography variant="body2" sx={{ fontWeight: 'bold', overflowWrap: 'anywhere' }}>{title}</Typography>
        <IconButton size="small" aria-label={`Dismiss ${label.toLowerCase()}`} onClick={onDismiss}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </Stack>
      <Typography variant="caption" component="div" role="status">
        {successful} successful · {results.length - successful} need attention
      </Typography>
      <TableContainer sx={{ maxHeight: 200, overflowY: 'auto' }}>
        <Table stickyHeader size="small" aria-label={`${label} by RSU`} sx={{ tableLayout: 'fixed' }}>
          <TableHead>
            <TableRow><TableCell align="center">RSU</TableCell><TableCell align="center">Status</TableCell></TableRow>
          </TableHead>
          <TableBody>
            {[...results].sort((a, b) => Number(a.success) - Number(b.success)).map((row) => (
              <TableRow key={row.ip}>
                <TableCell align="center">{row.ip}</TableCell>
                <TableCell align="center" sx={{ py: 0.5 }}>
                  <Tooltip title={row.detail} describeChild>
                    <Box component="span" tabIndex={0} aria-label={`${row.ip}: ${row.detail}`}
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

export default RsuOperationResults

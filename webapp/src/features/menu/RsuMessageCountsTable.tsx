import {
  Chip,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  useTheme,
} from '@mui/material'
import { formatCount, RsuCountTableRow } from './rsuCountTable'

const numberCellSx = {
  fontVariantNumeric: 'tabular-nums',
  fontSize: '0.875rem',
  fontWeight: 600,
} as const

export const RsuMessageCountsTable = ({ rows }: { rows: RsuCountTableRow[] }) => {
  const theme = useTheme()
  const headerBackground = theme.palette.custom?.tableHeaderBackground ?? theme.palette.background.paper

  return (
    <TableContainer
      component={Paper}
      elevation={0}
      sx={{
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: 1,
        overflow: 'hidden',
      }}
    >
      <Table size="small" aria-label="RSU message counts">
        <TableHead>
          <TableRow>
            <TableCell sx={{ backgroundColor: headerBackground, py: 1.25, px: 1.5 }}>Type</TableCell>
            <TableCell align="right" sx={{ backgroundColor: headerBackground, py: 1.25, px: 1.5 }}>
              Input
            </TableCell>
            <TableCell align="right" sx={{ backgroundColor: headerBackground, py: 1.25, px: 1.5 }}>
              Processed
            </TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row, index) => {
            const hasCounts = row.odeInputCount > 0 || row.odeOutputCount > 0
            return (
              <TableRow
                key={row.messageType}
                hover
                sx={{
                  backgroundColor: index % 2 === 0 ? 'transparent' : theme.palette.action.hover,
                }}
              >
                <TableCell sx={{ py: 1, px: 1.5, borderBottomColor: theme.palette.divider }}>
                  <Chip
                    label={row.messageType}
                    size="small"
                    color={hasCounts ? 'info' : 'default'}
                    variant="outlined"
                    sx={{ fontWeight: 600, minWidth: 64 }}
                  />
                </TableCell>
                <TableCell
                  align="right"
                  sx={{
                    ...numberCellSx,
                    py: 1,
                    px: 1.5,
                    borderBottomColor: theme.palette.divider,
                    color: hasCounts ? theme.palette.text.primary : theme.palette.text.disabled,
                  }}
                >
                  {formatCount(row.odeInputCount)}
                </TableCell>
                <TableCell
                  align="right"
                  sx={{
                    ...numberCellSx,
                    py: 1,
                    px: 1.5,
                    borderBottomColor: theme.palette.divider,
                    color: hasCounts ? theme.palette.text.primary : theme.palette.text.disabled,
                  }}
                >
                  {formatCount(row.odeOutputCount)}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

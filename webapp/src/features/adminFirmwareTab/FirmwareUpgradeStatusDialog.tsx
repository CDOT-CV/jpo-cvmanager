import {
  Alert, Box, Button, CircularProgress, Dialog, DialogContent,
  Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material'
import { Refresh } from '@mui/icons-material'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import { useGetFirmwareUpgradeStatusQuery } from '../api/firmwareApiSlice'

const FirmwareUpgradeStatusDialog = ({ onClose }: { onClose: () => void }) => {
  const { data, isFetching, isError, refetch, fulfilledTimeStamp } = useGetFirmwareUpgradeStatusQuery(undefined, {
    refetchOnMountOrArgChange: true,
  })
  const active = Object.entries(data?.active_upgrades ?? {})

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogContent>
        <SideBarHeader title="Firmware Upgrade Scheduler Status" onClick={onClose} closeButtonLabel="Close" />
        <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2} sx={{ mb: 2 }}>
          <Typography variant="body2" color="text.secondary">
            {fulfilledTimeStamp ? `Last updated: ${new Date(fulfilledTimeStamp).toLocaleString()}` : null}
          </Typography>
          <Button variant="outlined" color="info" size="small" startIcon={<Refresh />} disabled={isFetching}
            sx={{ padding: '4px 8px', margin: '0px 4px' }}
            onClick={() => void refetch()}>
            <Typography className="capital-case museo-slab" fontSize="12px">Refresh</Typography>
          </Button>
        </Stack>
        {isFetching && (
          <Box sx={{ textAlign: 'center', py: 3 }}>
            <CircularProgress aria-label="Loading firmware upgrade status" />
          </Box>
        )}
        {isError && !isFetching && (
          <Alert severity="error">Unable to retrieve firmware upgrade status. Please refresh to try again.</Alert>
        )}
        {data && !isFetching && !isError && (
          <Stack spacing={2}>
            <Box component="section" aria-label="Active upgrades">
              <Typography variant="subtitle1" fontWeight="bold" sx={{ mb: 1 }}>Active ({active.length})</Typography>
              {active.length === 0 ? <Typography>No active firmware upgrades.</Typography> : (
                <TableContainer sx={{ bgcolor: 'action.hover', border: 1, borderColor: 'divider', borderRadius: 1 }}>
                  <Table size="small" aria-label="Active upgrades" sx={{
                    '& th': { textTransform: 'none !important', bgcolor: 'custom.tableHeaderBackground' },
                    '& td': { overflowWrap: 'anywhere' },
                  }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>RSU IP</TableCell>
                        <TableCell>Manufacturer</TableCell>
                        <TableCell>Model</TableCell>
                        <TableCell>Target Firmware</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {active.map(([ip, upgrade]) => (
                        <TableRow key={ip}>
                          <TableCell>{ip}</TableCell>
                          <TableCell>{upgrade.manufacturer}</TableCell>
                          <TableCell>{upgrade.model}</TableCell>
                          <TableCell>{upgrade.target_firmware_version}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Box>
            <Box component="section" aria-label="Queued upgrades">
              <Typography variant="subtitle1" fontWeight="bold" sx={{ mb: 1 }}>Queued ({data.upgrade_queue.length})</Typography>
              {data.upgrade_queue.length === 0 ? <Typography>No queued firmware upgrades.</Typography> : (
                <TableContainer sx={{ bgcolor: 'action.hover', border: 1, borderColor: 'divider', borderRadius: 1 }}>
                  <Table size="small" aria-label="Queued upgrades" sx={{
                    '& th': { textTransform: 'none !important', bgcolor: 'custom.tableHeaderBackground' },
                  }}>
                    <TableHead>
                      <TableRow><TableCell>Queue Position</TableCell><TableCell>RSU IP</TableCell></TableRow>
                    </TableHead>
                    <TableBody>
                      {data.upgrade_queue.map((ip, index) => (
                        <TableRow key={ip}>
                          <TableCell>{index + 1}</TableCell><TableCell>{ip}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Box>
            <Typography variant="body2" color="text.secondary">
              This is a scheduler snapshot, not installation progress. Refresh to see changes.
            </Typography>
          </Stack>
        )}
      </DialogContent>
    </Dialog>
  )
}

export default FirmwareUpgradeStatusDialog

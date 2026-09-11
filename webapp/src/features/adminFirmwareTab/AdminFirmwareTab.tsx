import { useState } from 'react'
import {
  alpha,
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
  useTheme,
} from '@mui/material'
import { AddCircleOutline, Refresh } from '@mui/icons-material'
import { useGetFirmwareUploadOptionsQuery, useListFirmwareObjectsQuery } from '../api/firmwareApiSlice'
import FirmwareUploadForm from './FirmwareUploadForm'
import '../adminRsuTab/Admin.css'

const PAGE_SIZE = 100

const formatUpdatedAt = (value: string | number | null | undefined) => {
  if (value == null) return '—'
  const timestamp = typeof value === 'number' ? value * 1000 : value
  return new Date(timestamp).toLocaleString()
}

const verificationLabel = {
  VERIFIED: 'Verified',
  UNVERIFIED: 'Unverified',
  UNTRACKED: 'Untracked',
  CHANGED: 'Object changed',
}

const AdminFirmwareTab = () => {
  const theme = useTheme()
  const [tokens, setTokens] = useState<(string | undefined)[]>([undefined])
  const [manufacturer, setManufacturer] = useState('')
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<string>()
  const [showUpload, setShowUpload] = useState(false)

  const {
    currentData: data,
    isFetching,
    error,
    refetch,
  } = useListFirmwareObjectsQuery(
    {
      page_size: PAGE_SIZE,
      page_token: tokens[tokens.length - 1],
      manufacturer: manufacturer || undefined,
    },
    { refetchOnMountOrArgChange: true }
  )
  const { data: uploadOptions, isFetching: isFetchingOptions } = useGetFirmwareUploadOptionsQuery()

  const normalizedSearch = search.trim().toLowerCase()
  const visibleObjects =
    data?.objects.filter((object) => {
      if (!normalizedSearch) return true
      return [object.manufacturer, object.model, object.version, object.file_name, object.object_name].some((value) =>
        value?.toLowerCase().includes(normalizedSearch)
      )
    }) ?? []
  const selectedObject = data?.objects.find((object) => object.object_id === selected)

  const resetListing = () => {
    setSelected(undefined)
    setTokens([undefined])
    if (tokens.length === 1) refetch()
  }

  return (
    <Stack spacing={2} className="scroll-div-tab">
      <Stack direction="row" spacing={2} alignItems="center">
        <Typography variant="h5" className="panel-header" sx={{ flexGrow: 1, pl: 0 }}>
          Firmware
        </Typography>
        <Button
          variant="outlined"
          color="info"
          size="small"
          startIcon={<Refresh />}
          className="museo-slab capital-case"
          disabled={isFetching}
          onClick={resetListing}
        >
          Refresh
        </Button>
        <Button
          variant="contained"
          size="small"
          startIcon={<AddCircleOutline />}
          className="museo-slab capital-case"
          onClick={() => setShowUpload(true)}
        >
          Add Firmware
        </Button>
      </Stack>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <TextField
          label="Search firmware"
          size="small"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          sx={{ minWidth: 280 }}
        />
        <FormControl size="small" sx={{ minWidth: 240 }} disabled={isFetchingOptions}>
          <InputLabel id="firmware-filter-manufacturer-label">Manufacturer</InputLabel>
          <Select
            labelId="firmware-filter-manufacturer-label"
            label="Manufacturer"
            value={manufacturer}
            onChange={(event) => {
              setManufacturer(event.target.value)
              setSelected(undefined)
              setTokens([undefined])
            }}
          >
            <MenuItem value="">All manufacturers</MenuItem>
            {uploadOptions?.manufacturers.map((option) => (
              <MenuItem key={option.manufacturer_id} value={option.name}>
                {option.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>

      {error && <Alert severity="error">Unable to load firmware files. Please try refreshing.</Alert>}
      {isFetching && (
        <Box role="status" aria-label="Loading firmware files">
          <CircularProgress size={24} />
        </Box>
      )}

      {!error && data && (
        <>
          <TableContainer component={Paper} sx={{ boxShadow: 'none' }} className="admin-table">
            <Table aria-label="Firmware files">
              <TableHead>
                <TableRow>
                  <TableCell>Manufacturer</TableCell>
                  <TableCell>Model</TableCell>
                  <TableCell>Version</TableCell>
                  <TableCell>File</TableCell>
                  <TableCell>Size</TableCell>
                  <TableCell>Last modified</TableCell>
                  <TableCell>Verification</TableCell>
                </TableRow>
              </TableHead>
              <TableBody
                sx={{
                  '& .MuiTableRow-root': {
                    border: `1px solid ${alpha(theme.palette.divider, 0.1)}`,
                  },
                  '& .MuiTableCell-body': {
                    color: `${theme.palette.text.secondary} !important`,
                    padding: '6px 16px',
                    textTransform: 'none',
                  },
                  '& .MuiButtonBase-root': {
                    borderRadius: '4px',
                  },
                }}
              >
                {visibleObjects.map((object) => (
                  <TableRow key={object.object_id} hover selected={selected === object.object_id}>
                    <TableCell>{object.manufacturer ?? ''}</TableCell>
                    <TableCell>{object.model ?? ''}</TableCell>
                    <TableCell>{object.version ?? ''}</TableCell>
                    <TableCell sx={{ overflowWrap: 'anywhere' }}>
                      <Button
                        sx={{ minWidth: 0, p: 0, textTransform: 'none', textAlign: 'left' }}
                        onClick={() => setSelected(object.object_id)}
                      >
                        {object.file_name}
                      </Button>
                    </TableCell>
                    <TableCell>{`${object.content_length.toLocaleString()} bytes`}</TableCell>
                    <TableCell>{formatUpdatedAt(object.updated_at)}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={verificationLabel[object.verification_status]}
                        color={object.verification_status === 'VERIFIED' ? 'success' : 'default'}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {visibleObjects.length === 0 && <Typography sx={{ p: 3 }}>No matching firmware on this page.</Typography>}
          </TableContainer>

          <Stack direction="row" spacing={2} alignItems="center">
            <Button
              disabled={tokens.length === 1 || isFetching}
              onClick={() => {
                setSelected(undefined)
                setTokens(tokens.slice(0, -1))
              }}
            >
              Previous
            </Button>
            <Typography>Page {tokens.length}</Typography>
            <Button
              disabled={!data.next_page_token || isFetching}
              onClick={() => {
                setSelected(undefined)
                setTokens([...tokens, data.next_page_token!])
              }}
            >
              Next
            </Button>
          </Stack>
          <Typography variant="caption" color="text.secondary">
            Search filters the current page. Untracked files have no upload record; changed objects no longer match
            their verified version.
          </Typography>

          {selectedObject && (
            <Paper variant="outlined" sx={{ p: 2, overflowWrap: 'anywhere' }}>
              <Typography variant="h6">File details</Typography>
              <Typography>{selectedObject.object_name}</Typography>
              <Typography>Upload status: {selectedObject.upload_status ?? 'No upload record'}</Typography>
              <Typography>Upload ID: {selectedObject.upload_id ?? '—'}</Typography>
              <Typography>Firmware ID: {selectedObject.firmware_id ?? 'Not registered'}</Typography>
              <Typography>Object version: {selectedObject.provider_object_version ?? '—'}</Typography>
            </Paper>
          )}
        </>
      )}

      {showUpload && (
        <FirmwareUploadForm
          open
          onClose={() => setShowUpload(false)}
          onSuccess={() => {
            setShowUpload(false)
            resetListing()
          }}
        />
      )}
    </Stack>
  )
}

export default AdminFirmwareTab

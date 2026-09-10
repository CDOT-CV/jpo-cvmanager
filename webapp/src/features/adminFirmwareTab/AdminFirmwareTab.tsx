import { ReactNode, useState } from 'react'
import {
  alpha,
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  useTheme,
} from '@mui/material'
import { AddCircleOutline, KeyboardArrowDown, KeyboardArrowRight, Refresh } from '@mui/icons-material'
import { useListFirmwareObjectsQuery } from '../api/firmwareApiSlice'
import { FirmwareObject } from '../../models/Firmware'
import FirmwareUploadForm from './FirmwareUploadForm'
import '../adminRsuTab/Admin.css'

type TreeNode = {
  path: string
  label: string
  children: Map<string, TreeNode>
  object?: FirmwareObject
}

function buildTree(objects: FirmwareObject[]) {
  const root: TreeNode = { path: '', label: '', children: new Map() }

  for (const object of objects) {
    let node = root

    const pathSegments = object.object_name.split('/').filter((part) => part.length > 0)
    pathSegments.forEach((part) => {
      const path = node.path ? `${node.path}/${part}` : part
      if (!node.children.has(part))
        node.children.set(part, {
          path,
          label: part,
          children: new Map(),
        })
      node = node.children.get(part)!
    })

    // GCS may return zero-byte directory marker objects whose names end in '/'.
    // They define the folder structure but are not selectable firmware files.
    if (pathSegments.length > 0 && !object.object_name.endsWith('/')) node.object = object
  }

  return root
}

const formatUpdatedAt = (value: string | number | null | undefined) => {
  if (value == null) return '—'
  const timestamp = typeof value === 'number' ? value * 1000 : value
  return new Date(timestamp).toLocaleString()
}

const AdminFirmwareTab = () => {
  const theme = useTheme()
  const [tokens, setTokens] = useState<(string | undefined)[]>([undefined])
  const [closed, setClosed] = useState<Set<string>>(new Set())
  const [selected, setSelected] = useState<string>()
  const [showUpload, setShowUpload] = useState(false)

  const {
    currentData: data,
    isFetching,
    error,
    refetch,
  } = useListFirmwareObjectsQuery(
    { page_token: tokens[tokens.length - 1], page_size: 100 },
    { refetchOnMountOrArgChange: true }
  )
  const selectedObject = data?.objects.find((object) => object.object_id === selected)

  const toggle = (path: string) =>
    setClosed((previous) => {
      const next = new Set(previous)
      if (next.has(path)) next.delete(path)
      else next.add(path)
      return next
    })

  const rows = (node: TreeNode, depth = 0): ReactNode[] =>
    [...node.children.values()].flatMap((child) => {
      const object = child.object
      return [
        <TableRow key={child.path} hover selected={!!object && selected === object.object_id}>
          <TableCell sx={{ overflowWrap: 'anywhere' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', ml: depth * 3 }}>
              {child.children.size > 0 ? (
                <IconButton
                  size="small"
                  aria-label={`Toggle ${child.path}`}
                  aria-expanded={!closed.has(child.path)}
                  onClick={() => toggle(child.path)}
                  sx={{ width: 32, height: 32, mr: 0.5 }}
                >
                  {closed.has(child.path) ? <KeyboardArrowRight /> : <KeyboardArrowDown />}
                </IconButton>
              ) : (
                <Box sx={{ width: 32, mr: 0.5, flexShrink: 0 }} />
              )}
              {object ? (
                <Button
                  sx={{
                    minWidth: 0,
                    p: 0,
                    textTransform: 'none',
                    textAlign: 'left',
                    overflowWrap: 'anywhere',
                  }}
                  onClick={() => setSelected(object.object_id)}
                >
                  {child.label}
                </Button>
              ) : (
                child.label
              )}
            </Box>
          </TableCell>
          <TableCell>{object ? `${object.content_length.toLocaleString()} bytes` : null}</TableCell>
          <TableCell>{object ? formatUpdatedAt(object.updated_at) : null}</TableCell>
          <TableCell>
            {object && (
              <Chip
                size="small"
                label={
                  {
                    VERIFIED: 'Verified',
                    UNVERIFIED: 'Unverified',
                    UNTRACKED: 'Untracked',
                    CHANGED: 'Object changed',
                  }[object.verification_status]
                }
                color={object.verification_status === 'VERIFIED' ? 'success' : 'default'}
              />
            )}
          </TableCell>
        </TableRow>,
        ...(!closed.has(child.path) ? rows(child, depth + 1) : []),
      ]
    })

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
          onClick={() => {
            setSelected(undefined)
            setTokens([undefined])
            if (tokens.length === 1) refetch()
          }}
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
                  <TableCell>Name</TableCell>
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
                {rows(buildTree(data.objects))}
              </TableBody>
            </Table>
            {data.objects.length === 0 && <Typography sx={{ p: 3 }}>No firmware files on this page.</Typography>}
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
            Folders group files on the current page. Untracked files have no upload record; changed objects no longer
            match their verified version.
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
            setSelected(undefined)
            setTokens([undefined])
          }}
        />
      )}
    </Stack>
  )
}

export default AdminFirmwareTab

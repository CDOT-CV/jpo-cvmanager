import { ReactNode, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useListFirmwareObjectsQuery } from '../api/firmwareApiSlice'
import { FirmwareObject } from '../../models/Firmware'
import FirmwareUploadForm from './FirmwareUploadForm'

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
    object.object_name.split('/').forEach((part, index, parts) => {
      const path = parts.slice(0, index + 1).join('/')
      if (!node.children.has(part))
        node.children.set(part, {
          path,
          label: part || '(empty segment)',
          children: new Map(),
        })
      node = node.children.get(part)!
    })
    node.object = object
  }

  return root
}

const AdminFirmwareTab = () => {
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
          <TableCell sx={{ pl: 2 + depth * 3, overflowWrap: 'anywhere' }}>
            {child.children.size > 0 && (
              <Button
                size="small"
                aria-label={`Toggle ${child.path}`}
                aria-expanded={!closed.has(child.path)}
                onClick={() => toggle(child.path)}
              >
                {closed.has(child.path) ? '+' : '−'}
              </Button>
            )}
            {object ? (
              <Button
                sx={{
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
          </TableCell>
          <TableCell>{object ? `${object.content_length.toLocaleString()} bytes` : '—'}</TableCell>
          <TableCell>{object?.updated_at ? new Date(object.updated_at).toLocaleString() : '—'}</TableCell>
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

  if (showUpload)
    return (
      <Stack spacing={2}>
        <Button
          onClick={() => {
            setShowUpload(false)
            refetch()
          }}
        >
          Back to firmware files
        </Button>
        <FirmwareUploadForm />
      </Stack>
    )

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Typography variant="h5" className="panel-header" sx={{ flexGrow: 1, pl: 0 }}>
          Firmware
        </Typography>
        <Button
          disabled={isFetching}
          onClick={() => {
            setSelected(undefined)
            setTokens([undefined])
            if (tokens.length === 1) refetch()
          }}
        >
          Refresh
        </Button>
        <Button variant="contained" onClick={() => setShowUpload(true)}>
          Upload firmware
        </Button>
      </Stack>

      {data && <Typography color="text.secondary">{data.container}</Typography>}
      {error && <Alert severity="error">Unable to load firmware files. Please try refreshing.</Alert>}
      {isFetching && (
        <Box role="status" aria-label="Loading firmware files">
          <CircularProgress size={24} />
        </Box>
      )}

      {!error && data && (
        <>
          <TableContainer component={Paper} variant="outlined">
            <Table aria-label="Firmware files">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Size</TableCell>
                  <TableCell>Last modified</TableCell>
                  <TableCell>Verification</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>{rows(buildTree(data.objects))}</TableBody>
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
    </Stack>
  )
}

export default AdminFirmwareTab

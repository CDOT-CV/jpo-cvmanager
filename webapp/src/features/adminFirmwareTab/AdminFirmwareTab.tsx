import { useCallback, useEffect, useRef, useState } from 'react'
import { Action, Column, Query } from '@material-table/core'
import { Box, Chip, FormControl, InputLabel, MenuItem, Paper, Select, Typography, useTheme } from '@mui/material'
import { DeleteOutline } from '@mui/icons-material'
import { confirmAlert } from 'react-confirm-alert'
import { Options } from '../../components/AdminDeletionOptions'
import toast from 'react-hot-toast'
import AdminTable from '../../components/AdminTable'
import { FirmwareObject } from '../../models/Firmware'
import {
  useDeleteFirmwareObjectMutation,
  useGetFirmwareUploadOptionsQuery,
  useLazyListFirmwareObjectsQuery,
  useListFirmwareObjectsQuery,
} from '../api/firmwareApiSlice'
import FirmwareUploadForm from './FirmwareUploadForm'
import { formatFileSize } from './firmwareUpload'
import '../adminRsuTab/Admin.css'

const DEFAULT_PAGE_SIZE = 25
const HEADER_STYLE = { textTransform: 'none' as const }
const FIRMWARE_SORT_FIELDS = [
  'manufacturer',
  'model',
  'version',
  'content_length',
  'updated_at',
  'verification_status',
]

const firmwareSort = (query: Query<FirmwareObject>) => {
  const sort = query.orderByCollection?.[0]
  const orderBy = sort?.orderBy as number | Column<FirmwareObject> | undefined
  const field = typeof orderBy === 'number' ? FIRMWARE_SORT_FIELDS[orderBy] : orderBy?.field
  return typeof field === 'string'
    ? `${field},${sort.orderDirection || 'asc'}`
    : 'manufacturer,asc'
}

type FirmwareListParams = {
  page: number
  size: number
  search: string
  manufacturer?: string
  sort: string
}

const listingSignature = (params: FirmwareListParams, result: { objects: FirmwareObject[]; total_elements: number }) =>
  JSON.stringify({ params, objects: result.objects, totalElements: result.total_elements })

const formatUpdatedAt = (value: string | number | null | undefined) => {
  if (value == null) return ''
  const timestamp = typeof value === 'number' ? value * 1000 : value
  return new Date(timestamp).toLocaleString()
}

const verificationLabel = {
  VERIFIED: 'Verified',
  UNVERIFIED: 'Unverified',
  UNTRACKED: 'Untracked',
  CHANGED: 'Object changed',
  MISSING: 'Missing file',
}

const AdminFirmwareTab = () => {
  const theme = useTheme()
  const tableRef = useRef<any>(null)
  const manufacturerRef = useRef('')
  const renderedListingSignature = useRef<string>()
  const [manufacturer, setManufacturer] = useState('')
  const [selectedObject, setSelectedObject] = useState<FirmwareObject>()
  const [showUpload, setShowUpload] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [listParams, setListParams] = useState<FirmwareListParams>({
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    search: '',
    sort: 'manufacturer,asc',
  })
  const deleting = useRef(false)

  const [deleteFirmwareObject] = useDeleteFirmwareObjectMutation()
  const [listFirmwareObjects] = useLazyListFirmwareObjectsQuery()
  const { data: subscribedListing } = useListFirmwareObjectsQuery(listParams)
  const { data: uploadOptions, isFetching: isFetchingOptions } = useGetFirmwareUploadOptionsQuery()

  // Mutations refresh the subscribed cache. Notify Material Table when that
  // result differs from the rows it most recently rendered.
  useEffect(() => {
    if (!subscribedListing || isRefreshing || !tableRef.current?.onQueryChange) return

    const signature = listingSignature(listParams, subscribedListing)
    if (signature !== renderedListingSignature.current) {
      renderedListingSignature.current = signature
      tableRef.current.onQueryChange({ page: listParams.page })
    }
  }, [isRefreshing, listParams, subscribedListing])

  const refreshListing = useCallback(() => {
    setSelectedObject(undefined)
    setIsRefreshing(true)
    if (!tableRef.current?.onQueryChange) {
      setIsRefreshing(false)
      return
    }
    tableRef.current.onQueryChange({ page: 0 })
  }, [])

  const deleteFirmware = async (object: FirmwareObject) => {
    if (deleting.current) return
    const missing = object.verification_status === 'MISSING'
    if (!missing && !object.provider_object_version) {
      toast.error('This file has no object version. Refresh the table before deleting it.')
      return
    }
    deleting.current = true
    setIsDeleting(true)
    const notification = toast.loading(missing ? 'Cleaning up firmware records...' : 'Deleting firmware...')
    try {
      await deleteFirmwareObject({
        object_id: object.object_id,
        provider_object_version: missing ? null : object.provider_object_version,
      }).unwrap()
      toast.success(missing ? 'Firmware records cleaned up successfully' : 'Firmware deleted successfully', {
        id: notification,
      })
      refreshListing()
    } catch (error) {
      const response = error as { data?: { detail?: string; message?: string } }
      toast.error(response?.data?.detail || response?.data?.message || 'Failed to delete firmware. Please retry.', {
        id: notification,
      })
    } finally {
      deleting.current = false
      setIsDeleting(false)
    }
  }

  const handleQueryChange = useCallback(
    async (query: Query<FirmwareObject>) => {
      try {
        const params = {
          page: query.page,
          size: query.pageSize,
          search: query.search || '',
          manufacturer: manufacturerRef.current || undefined,
          sort: firmwareSort(query),
        }
        setListParams(params)

        const result = await listFirmwareObjects(params).unwrap()
        renderedListingSignature.current = listingSignature(params, result)

        return {
          data: result.objects,
          page: query.page,
          totalCount: result.total_elements,
        }
      } catch (error) {
        console.error('Failed to fetch firmware:', error)
        toast.error('Failed to fetch firmware')
        throw error
      } finally {
        setIsRefreshing(false)
      }
    },
    [listFirmwareObjects]
  )

  const columns: Column<FirmwareObject>[] = [
    { title: 'Manufacturer', field: 'manufacturer', headerStyle: HEADER_STYLE },
    { title: 'Model', field: 'model', headerStyle: HEADER_STYLE },
    {
      title: 'Version',
      field: 'version',
      headerStyle: HEADER_STYLE,
      render: (object) => (
        <Typography
          component="button"
          variant="body2"
          color="primary"
          sx={{ background: 'none', border: 0, cursor: 'pointer', p: 0, textAlign: 'left' }}
          onClick={() => setSelectedObject(object)}
        >
          {object.version ?? object.object_name}
        </Typography>
      ),
    },
    {
      title: 'Size',
      field: 'content_length',
      headerStyle: HEADER_STYLE,
      render: (object) => (object.content_length == null ? '' : formatFileSize(object.content_length)),
    },
    {
      title: 'Last Modified',
      field: 'updated_at',
      headerStyle: HEADER_STYLE,
      render: (object) => formatUpdatedAt(object.updated_at),
    },
    {
      title: 'Verification',
      field: 'verification_status',
      headerStyle: HEADER_STYLE,
      render: (object) => (
        <Chip
          size="small"
          label={verificationLabel[object.verification_status]}
          color={
            object.verification_status === 'VERIFIED'
              ? 'success'
              : object.verification_status === 'MISSING' ? 'warning' : 'default'
          }
        />
      ),
    },
  ]

  const tableActions: (Action<FirmwareObject> | ((row: FirmwareObject) => Action<FirmwareObject>))[] = [
    (object) => ({
      position: 'row',
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      iconProps: { itemType: 'rowAction' },
      tooltip: object.verification_status === 'MISSING' ? 'Clean Up Records' : 'Delete Firmware',
      disabled: isDeleting,
      onClick: (_event, row: FirmwareObject) => {
        const missing = row.verification_status === 'MISSING'
        confirmAlert({
          ...Options(
            missing ? 'Clean Up Firmware Records' : 'Delete Firmware',
            missing
              ? 'The file is missing from storage. Remove its database records and associated upgrade rules?'
              : 'Delete this file and its database records and associated upgrade rules?',
            [
              { label: 'Yes', onClick: () => deleteFirmware(row) },
              { label: 'No', onClick: () => {} },
            ]
          ),
          childrenElement: () => (
            <div style={{ marginTop: 16, whiteSpace: 'pre-line', overflowWrap: 'anywhere' }}>
              {`Manufacturer: ${row.manufacturer ?? 'Unknown'}\nModel: ${row.model ?? 'Unknown'}\nVersion: ${row.version ?? 'Unknown'}\nFile: ${row.file_name}`}
            </div>
          ),
        })
      },
    }),
    {
      position: 'toolbar',
      icon: () => null,
      tooltip: 'Filter by manufacturer',
      iconProps: {
        itemType: 'custom',
        render: () => (
          <FormControl size="small" sx={{ minWidth: 210, mx: 0.5 }} disabled={isFetchingOptions}>
            <InputLabel id="firmware-filter-manufacturer-label">Manufacturer</InputLabel>
            <Select
              labelId="firmware-filter-manufacturer-label"
              label="Manufacturer"
              value={manufacturer}
              onChange={(event) => {
                const value = event.target.value
                manufacturerRef.current = value
                setManufacturer(value)
                setSelectedObject(undefined)
                tableRef.current?.onQueryChange({ page: 0 })
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
        ),
      } as any,
      onClick: () => undefined,
    },
    {
      position: 'toolbar',
      icon: () => null,
      tooltip: 'Refresh firmware',
      iconProps: { itemType: 'outlined', color: 'info', title: 'Refresh' },
      disabled: isRefreshing,
      onClick: refreshListing,
    },
    {
      position: 'toolbar',
      icon: () => null,
      tooltip: 'Add firmware',
      iconProps: { itemType: 'contained', title: 'New' },
      onClick: () => setShowUpload(true),
    },
  ]

  return (
    <Box
      className="scroll-div-tab"
      sx={{
        // Match the action-column space of the two-button RSU and User tables.
        '& thead th:last-child, & tbody td:last-child:not([colspan])': {
          width: '96px !important',
        },
        '& tbody td:last-child:not([colspan]) > div': {
          justifyContent: 'center',
        },
      }}
    >
      <AdminTable
        actions={tableActions}
        columns={columns}
        defaultPageSize={DEFAULT_PAGE_SIZE}
        handleQueryChange={handleQueryChange}
        isLoading={isRefreshing}
        selection={false}
        tableRef={tableRef}
        title=""
      />

      {selectedObject && (
        <Paper variant="outlined" sx={{ mt: 2, p: 2, overflowWrap: 'anywhere' }}>
          <Typography variant="h6">File details</Typography>
          <Typography>{selectedObject.object_name}</Typography>
          {selectedObject.verification_status === 'MISSING' && (
            <Typography>The cloud file is missing. Use Clean Up Records to finish removing its database records.</Typography>
          )}
          <Typography>Upload status: {selectedObject.upload_status ?? 'No upload record'}</Typography>
          <Typography>Upload ID: {selectedObject.upload_id ?? ''}</Typography>
          <Typography>Firmware ID: {selectedObject.firmware_id ?? 'Not registered'}</Typography>
          <Typography>Object version: {selectedObject.provider_object_version ?? ''}</Typography>
        </Paper>
      )}

      {showUpload && (
        <FirmwareUploadForm
          open
          onClose={() => setShowUpload(false)}
          onSuccess={() => {
            setShowUpload(false)
            refreshListing()
          }}
        />
      )}
    </Box>
  )
}

export default AdminFirmwareTab

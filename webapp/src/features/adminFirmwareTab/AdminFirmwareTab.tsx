import { useCallback, useRef, useState } from 'react'
import { Action, Column, Query } from '@material-table/core'
import { Chip, FormControl, InputLabel, MenuItem, Paper, Select, Typography, useTheme } from '@mui/material'
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
} from '../api/firmwareApiSlice'
import FirmwareUploadForm from './FirmwareUploadForm'
import { formatFileSize } from './firmwareUpload'
import '../adminRsuTab/Admin.css'

const DEFAULT_PAGE_SIZE = 25
const HEADER_STYLE = { textTransform: 'none' as const }

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
}

const AdminFirmwareTab = () => {
  const theme = useTheme()
  const tableRef = useRef<any>(null)
  // Material Table uses page numbers while object storage returns opaque cursors
  // Keep the cursor needed to request each page as the user moves through the table
  const pageTokens = useRef<Map<number, string | undefined>>(new Map([[0, undefined]]))
  const currentPageSize = useRef(DEFAULT_PAGE_SIZE)
  const manufacturerRef = useRef('')
  const [manufacturer, setManufacturer] = useState('')
  const [selectedObject, setSelectedObject] = useState<FirmwareObject>()
  const [showUpload, setShowUpload] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const deleting = useRef(false)

  const [deleteFirmwareObject] = useDeleteFirmwareObjectMutation()
  const [listFirmwareObjects] = useLazyListFirmwareObjectsQuery()
  const { data: uploadOptions, isFetching: isFetchingOptions } = useGetFirmwareUploadOptionsQuery()

  const resetPagination = useCallback(() => {
    pageTokens.current = new Map([[0, undefined]])
    setSelectedObject(undefined)
  }, [])

  const refreshListing = useCallback(() => {
    resetPagination()
    setIsRefreshing(true)
    Promise.resolve(tableRef.current?.onQueryChange({ page: 0 })).finally(() => setIsRefreshing(false))
  }, [resetPagination])

  const deleteFirmware = async (object: FirmwareObject) => {
    if (deleting.current) return
    if (!object.provider_object_version) {
      toast.error('This file has no object version. Refresh the table before deleting it.')
      return
    }
    deleting.current = true
    setIsDeleting(true)
    const notification = toast.loading('Deleting firmware...')
    try {
      await deleteFirmwareObject({
        object_id: object.object_id,
        provider_object_version: object.provider_object_version,
      }).unwrap()
      toast.success('Firmware deleted successfully', { id: notification })
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
      // Provider cursors depend on page size and cannot be reused after it changes
      if (query.pageSize !== currentPageSize.current) {
        currentPageSize.current = query.pageSize
        pageTokens.current = new Map([[0, undefined]])
      }

      const pageToken = pageTokens.current.get(query.page)
      if (query.page > 0 && pageToken === undefined) {
        return { data: [], page: query.page, totalCount: query.page * query.pageSize }
      }

      try {
        const result = await listFirmwareObjects({
          page_size: query.pageSize,
          page_token: pageToken,
          manufacturer: manufacturerRef.current || undefined,
        }).unwrap()

        if (result.next_page_token) {
          pageTokens.current.set(query.page + 1, result.next_page_token)
        } else {
          pageTokens.current.delete(query.page + 1)
        }

        return {
          data: result.objects,
          page: query.page,
          // The provider does not return a total. One extra row keeps Next enabled
          // until the provider reports that no following cursor exists
          totalCount: result.next_page_token
            ? (query.page + 1) * query.pageSize + 1
            : query.page * query.pageSize + result.objects.length,
        }
      } catch (error) {
        console.error('Failed to fetch firmware:', error)
        toast.error('Failed to fetch firmware')
        throw error
      }
    },
    [listFirmwareObjects]
  )

  const columns: Column<FirmwareObject>[] = [
    { title: 'Manufacturer', field: 'manufacturer', sorting: false, headerStyle: HEADER_STYLE },
    { title: 'Model', field: 'model', sorting: false, headerStyle: HEADER_STYLE },
    {
      title: 'Version',
      field: 'version',
      sorting: false,
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
      sorting: false,
      headerStyle: HEADER_STYLE,
      render: (object) => formatFileSize(object.content_length),
    },
    {
      title: 'Last Modified',
      field: 'updated_at',
      sorting: false,
      headerStyle: HEADER_STYLE,
      render: (object) => formatUpdatedAt(object.updated_at),
    },
    {
      title: 'Verification',
      field: 'verification_status',
      sorting: false,
      headerStyle: HEADER_STYLE,
      render: (object) => (
        <Chip
          size="small"
          label={verificationLabel[object.verification_status]}
          color={object.verification_status === 'VERIFIED' ? 'success' : 'default'}
        />
      ),
    },
  ]

  const tableActions: Action<FirmwareObject>[] = [
    {
      position: 'row',
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      iconProps: { itemType: 'rowAction' },
      tooltip: 'Delete firmware',
      disabled: isDeleting,
      onClick: (_event, row: FirmwareObject) => {
        const name = [row.manufacturer, row.model, row.version].filter(Boolean).join(' / ') || row.object_name
        confirmAlert(
          Options('Delete Firmware', `Delete "${name}"? Its file and associated upgrade rules will be removed.`, [
            { label: 'Yes', onClick: () => deleteFirmware(row) },
            { label: 'No', onClick: () => {} },
          ])
        )
      },
    },
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
                resetPagination()
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
    <div className="scroll-div-tab">
      <AdminTable
        actions={tableActions}
        columns={columns}
        defaultPageSize={DEFAULT_PAGE_SIZE}
        handleQueryChange={handleQueryChange}
        isLoading={isRefreshing}
        search={false}
        selection={false}
        tableRef={tableRef}
        title=""
      />

      {selectedObject && (
        <Paper variant="outlined" sx={{ mt: 2, p: 2, overflowWrap: 'anywhere' }}>
          <Typography variant="h6">File details</Typography>
          <Typography>{selectedObject.object_name}</Typography>
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
    </div>
  )
}

export default AdminFirmwareTab

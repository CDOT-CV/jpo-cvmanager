import { useCallback, useRef, useState } from 'react'
import { Action, Column, Query } from '@material-table/core'
import { Chip, FormControl, InputLabel, MenuItem, Paper, Select, Typography } from '@mui/material'
import toast from 'react-hot-toast'
import AdminTable from '../../components/AdminTable'
import { FirmwareObject } from '../../models/Firmware'
import { useGetFirmwareUploadOptionsQuery, useLazyListFirmwareObjectsQuery } from '../api/firmwareApiSlice'
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
  const tableRef = useRef<any>(null)
  const pageTokens = useRef<Map<number, string | undefined>>(new Map([[0, undefined]]))
  const currentPageSize = useRef(DEFAULT_PAGE_SIZE)
  const manufacturerRef = useRef('')
  const [manufacturer, setManufacturer] = useState('')
  const [selectedObject, setSelectedObject] = useState<FirmwareObject>()
  const [showUpload, setShowUpload] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)

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

  const handleQueryChange = useCallback(
    async (query: Query<FirmwareObject>) => {
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

        const normalizedSearch = query.search.trim().toLowerCase()
        const objects = normalizedSearch
          ? result.objects.filter((object) =>
              [object.manufacturer, object.model, object.version, object.object_name].some((value) =>
                value?.toLowerCase().includes(normalizedSearch)
              )
            )
          : result.objects

        return {
          data: objects,
          page: query.page,
          totalCount: result.next_page_token
            ? (query.page + 1) * query.pageSize + 1
            : query.page * query.pageSize + objects.length,
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

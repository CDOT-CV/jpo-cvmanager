import { useCallback, useRef, useState } from 'react'
import { Action, Column, Query } from '@material-table/core'
import { Box, Chip, FormControl, InputLabel, MenuItem, Select, Tooltip, Typography } from '@mui/material'
import toast from 'react-hot-toast'
import AdminTable from '../../components/AdminTable'
import { ObuOtaRequest } from '../../models/ObuFirmware'
import { useGetObuFirmwareUploadOptionsQuery, useLazyListObuOtaRequestsQuery } from '../api/obuFirmwareApiSlice'
import '../adminRsuTab/Admin.css'

const DEFAULT_PAGE_SIZE = 25
const DEFAULT_SORT = 'request_datetime,desc'
const HEADER_STYLE = { textTransform: 'none' as const }
// API sort fields in column order; null marks a column that cannot be sorted
const COLUMN_SORT_FIELDS = [
  'request_datetime',
  'obu_sn',
  null,
  'obu_firmware_version',
  'requested_firmware_version',
  null,
  null,
]
type StatusFilter = '' | 'success' | 'error'

const requestSort = (query: Query<ObuOtaRequest>) => {
  const sort = query.orderByCollection?.[0]
  const orderBy = sort?.orderBy as number | Column<ObuOtaRequest> | undefined
  const field = typeof orderBy === 'number' ? COLUMN_SORT_FIELDS[orderBy] : orderBy?.field
  return typeof field === 'string' ? `${field},${sort.orderDirection || 'asc'}` : DEFAULT_SORT
}

const formatTimestamp = (value: string | null | undefined) => (value ? new Date(value).toLocaleString() : '')

const RequestStatus = ({ request }: { request: ObuOtaRequest }) => {
  const success = request.status === 'success'
  const chip = (
    <Chip
      size="small"
      variant="outlined"
      color={success ? 'success' : 'error'}
      label={success ? 'Success' : 'Error'}
    />
  )
  return success || !request.error_message ? (
    chip
  ) : (
    <Tooltip title={request.error_message}>
      <span>{chip}</span>
    </Tooltip>
  )
}

// Shows the firmware requests OBUs made to the OBU OTA server, newest first
const AdminObuOtaRequestsTab = () => {
  const tableRef = useRef<any>(null)
  const manufacturerRef = useRef('')
  const statusRef = useRef<StatusFilter>('')
  const [manufacturer, setManufacturer] = useState('')
  const [status, setStatus] = useState<StatusFilter>('')
  const [isRefreshing, setIsRefreshing] = useState(false)

  const [listObuOtaRequests] = useLazyListObuOtaRequestsQuery()
  const { data: uploadOptions, isFetching: isFetchingOptions } = useGetObuFirmwareUploadOptionsQuery()

  const refreshListing = useCallback(() => {
    setIsRefreshing(true)
    if (!tableRef.current?.onQueryChange) {
      setIsRefreshing(false)
      return
    }
    void Promise.resolve(tableRef.current.onQueryChange({ page: 0 })).catch(() => {
      /* The query handler displays the fetch error. */
    })
  }, [])

  const handleQueryChange = useCallback(
    async (query: Query<ObuOtaRequest>) => {
      try {
        const result = await listObuOtaRequests({
          page: query.page,
          size: query.pageSize,
          serial: query.search || undefined,
          manufacturer: manufacturerRef.current || undefined,
          status: statusRef.current || undefined,
          sort: requestSort(query),
        }).unwrap()

        return { data: result.items, page: query.page, totalCount: result.total_elements }
      } catch (error) {
        console.error('Failed to fetch OBU update requests:', error)
        toast.error('Failed to fetch OBU update requests')
        throw error
      } finally {
        setIsRefreshing(false)
      }
    },
    [listObuOtaRequests]
  )

  const columns: Column<ObuOtaRequest>[] = [
    {
      title: 'Time',
      field: 'request_datetime',
      headerStyle: HEADER_STYLE,
      render: (request) => formatTimestamp(request.request_datetime),
    },
    { title: 'Serial Number', field: 'obu_sn', headerStyle: HEADER_STYLE },
    { title: 'Manufacturer', field: 'manufacturer', sorting: false, headerStyle: HEADER_STYLE },
    { title: 'Current Version', field: 'obu_firmware_version', headerStyle: HEADER_STYLE },
    {
      title: 'Requested Firmware',
      field: 'requested_firmware_version',
      headerStyle: HEADER_STYLE,
      render: (request) => <span style={{ overflowWrap: 'anywhere' }}>{request.requested_firmware_version}</span>,
    },
    { title: 'Origin IP', field: 'origin_ip', sorting: false, headerStyle: HEADER_STYLE },
    {
      title: 'Status',
      field: 'status',
      sorting: false,
      headerStyle: HEADER_STYLE,
      render: (request) => <RequestStatus request={request} />,
    },
  ]

  const tableActions: Action<ObuOtaRequest>[] = [
    {
      position: 'toolbar',
      icon: () => null,
      tooltip: 'Filter by manufacturer',
      iconProps: {
        itemType: 'custom',
        render: () => (
          <FormControl size="small" sx={{ minWidth: 210, mx: 0.5 }} disabled={isFetchingOptions}>
            <InputLabel id="obu-requests-filter-manufacturer-label">Manufacturer</InputLabel>
            <Select
              labelId="obu-requests-filter-manufacturer-label"
              label="Manufacturer"
              value={manufacturer}
              onChange={(event) => {
                manufacturerRef.current = event.target.value
                setManufacturer(event.target.value)
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
      tooltip: 'Filter by status',
      iconProps: {
        itemType: 'custom',
        render: () => (
          <FormControl size="small" sx={{ minWidth: 150, mx: 0.5 }}>
            <InputLabel id="obu-requests-filter-status-label">Status</InputLabel>
            <Select
              labelId="obu-requests-filter-status-label"
              label="Status"
              value={status}
              onChange={(event) => {
                const value = event.target.value as StatusFilter
                statusRef.current = value
                setStatus(value)
                tableRef.current?.onQueryChange({ page: 0 })
              }}
            >
              <MenuItem value="">All requests</MenuItem>
              <MenuItem value="success">Success</MenuItem>
              <MenuItem value="error">Error</MenuItem>
            </Select>
          </FormControl>
        ),
      } as any,
      onClick: () => undefined,
    },
    {
      position: 'toolbar',
      icon: () => null,
      tooltip: 'Refresh OBU update requests',
      iconProps: { itemType: 'outlined', color: 'info', title: 'Refresh' },
      disabled: isRefreshing,
      onClick: refreshListing,
    },
  ]

  return (
    <Box className="scroll-div-tab">
      <Typography color="text.secondary" sx={{ mb: 1 }}>
        Firmware requests OBUs made to the OBU OTA server. Search by serial number. The OTA server keeps only the most
        recent successful requests of each OBU.
      </Typography>
      <AdminTable
        actions={tableActions}
        columns={columns}
        defaultPageSize={DEFAULT_PAGE_SIZE}
        handleQueryChange={handleQueryChange}
        isLoading={isRefreshing}
        selection={false}
        tableRef={tableRef}
        thirdSortClick={false}
        title=""
      />
    </Box>
  )
}

export default AdminObuOtaRequestsTab

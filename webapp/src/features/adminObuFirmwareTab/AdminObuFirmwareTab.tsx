import { useCallback, useRef, useState } from 'react'
import { Action, Column, Query } from '@material-table/core'
import { Alert, Box, FormControl, InputLabel, MenuItem, Select, Switch, Tooltip, Typography } from '@mui/material'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import { confirmAlert } from 'react-confirm-alert'
import toast from 'react-hot-toast'
import { Options } from '../../components/AdminDeletionOptions'
import AdminTable from '../../components/AdminTable'
import { ObuFirmwareObject, ObuFirmwarePublication, ObuFirmwareVerificationStatus } from '../../models/ObuFirmware'
import {
  useDeleteObuFirmwareObjectMutation,
  useGetObuFirmwareUploadOptionsQuery,
  useLazyListObuFirmwareObjectsQuery,
  useSetObuFirmwarePublishedMutation,
} from '../api/obuFirmwareApiSlice'
import { formatFileSize, getErrorMessage } from '../adminFirmwareTab/firmwareUpload'
import '../adminRsuTab/Admin.css'
import ObuFirmwareUploadForm from './ObuFirmwareUploadForm'

const DEFAULT_PAGE_SIZE = 25
const HEADER_STYLE = { textTransform: 'none' as const }
// API sort fields in column order; null marks a column that cannot be sorted
const COLUMN_SORT_FIELDS = [
  'manufacturer',
  'file_name',
  'device_type',
  'release_version',
  null,
  'content_length',
  'registered_at',
  'verification_status',
  'published',
]
type PublishedFilter = '' | 'true' | 'false'

const obuFirmwareSort = (query: Query<ObuFirmwareObject>) => {
  const sort = query.orderByCollection?.[0]
  const orderBy = sort?.orderBy as number | Column<ObuFirmwareObject> | undefined
  const field = typeof orderBy === 'number' ? COLUMN_SORT_FIELDS[orderBy] : orderBy?.field
  return typeof field === 'string' ? `${field},${sort.orderDirection || 'asc'}` : 'manufacturer,asc'
}

const formatTimestamp = (value: string | null | undefined) => (value ? new Date(value).toLocaleString() : '')

const verificationLabel: Record<ObuFirmwareVerificationStatus, string> = {
  VERIFIED: 'Verified',
  CHANGED: 'File changed',
  MISSING: 'Missing file',
  UNVERIFIED: 'Unverified',
  UNTRACKED: 'Untracked',
  LOCAL: 'Local file',
  UNKNOWN: 'Unknown',
}

const verificationColor: Record<ObuFirmwareVerificationStatus, string> = {
  VERIFIED: 'success.light',
  CHANGED: 'error.light',
  MISSING: 'warning.light',
  UNVERIFIED: 'warning.light',
  UNTRACKED: 'text.primary',
  LOCAL: 'info.light',
  UNKNOWN: 'text.secondary',
}

const describeDetails = (object: ObuFirmwareObject) =>
  [
    object.variant,
    object.writable_system_partition == null ? null : object.writable_system_partition ? 'Read-write' : 'Read-only',
    object.secure_boot ? 'Secure boot' : null,
  ]
    .filter(Boolean)
    .join(' · ')

// Only verified cloud files and local files can be published. Published firmware can
// always be withdrawn, even after its file changed or went missing.
const canChangePublication = (object: ObuFirmwareObject) =>
  object.obu_firmware_id != null &&
  (object.published || object.verification_status === 'VERIFIED' || object.verification_status === 'LOCAL')

const publicationTooltip = (object: ObuFirmwareObject) => {
  if (object.published) {
    const by = object.published_by ? ` by ${object.published_by}` : ''
    const at = object.published_at ? ` on ${formatTimestamp(object.published_at)}` : ''
    return `Published${by}${at}`
  }
  return canChangePublication(object) ? 'Not published' : 'Only verified or local firmware can be published'
}

// Missing and local files are removed from the catalog without deleting a stored file
const isRecordsCleanup = (object: ObuFirmwareObject) =>
  object.verification_status === 'MISSING' || object.verification_status === 'LOCAL'

const deletionTooltip = (object: ObuFirmwareObject) => {
  if (object.published) return 'Unpublish before deleting'
  if (isRecordsCleanup(object)) return 'Clean Up Records'
  if (!object.provider_object_version) return 'Refresh the list once storage is available to delete this file'
  return 'Delete Firmware'
}

const deletionMessage = (object: ObuFirmwareObject) => {
  if (object.verification_status === 'LOCAL') {
    return "Remove this catalog entry? The file stays in the OBU OTA server's local firmware folder and must be removed there."
  }
  if (object.verification_status === 'MISSING') {
    return 'The file is missing from storage. Remove its upload and catalog records?'
  }
  return 'Delete this file and its upload and catalog records?'
}

const ObuFirmwareDeleteIcon = () => <DeleteOutline sx={{ color: (theme) => theme.palette.custom.rowActionIcon }} />

const ObuFirmwareDetails = ({ object }: { object: ObuFirmwareObject }) => (
  <div style={{ marginTop: 16, whiteSpace: 'pre-line', overflowWrap: 'anywhere' }}>
    {`Manufacturer: ${object.manufacturer ?? 'Unknown'}\nFile: ${object.file_name}\nRelease: ${object.release_version ?? 'Unknown'}`}
  </div>
)

const AdminObuFirmwareTab = () => {
  const tableRef = useRef<any>(null)
  const manufacturerRef = useRef('')
  const publishedRef = useRef<PublishedFilter>('')
  const [manufacturer, setManufacturer] = useState('')
  const [published, setPublished] = useState<PublishedFilter>('')
  const [storageAvailable, setStorageAvailable] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [isChanging, setIsChanging] = useState(false)
  const [showUpload, setShowUpload] = useState(false)
  const changing = useRef(false)

  const [listObuFirmwareObjects] = useLazyListObuFirmwareObjectsQuery()
  const [setObuFirmwarePublished] = useSetObuFirmwarePublishedMutation()
  const [deleteObuFirmwareObject] = useDeleteObuFirmwareObjectMutation()
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

  // Publish and delete requests run one at a time so a second click cannot race the first
  const runChange = async <T,>(
    pending: string,
    success: string | ((result: T) => string),
    failure: string,
    change: () => Promise<T>
  ) => {
    if (changing.current) return
    changing.current = true
    setIsChanging(true)
    const notification = toast.loading(pending)
    try {
      const result = await change()
      toast.success(typeof success === 'function' ? success(result) : success, { id: notification })
      refreshListing()
    } catch (error) {
      const message = getErrorMessage(error)
      toast.error(message.startsWith('An unexpected error') ? failure : message, { id: notification })
    } finally {
      changing.current = false
      setIsChanging(false)
    }
  }

  const publishedMessage = (publication: ObuFirmwarePublication) =>
    publication.replaced.length === 0
      ? 'Firmware published'
      : `Firmware published. It replaces ${publication.replaced.map((firmware) => firmware.file_name).join(', ')}.`

  const changePublication = (object: ObuFirmwareObject) => {
    const publish = !object.published
    confirmAlert({
      ...Options(
        publish ? 'Publish OBU Firmware' : 'Unpublish OBU Firmware',
        publish
          ? 'Publish this firmware? It replaces the currently published release of the same build. OBUs install the newest published firmware that matches their build.'
          : 'Stop offering this firmware to OBUs? OBUs that already downloaded it are not affected.',
        [
          {
            label: 'Yes',
            onClick: () =>
              void runChange(
                publish ? 'Publishing firmware...' : 'Unpublishing firmware...',
                publish ? publishedMessage : 'Firmware unpublished',
                'Failed to update the firmware. Please retry.',
                () => setObuFirmwarePublished({ obuFirmwareId: object.obu_firmware_id!, published: publish }).unwrap()
              ),
          },
          { label: 'No', onClick: () => {} },
        ]
      ),
      childrenElement: ObuFirmwareDetails.bind(null, { object }),
    })
  }

  const deleteFirmware = (object: ObuFirmwareObject) => {
    const cleanup = isRecordsCleanup(object)
    void runChange(
      cleanup ? 'Cleaning up firmware records...' : 'Deleting firmware...',
      cleanup ? 'Firmware records cleaned up successfully' : 'Firmware deleted successfully',
      'Failed to delete firmware. Please retry.',
      () =>
        deleteObuFirmwareObject({
          object_id: object.object_id,
          provider_object_version: cleanup ? null : object.provider_object_version,
        }).unwrap()
    )
  }

  const handleQueryChange = useCallback(
    async (query: Query<ObuFirmwareObject>) => {
      try {
        const result = await listObuFirmwareObjects({
          page: query.page,
          size: query.pageSize,
          search: query.search || '',
          manufacturer: manufacturerRef.current || undefined,
          published: publishedRef.current === '' ? undefined : publishedRef.current === 'true',
          sort: obuFirmwareSort(query),
        }).unwrap()
        setStorageAvailable(result.storage_available)

        return { data: result.objects, page: query.page, totalCount: result.total_elements }
      } catch (error) {
        console.error('Failed to fetch OBU firmware:', error)
        toast.error('Failed to fetch OBU firmware')
        throw error
      } finally {
        setIsRefreshing(false)
      }
    },
    [listObuFirmwareObjects]
  )

  const columns: Column<ObuFirmwareObject>[] = [
    { title: 'Manufacturer', field: 'manufacturer', headerStyle: HEADER_STYLE },
    {
      title: 'File Name',
      field: 'file_name',
      headerStyle: HEADER_STYLE,
      render: (object) => <span style={{ overflowWrap: 'anywhere' }}>{object.file_name}</span>,
    },
    { title: 'Type', field: 'device_type', headerStyle: HEADER_STYLE },
    { title: 'Release', field: 'release_version', headerStyle: HEADER_STYLE },
    { title: 'Details', sorting: false, headerStyle: HEADER_STYLE, render: describeDetails },
    {
      title: 'Size',
      field: 'content_length',
      headerStyle: HEADER_STYLE,
      render: (object) => (object.content_length == null ? '' : formatFileSize(object.content_length)),
    },
    {
      title: 'Registered',
      field: 'registered_at',
      headerStyle: HEADER_STYLE,
      render: (object) => formatTimestamp(object.registered_at),
    },
    {
      title: 'Verification',
      field: 'verification_status',
      headerStyle: HEADER_STYLE,
      render: (object) => (
        <Typography variant="body2" sx={{ color: verificationColor[object.verification_status], fontWeight: 'bold' }}>
          {verificationLabel[object.verification_status]}
        </Typography>
      ),
    },
    {
      title: 'Published',
      field: 'published',
      headerStyle: HEADER_STYLE,
      render: (object) => (
        <Tooltip title={publicationTooltip(object)}>
          <span>
            <Switch
              size="small"
              checked={object.published}
              disabled={isChanging || !canChangePublication(object)}
              onChange={() => changePublication(object)}
              inputProps={{ 'aria-label': `${object.published ? 'Unpublish' : 'Publish'} ${object.file_name}` }}
            />
          </span>
        </Tooltip>
      ),
    },
  ]

  const tableActions: (Action<ObuFirmwareObject> | ((row: ObuFirmwareObject) => Action<ObuFirmwareObject>))[] = [
    (object) => ({
      position: 'row',
      icon: ObuFirmwareDeleteIcon,
      iconProps: { itemType: 'rowAction' },
      tooltip: deletionTooltip(object),
      disabled: isChanging || object.published || (!isRecordsCleanup(object) && !object.provider_object_version),
      onClick: (_event, row: ObuFirmwareObject) => {
        confirmAlert({
          ...Options(isRecordsCleanup(row) ? 'Clean Up OBU Firmware Records' : 'Delete OBU Firmware', deletionMessage(row), [
            { label: 'Yes', onClick: () => deleteFirmware(row) },
            { label: 'No', onClick: () => {} },
          ]),
          childrenElement: ObuFirmwareDetails.bind(null, { object: row }),
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
            <InputLabel id="obu-firmware-filter-manufacturer-label">Manufacturer</InputLabel>
            <Select
              labelId="obu-firmware-filter-manufacturer-label"
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
      tooltip: 'Filter by publish state',
      iconProps: {
        itemType: 'custom',
        render: () => (
          <FormControl size="small" sx={{ minWidth: 170, mx: 0.5 }}>
            <InputLabel id="obu-firmware-filter-published-label">Published</InputLabel>
            <Select
              labelId="obu-firmware-filter-published-label"
              label="Published"
              value={published}
              onChange={(event) => {
                const value = event.target.value as PublishedFilter
                publishedRef.current = value
                setPublished(value)
                tableRef.current?.onQueryChange({ page: 0 })
              }}
            >
              <MenuItem value="">All firmware</MenuItem>
              <MenuItem value="true">Published</MenuItem>
              <MenuItem value="false">Not published</MenuItem>
            </Select>
          </FormControl>
        ),
      } as any,
      onClick: () => undefined,
    },
    {
      position: 'toolbar',
      icon: () => null,
      tooltip: 'Refresh OBU firmware',
      iconProps: { itemType: 'outlined', color: 'info', title: 'Refresh' },
      disabled: isRefreshing,
      onClick: refreshListing,
    },
    {
      position: 'toolbar',
      icon: () => null,
      tooltip: 'Add OBU firmware',
      iconProps: { itemType: 'contained', title: 'New' },
      onClick: () => setShowUpload(true),
    },
  ]

  return (
    <Box
      className="scroll-div-tab"
      sx={{
        '& thead th:last-child, & tbody td:last-child:not([colspan])': {
          width: '96px !important',
        },
        '& tbody td:last-child:not([colspan]) > div': {
          justifyContent: 'center',
        },
      }}
    >
      {/* Remove once the OBU OTA server serves only published catalog entries */}
      <Alert severity="info" variant="outlined" sx={{ mb: 2 }}>
        Publish state is recorded here but is not yet applied by the OBU OTA server. It takes effect once the OTA
        server update that serves only published firmware is deployed.
      </Alert>
      {!storageAvailable && (
        <Alert severity="warning" variant="outlined" sx={{ mb: 2 }}>
          Object storage could not be listed, so only catalog entries are shown. Cloud files cannot be checked or
          deleted until storage is available.
        </Alert>
      )}
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

      {showUpload && (
        <ObuFirmwareUploadForm
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

export default AdminObuFirmwareTab

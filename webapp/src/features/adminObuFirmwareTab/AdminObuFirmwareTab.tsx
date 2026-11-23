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

const DEFAULT_PAGE_SIZE = 25
const HEADER_STYLE = { textTransform: 'none' as const }
type PublishedFilter = '' | 'true' | 'false'

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

const describeFileSystem = (writable: boolean | null | undefined) => {
  if (writable == null) {
    return null
  }
  return writable ? 'Read-write' : 'Read-only'
}

const describeDetails = (object: ObuFirmwareObject) =>
  [object.variant, describeFileSystem(object.writable_system_partition), object.secure_boot ? 'Secure boot' : null]
    .filter(Boolean)
    .join(' · ')

// Only verified cloud files and local files in a build the API accepts can be published,
// so RSU firmware and older entries of an unsupported build cannot. Published firmware
// can always be withdrawn, even after its file changed or went missing.
const canChangePublication = (object: ObuFirmwareObject) =>
  object.obu_firmware_id != null &&
  (object.published ||
    (!object.unsupported_reason &&
      (object.verification_status === 'VERIFIED' || object.verification_status === 'LOCAL')))

const publicationTooltip = (object: ObuFirmwareObject) => {
  if (object.published) {
    const by = object.published_by ? ` by ${object.published_by}` : ''
    const at = object.published_at ? ` on ${formatTimestamp(object.published_at)}` : ''
    return `Published${by}${at}`
  }
  if (canChangePublication(object)) return 'Not published'
  if (object.unsupported_reason) return object.unsupported_reason
  return 'Only verified or local firmware can be published'
}

// Missing and local files are removed from the catalog without deleting a stored file.
// While storage is available, UNKNOWN entries are from a previously configured bucket
// and are removed the same way; without storage nothing can be checked.
const isRecordsCleanup = (object: ObuFirmwareObject, storageAvailable: boolean) =>
  object.verification_status === 'MISSING' ||
  object.verification_status === 'LOCAL' ||
  (object.verification_status === 'UNKNOWN' && storageAvailable)

const deletionTooltip = (object: ObuFirmwareObject, storageAvailable: boolean) => {
  if (object.published) return 'Unpublish before deleting'
  if (isRecordsCleanup(object, storageAvailable)) return 'Clean Up Records'
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
  if (object.verification_status === 'UNKNOWN') {
    return 'This entry is from a previously configured storage bucket. Remove its upload and catalog records? The file stays in that bucket and must be deleted there.'
  }
  return 'Delete this file and its upload and catalog records?'
}

// Table columns in display order. A sortable column's field is also its API sort
// field, and column header clicks report the column's index in this list. The
// Published column is rendered by the tab, since its switch depends on tab state.
const COLUMN_LAYOUT: Column<ObuFirmwareObject>[] = [
  { title: 'Manufacturer', field: 'manufacturer' },
  {
    title: 'File Name',
    field: 'file_name',
    render: (object) => <span style={{ overflowWrap: 'anywhere' }}>{object.file_name}</span>,
  },
  { title: 'Type', field: 'device_type' },
  { title: 'Release', field: 'release_version' },
  { title: 'Details', sorting: false, render: describeDetails },
  {
    title: 'Size',
    field: 'content_length',
    render: (object) => (object.content_length == null ? '' : formatFileSize(object.content_length)),
  },
  { title: 'Registered', field: 'registered_at', render: (object) => formatTimestamp(object.registered_at) },
  {
    title: 'Verification',
    field: 'verification_status',
    render: (object) => (
      <Typography variant="body2" sx={{ color: verificationColor[object.verification_status], fontWeight: 'bold' }}>
        {verificationLabel[object.verification_status]}
      </Typography>
    ),
  },
  { title: 'Published', field: 'published' },
]

const obuFirmwareSort = (query: Query<ObuFirmwareObject>) => {
  const sort = query.orderByCollection?.[0]
  const orderBy = sort?.orderBy as number | Column<ObuFirmwareObject> | undefined
  const column = typeof orderBy === 'number' ? COLUMN_LAYOUT[orderBy] : orderBy
  const field = column?.sorting === false ? undefined : column?.field
  return typeof field === 'string' ? `${field},${sort.orderDirection || 'asc'}` : 'manufacturer,asc'
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
    const cleanup = isRecordsCleanup(object, storageAvailable)
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

  const columns: Column<ObuFirmwareObject>[] = COLUMN_LAYOUT.map((column) => ({
    ...column,
    headerStyle: HEADER_STYLE,
    render:
      column.field === 'published'
        ? (object: ObuFirmwareObject) => {
            const changeable = !isChanging && canChangePublication(object)
            return (
              <Tooltip title={publicationTooltip(object)}>
                {/* A disabled switch cannot take focus, so the wrapper does, to reveal why */}
                <span tabIndex={changeable ? undefined : 0}>
                  <Switch
                    size="small"
                    checked={object.published}
                    disabled={!changeable}
                    onChange={() => changePublication(object)}
                    inputProps={{ 'aria-label': `${object.published ? 'Unpublish' : 'Publish'} ${object.file_name}` }}
                  />
                </span>
              </Tooltip>
            )
          }
        : column.render,
  }))

  const tableActions: (Action<ObuFirmwareObject> | ((row: ObuFirmwareObject) => Action<ObuFirmwareObject>))[] = [
    (object) => ({
      position: 'row',
      icon: ObuFirmwareDeleteIcon,
      iconProps: { itemType: 'rowAction' },
      tooltip: deletionTooltip(object, storageAvailable),
      disabled:
        isChanging ||
        object.published ||
        (!isRecordsCleanup(object, storageAvailable) && !object.provider_object_version),
      onClick: (_event, row: ObuFirmwareObject) => {
        confirmAlert({
          ...Options(isRecordsCleanup(row, storageAvailable) ? 'Clean Up OBU Firmware Records' : 'Delete OBU Firmware', deletionMessage(row), [
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
    </Box>
  )
}

export default AdminObuFirmwareTab

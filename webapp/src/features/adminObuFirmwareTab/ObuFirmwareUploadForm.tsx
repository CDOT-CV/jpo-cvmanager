import { ChangeEvent, FormEvent, useRef, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  FormControl,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Typography,
} from '@mui/material'
import toast from 'react-hot-toast'
import { Upload } from '../../icons/upload'
import { ObuFirmwareFileNameCheck, ObuFirmwareManufacturerOption } from '../../models/ObuFirmware'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import {
  useCompleteObuFirmwareUploadMutation,
  useCreateObuFirmwareUploadUrlMutation,
  useGetObuFirmwareUploadOptionsQuery,
  useLazyCheckObuFirmwareFileNameQuery,
} from '../api/obuFirmwareApiSlice'
import { formatFileSize, getErrorMessage } from '../adminFirmwareTab/firmwareUpload'
import FirmwareUploadProgress from '../adminFirmwareTab/FirmwareUploadProgress'
import { runSignedUpload, UploadStage } from '../adminFirmwareTab/signedUpload'

export const OBU_FIRMWARE_CONFLICT_MESSAGE =
  'A file with this name is already in storage. Delete it from the OBU firmware list, then upload it again.'

const ERROR_ALERT_SX = { color: 'error.light', borderColor: 'error.light', '& .MuiAlert-icon': { color: 'error.light' } }

type NameCheck =
  | { state: 'none' }
  | { state: 'checking' }
  | { state: 'accepted'; details: ObuFirmwareFileNameCheck }
  | { state: 'rejected'; message: string }

// The upload options pattern gives quick feedback; the API decides whether a name is accepted
export const localNameError = (manufacturer: ObuFirmwareManufacturerOption, fileName: string) => {
  if (fileName.length > manufacturer.max_file_name_length) {
    return `File names can be at most ${manufacturer.max_file_name_length} characters.`
  }
  let pattern: RegExp
  try {
    pattern = new RegExp(manufacturer.file_name_pattern)
  } catch {
    return null
  }
  return pattern.test(fileName)
    ? null
    : `${fileName} does not follow the ${manufacturer.name} OBU firmware naming convention. Upload the file with its original name from the manufacturer, for example ${manufacturer.example_file_name}.`
}

const describeFileSystem = (details: ObuFirmwareFileNameCheck) =>
  details.writable_system_partition ? 'Read-write' : 'Read-only'

const FileNameDetails = ({ details }: { details: ObuFirmwareFileNameCheck }) => (
  <Box
    component="dl"
    aria-label="Firmware details"
    sx={{ display: 'grid', gridTemplateColumns: 'max-content 1fr', columnGap: 2, rowGap: 0.5, m: 0 }}
  >
    {[
      ['Device type', details.device_type],
      ['Variant', details.variant],
      ['Release', details.release_version],
      ['File system', describeFileSystem(details)],
      ['Secure boot', details.secure_boot ? 'Yes' : 'No'],
    ].map(([label, value]) => (
      <Box key={label} sx={{ display: 'contents' }}>
        <Typography component="dt" color="text.secondary">
          {label}
        </Typography>
        <Typography component="dd" sx={{ m: 0, overflowWrap: 'anywhere' }}>
          {value}
        </Typography>
      </Box>
    ))}
  </Box>
)

type ObuFirmwareUploadFormProps = {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}

const ObuFirmwareUploadForm = ({ open, onClose, onSuccess }: ObuFirmwareUploadFormProps) => {
  const [selectedName, setSelectedName] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [nameCheck, setNameCheck] = useState<NameCheck>({ state: 'none' })
  const [stage, setStage] = useState<UploadStage>('idle')
  const [progress, setProgress] = useState(0)
  const [errorMessage, setErrorMessage] = useState('')
  // Only the latest file's check may update the form
  const latestCheck = useRef(0)

  const [checkFileName] = useLazyCheckObuFirmwareFileNameQuery()
  const [createUploadUrl] = useCreateObuFirmwareUploadUrlMutation()
  const [completeUpload] = useCompleteObuFirmwareUploadMutation()
  const {
    data: uploadOptions,
    isLoading: isLoadingOptions,
    isError: isOptionsError,
  } = useGetObuFirmwareUploadOptionsQuery(undefined, { skip: !open })
  const manufacturers = uploadOptions?.manufacturers ?? []
  // With a single supported manufacturer there is nothing to choose
  const manufacturerName = selectedName || (manufacturers.length === 1 ? manufacturers[0].name : '')
  const manufacturer = manufacturers.find((option) => option.name === manufacturerName)

  const isWorking = !['idle', 'complete'].includes(stage)
  const closeDialog = () => {
    if (!isWorking) onClose()
  }

  const resetFile = () => {
    latestCheck.current += 1
    setFile(null)
    setNameCheck({ state: 'none' })
    setErrorMessage('')
    setProgress(0)
    setStage('idle')
  }

  const selectFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0] ?? null
    resetFile()
    if (!selectedFile || !manufacturer) return
    setFile(selectedFile)

    const localError = localNameError(manufacturer, selectedFile.name)
    if (localError) {
      setNameCheck({ state: 'rejected', message: localError })
      return
    }

    const check = latestCheck.current
    setNameCheck({ state: 'checking' })
    try {
      const details = await checkFileName({
        manufacturer_name: manufacturer.name,
        file_name: selectedFile.name,
      }).unwrap()
      if (check === latestCheck.current) setNameCheck({ state: 'accepted', details })
    } catch (error) {
      if (check === latestCheck.current) setNameCheck({ state: 'rejected', message: getErrorMessage(error) })
    }
  }

  const submitUpload = async (event: FormEvent) => {
    event.preventDefault()
    if (!file || !manufacturer || nameCheck.state !== 'accepted') return
    setErrorMessage('')
    setProgress(0)

    try {
      await runSignedUpload({
        file,
        requestUploadUrl: (upload) => createUploadUrl({ manufacturer_name: manufacturer.name, ...upload }).unwrap(),
        completeUpload: (uploadId) => completeUpload(uploadId).unwrap(),
        onStage: setStage,
        onProgress: setProgress,
        conflictMessage: OBU_FIRMWARE_CONFLICT_MESSAGE,
      })
      toast.success('OBU firmware uploaded and verified. Publish it to offer it to OBUs.')
      onSuccess()
    } catch (error) {
      setStage('idle')
      setErrorMessage(getErrorMessage(error))
    }
  }

  return (
    <Dialog open={open} onClose={closeDialog}>
      <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
        <SideBarHeader onClick={closeDialog} title="Add OBU Firmware" />
        <Box id="add-obu-firmware-form" component="form" onSubmit={submitUpload}>
          <Stack spacing={2.5}>
            <Typography color="text.secondary">
              Upload OBU firmware with its original file name from the manufacturer. OBUs choose firmware by the build
              and release in the name. Uploaded firmware is offered to OBUs only after it is published.
            </Typography>
            <FormControl required disabled={isWorking || isLoadingOptions || isOptionsError || !manufacturers.length}>
              <InputLabel id="obu-firmware-manufacturer-label">Manufacturer</InputLabel>
              <Select
                required
                labelId="obu-firmware-manufacturer-label"
                label="Manufacturer"
                value={manufacturerName}
                onChange={(event) => {
                  setSelectedName(event.target.value)
                  resetFile()
                }}
              >
                {manufacturers.map((option) => (
                  <MenuItem key={option.manufacturer_id} value={option.name}>
                    {option.name}
                  </MenuItem>
                ))}
              </Select>
              {isLoadingOptions && <FormHelperText>Loading manufacturers...</FormHelperText>}
              {!isLoadingOptions && !isOptionsError && !manufacturers.length && (
                <FormHelperText>No manufacturers accept OBU firmware uploads.</FormHelperText>
              )}
            </FormControl>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
              <Button
                component="label"
                variant="outlined"
                color="info"
                startIcon={<Upload />}
                sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}
                disabled={isWorking || !manufacturer}
              >
                <span>Choose File</span>
                <input
                  key={manufacturerName}
                  hidden
                  type="file"
                  accept={manufacturer?.file_extension}
                  disabled={isWorking || !manufacturer}
                  onChange={(event) => void selectFile(event)}
                />
              </Button>
              {file ? (
                <Stack direction="row" spacing={0.5} sx={{ minWidth: 0, flex: 1 }}>
                  <Typography noWrap title={file.name} sx={{ minWidth: 0 }}>
                    {file.name}
                  </Typography>
                  <Typography sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}>
                    ({formatFileSize(file.size)})
                  </Typography>
                </Stack>
              ) : (
                <Typography color="text.secondary">No file selected</Typography>
              )}
            </Stack>
            {manufacturer && (
              <FormHelperText sx={{ overflowWrap: 'anywhere' }}>
                {`${manufacturer.file_extension} file, for example ${manufacturer.example_file_name}`}
              </FormHelperText>
            )}
            {nameCheck.state === 'checking' && <Typography color="text.secondary">Checking file name...</Typography>}
            {nameCheck.state === 'accepted' && <FileNameDetails details={nameCheck.details} />}
            {nameCheck.state === 'rejected' && (
              <Alert severity="error" variant="outlined" sx={ERROR_ALERT_SX}>
                {nameCheck.message}
              </Alert>
            )}
            {isOptionsError && (
              <Alert severity="error" variant="outlined" sx={ERROR_ALERT_SX}>
                Unable to load OBU firmware manufacturers. Close and reopen this form to try again.
              </Alert>
            )}

            <FirmwareUploadProgress stage={stage} progress={progress} />

            {errorMessage && (
              <Alert severity="error" variant="outlined" sx={ERROR_ALERT_SX}>
                {errorMessage}
              </Alert>
            )}
          </Stack>
        </Box>
      </DialogContent>
      <DialogActions sx={{ padding: '20px', mt: 1 }}>
        <Button
          variant="outlined"
          color="info"
          className="museo-slab capital-case"
          disabled={isWorking}
          onClick={onClose}
        >
          Cancel
        </Button>
        <Button
          form="add-obu-firmware-form"
          type="submit"
          variant="contained"
          className="museo-slab capital-case"
          disabled={isWorking || nameCheck.state !== 'accepted'}
        >
          {isWorking ? 'Uploading...' : 'Add Firmware'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default ObuFirmwareUploadForm

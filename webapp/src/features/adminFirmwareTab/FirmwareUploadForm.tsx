import { ChangeEvent, FormEvent, useState } from 'react'
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  FormControl,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import toast from 'react-hot-toast'
import { Upload } from '../../icons/upload'
import { ErrorMessageText } from '../../styles/components/Messages'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import { ChecksumAlgorithm } from '../../models/Firmware'
import {
  useCompleteFirmwareUploadMutation,
  useCreateFirmwareUploadUrlMutation,
  useGetFirmwareUploadOptionsQuery,
} from '../api/firmwareApiSlice'
import { calculateFileChecksum, uploadFileToSignedUrl } from './firmwareUpload'

const DEFAULT_CHECKSUM_ALGORITHM: ChecksumAlgorithm = 'CRC32C'
const SAFE_FILE_COMPONENT = /^[A-Za-z0-9][A-Za-z0-9._-]*$/

type UploadStage = 'idle' | 'checksum' | 'requesting-url' | 'uploading' | 'verifying' | 'complete'

type FirmwareUploadFormProps = {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}

const stageLabel: Record<UploadStage, string> = {
  idle: 'Ready to upload',
  checksum: 'Calculating checksum…',
  'requesting-url': 'Requesting upload URL…',
  uploading: 'Uploading firmware…',
  verifying: 'Verifying uploaded firmware…',
  complete: 'Upload complete',
}

const getErrorMessage = (error: unknown) => {
  if (error instanceof Error) return error.message
  if (typeof error === 'object' && error !== null && 'data' in error) {
    const data = (error as { data?: unknown }).data
    if (typeof data === 'string' && data) return data
    if (typeof data === 'object' && data !== null) {
      const response = data as {
        message?: string
        detail?: string
        error?: string
      }
      return response.message || response.detail || response.error || 'The firmware API rejected the request.'
    }
  }
  return 'An unexpected error occurred while uploading the firmware.'
}

const FirmwareUploadForm = ({ open, onClose, onSuccess }: FirmwareUploadFormProps) => {
  const [vendorName, setVendorName] = useState('')
  const [modelName, setModelName] = useState('')
  const [version, setVersion] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [stage, setStage] = useState<UploadStage>('idle')
  const [progress, setProgress] = useState(0)
  const [errorMessage, setErrorMessage] = useState('')
  const [createUploadUrl] = useCreateFirmwareUploadUrlMutation()
  const [completeUpload] = useCompleteFirmwareUploadMutation()
  const {
    data: uploadOptions,
    isLoading: isLoadingOptions,
    isError: isOptionsError,
  } = useGetFirmwareUploadOptionsQuery(undefined, { skip: !open })
  const selectedManufacturer = uploadOptions?.manufacturers.find((option) => option.name === vendorName)
  const hasUploadOptions = Boolean(uploadOptions?.manufacturers.length)

  const isWorking = !['idle', 'complete'].includes(stage)
  const closeDialog = () => {
    if (!isWorking) onClose()
  }

  const selectFile = (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0] ?? null
    setFile(selectedFile)
    setErrorMessage('')
    setProgress(0)
    setStage('idle')
  }

  const validateForm = () => {
    if (!file) return 'Please choose a firmware file.'
    if (!selectedManufacturer) return 'Please select a manufacturer.'
    if (!selectedManufacturer.models.some((model) => model.name === modelName)) return 'Please select a model.'
    if (!SAFE_FILE_COMPONENT.test(version.trim())) {
      return 'Version must start with a letter or number and use only letters, numbers, dots, underscores, or hyphens.'
    }
    if (file.name.length > 128 || !SAFE_FILE_COMPONENT.test(file.name)) {
      return 'File name must start with a letter or number and use only letters, numbers, dots, underscores, or hyphens.'
    }
    return null
  }

  const submitUpload = async (event: FormEvent) => {
    event.preventDefault()
    setErrorMessage('')
    setProgress(0)

    const validationError = validateForm()
    if (validationError || !file) {
      setErrorMessage(validationError ?? 'Please choose a firmware file.')
      return
    }

    try {
      setStage('checksum')
      const checksum = await calculateFileChecksum(file, DEFAULT_CHECKSUM_ALGORITHM)

      setStage('requesting-url')
      const uploadInstructions = await createUploadUrl({
        vendor_name: vendorName.trim(),
        model_name: modelName.trim(),
        version: version.trim(),
        file_name: file.name,
        content_length: file.size,
        content_type: file.type || 'application/octet-stream',
        checksum_algorithm: DEFAULT_CHECKSUM_ALGORITHM,
        checksum,
      }).unwrap()

      setStage('uploading')
      await uploadFileToSignedUrl(file, uploadInstructions, setProgress)

      setStage('verifying')
      const verification = await completeUpload(uploadInstructions.upload_id).unwrap()
      if (verification.status !== 'VERIFIED') {
        throw new Error('The firmware API did not verify the uploaded file.')
      }

      setStage('complete')
      toast.success('Firmware uploaded and verified successfully')
      onSuccess()
    } catch (error) {
      setStage('idle')
      setErrorMessage(getErrorMessage(error))
    }
  }

  return (
    <Dialog open={open} onClose={closeDialog}>
      <DialogContent sx={{ width: '600px', padding: '5px 10px' }}>
        <SideBarHeader onClick={closeDialog} title="Add Firmware" />
        <Box id="add-firmware-form" component="form" onSubmit={submitUpload} noValidate>
          <Stack spacing={2.5}>
            <Typography color="text.secondary">
              Upload a firmware artifact directly to object storage and verify it with the firmware API.
            </Typography>
            <FormControl required disabled={isWorking || isLoadingOptions || isOptionsError || !hasUploadOptions}>
              <InputLabel id="firmware-manufacturer-label">Manufacturer</InputLabel>
              <Select
                labelId="firmware-manufacturer-label"
                label="Manufacturer"
                value={vendorName}
                onChange={(event) => {
                  setVendorName(event.target.value)
                  setModelName('')
                }}
              >
                {uploadOptions?.manufacturers.map((manufacturer) => (
                  <MenuItem key={manufacturer.manufacturer_id} value={manufacturer.name}>
                    {manufacturer.name}
                  </MenuItem>
                ))}
              </Select>
              {isLoadingOptions && <FormHelperText>Loading manufacturers...</FormHelperText>}
              {!isLoadingOptions && !isOptionsError && !hasUploadOptions && (
                <FormHelperText>No RSU manufacturers with models are available.</FormHelperText>
              )}
            </FormControl>
            <FormControl required disabled={isWorking || !selectedManufacturer}>
              <InputLabel id="firmware-model-label">Model</InputLabel>
              <Select
                labelId="firmware-model-label"
                label="Model"
                value={modelName}
                onChange={(event) => setModelName(event.target.value)}
              >
                {selectedManufacturer?.models.map((model) => (
                  <MenuItem key={model.model_id} value={model.name}>
                    {model.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Version"
              value={version}
              onChange={(event) => setVersion(event.target.value)}
              helperText="Letters, numbers, dots, underscores, and hyphens"
              required
              disabled={isWorking}
              inputProps={{ maxLength: 128 }}
            />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
              <Button component="label" variant="outlined" color="info" startIcon={<Upload />} disabled={isWorking}>
                Choose File
                <input hidden type="file" onChange={selectFile} />
              </Button>
              <Typography color={file ? 'text.primary' : 'text.secondary'}>
                {file ? `${file.name} (${file.size.toLocaleString()} bytes)` : 'No file selected'}
              </Typography>
            </Stack>
            {isOptionsError && (
              <ErrorMessageText role="alert">
                Unable to load firmware manufacturers and models. Close and reopen this form to try again.
              </ErrorMessageText>
            )}

            {(isWorking || stage === 'complete') && (
              <Stack direction="row" spacing={1.5} alignItems="center">
                <Box sx={{ position: 'relative', display: 'inline-flex' }}>
                  <CircularProgress
                    variant="determinate"
                    value={stage === 'complete' || stage === 'verifying' ? 100 : progress}
                    aria-label="Firmware upload progress"
                  />
                  <Box
                    sx={{
                      position: 'absolute',
                      inset: 0,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Typography variant="caption" color="text.secondary">{`${
                      stage === 'complete' || stage === 'verifying' ? 100 : progress
                    }%`}</Typography>
                  </Box>
                </Box>
                <Typography>{stageLabel[stage]}</Typography>
              </Stack>
            )}

            {errorMessage && <ErrorMessageText role="alert">{errorMessage}</ErrorMessageText>}
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
          form="add-firmware-form"
          type="submit"
          variant="contained"
          className="museo-slab capital-case"
          disabled={isWorking || isLoadingOptions || isOptionsError || !hasUploadOptions}
        >
          {isWorking ? 'Uploading...' : 'Add Firmware'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default FirmwareUploadForm

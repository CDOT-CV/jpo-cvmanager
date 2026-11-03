import { ChangeEvent, FormEvent, useState } from 'react'
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
  TextField,
  Typography,
} from '@mui/material'
import toast from 'react-hot-toast'
import { Upload } from '../../icons/upload'
import { SideBarHeader } from '../../styles/components/SideBarHeader'
import {
  useCompleteFirmwareUploadMutation,
  useCreateFirmwareUploadUrlMutation,
  useGetFirmwareUploadOptionsQuery,
} from '../api/firmwareApiSlice'
import { formatFileSize, getErrorMessage } from './firmwareUpload'
import FirmwareUploadProgress from './FirmwareUploadProgress'
import { runSignedUpload, UploadStage } from './signedUpload'

const SAFE_FILE_COMPONENT = /^[A-Za-z0-9][A-Za-z0-9._-]*$/

type FirmwareUploadFormProps = {
  open: boolean
  onClose: () => void
  onSuccess: () => void
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
    if (!selectedManufacturer.file_extension) {
      return `Firmware uploads are not configured for ${selectedManufacturer.name}.`
    }
    if (file.name.length > 128 || !SAFE_FILE_COMPONENT.test(file.name)) {
      return 'File name must start with a letter or number and use only letters, numbers, dots, underscores, or hyphens.'
    }
    if (!file.name.toLowerCase().endsWith(selectedManufacturer.file_extension.toLowerCase())) {
      return `The selected file must use the ${selectedManufacturer.file_extension} extension for ${selectedManufacturer.name}.`
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
      await runSignedUpload({
        file,
        requestUploadUrl: (upload) =>
          createUploadUrl({
            vendor_name: vendorName.trim(),
            model_name: modelName.trim(),
            version: version.trim(),
            ...upload,
          }).unwrap(),
        completeUpload: (uploadId) => completeUpload(uploadId).unwrap(),
        onStage: setStage,
        onProgress: setProgress,
      })
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
        <Box id="add-firmware-form" component="form" onSubmit={submitUpload}>
          <Stack spacing={2.5}>
            <Typography color="text.secondary">
              Upload a firmware artifact directly to object storage and verify it with the firmware API.
            </Typography>
            <FormControl required disabled={isWorking || isLoadingOptions || isOptionsError || !hasUploadOptions}>
              <InputLabel id="firmware-manufacturer-label">Manufacturer</InputLabel>
              <Select
                required
                labelId="firmware-manufacturer-label"
                label="Manufacturer"
                value={vendorName}
                onChange={(event) => {
                  setVendorName(event.target.value)
                  setModelName('')
                  setFile(null)
                  setErrorMessage('')
                  setProgress(0)
                  setStage('idle')
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
              {selectedManufacturer && !selectedManufacturer.file_extension && (
                <Alert
                  severity="error"
                  variant="outlined"
                  sx={{
                    mt: 1,
                    color: 'error.light',
                    borderColor: 'error.light',
                    '& .MuiAlert-icon': { color: 'error.light' },
                  }}
                >
                  Firmware uploads are not configured for this manufacturer.
                </Alert>
              )}
            </FormControl>
            <FormControl required disabled={isWorking || !selectedManufacturer}>
              <InputLabel id="firmware-model-label">Model</InputLabel>
              <Select
                required
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
              <Button
                component="label"
                variant="outlined"
                color="info"
                startIcon={<Upload />}
                sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}
                disabled={isWorking || !selectedManufacturer?.file_extension}
              >
                <span>Choose File</span>
                <input
                  key={vendorName}
                  hidden
                  type="file"
                  accept={selectedManufacturer?.file_extension}
                  disabled={isWorking || !selectedManufacturer?.file_extension}
                  onChange={selectFile}
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
            {selectedManufacturer?.file_extension && (
              <FormHelperText>Required file extension: {selectedManufacturer.file_extension}</FormHelperText>
            )}
            {isOptionsError && (
              <Alert
                severity="error"
                variant="outlined"
                sx={{ color: 'error.light', borderColor: 'error.light', '& .MuiAlert-icon': { color: 'error.light' } }}
              >
                Unable to load firmware manufacturers and models. Close and reopen this form to try again.
              </Alert>
            )}

            <FirmwareUploadProgress stage={stage} progress={progress} />

            {errorMessage && (
              <Alert
                severity="error"
                variant="outlined"
                sx={{ color: 'error.light', borderColor: 'error.light', '& .MuiAlert-icon': { color: 'error.light' } }}
              >
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
          form="add-firmware-form"
          type="submit"
          variant="contained"
          className="museo-slab capital-case"
          disabled={
            isWorking ||
            isLoadingOptions ||
            isOptionsError ||
            !hasUploadOptions ||
            Boolean(selectedManufacturer && !selectedManufacturer.file_extension)
          }
        >
          {isWorking ? 'Uploading...' : 'Add Firmware'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default FirmwareUploadForm

import { ChangeEvent, FormEvent, useState } from 'react'
import { Box, Button, CircularProgress, Paper, Stack, TextField, Typography } from '@mui/material'
import { Upload } from '../../icons/upload'
import { ErrorMessageText, SuccessMessageText } from '../../styles/components/Messages'
import { ChecksumAlgorithm } from '../../models/Firmware'
import { useCompleteFirmwareUploadMutation, useCreateFirmwareUploadUrlMutation } from '../api/firmwareApiSlice'
import { calculateFileChecksum, uploadFileToSignedUrl } from './firmwareUpload'

const DEFAULT_CHECKSUM_ALGORITHM: ChecksumAlgorithm = 'CRC32C'
const SAFE_FILE_COMPONENT = /^[A-Za-z0-9][A-Za-z0-9._-]*$/
const SAFE_PATH_SEGMENT = /^[^/\\\p{Cc}]+$/u

type UploadStage = 'idle' | 'checksum' | 'requesting-url' | 'uploading' | 'verifying' | 'complete'

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
      const response = data as { message?: string; detail?: string; error?: string }
      return response.message || response.detail || response.error || 'The firmware API rejected the request.'
    }
  }
  return 'An unexpected error occurred while uploading the firmware.'
}

const AdminFirmwareTab = () => {
  const [vendorName, setVendorName] = useState('')
  const [modelName, setModelName] = useState('')
  const [version, setVersion] = useState('')
  const [fileName, setFileName] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [stage, setStage] = useState<UploadStage>('idle')
  const [progress, setProgress] = useState(0)
  const [errorMessage, setErrorMessage] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [createUploadUrl] = useCreateFirmwareUploadUrlMutation()
  const [completeUpload] = useCompleteFirmwareUploadMutation()

  const isWorking = !['idle', 'complete'].includes(stage)

  const selectFile = (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0] ?? null
    setFile(selectedFile)
    if (selectedFile) setFileName(selectedFile.name)
    setErrorMessage('')
    setSuccessMessage('')
    setProgress(0)
    setStage('idle')
  }

  const validateForm = () => {
    if (!file) return 'Please choose a firmware file.'
    if (!vendorName.trim() || !SAFE_PATH_SEGMENT.test(vendorName.trim())) return 'Please enter a valid vendor name.'
    if (!modelName.trim() || !SAFE_PATH_SEGMENT.test(modelName.trim())) return 'Please enter a valid model name.'
    if (!SAFE_FILE_COMPONENT.test(version.trim())) {
      return 'Version must start with a letter or number and use only letters, numbers, dots, underscores, or hyphens.'
    }
    if (!SAFE_FILE_COMPONENT.test(fileName.trim())) {
      return 'File name must start with a letter or number and use only letters, numbers, dots, underscores, or hyphens.'
    }
    return null
  }

  const submitUpload = async (event: FormEvent) => {
    event.preventDefault()
    setErrorMessage('')
    setSuccessMessage('')
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
        file_name: fileName.trim(),
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
      setSuccessMessage(`Firmware uploaded and verified successfully: ${verification.object_name}`)
    } catch (error) {
      setStage('idle')
      setErrorMessage(getErrorMessage(error))
    }
  }

  return (
    <Box sx={{ maxWidth: 720 }}>
      <Typography variant="h5" className="panel-header" sx={{ pl: 0, pt: 0 }}>
        Firmware Upload
      </Typography>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Box component="form" onSubmit={submitUpload} noValidate>
          <Stack spacing={2.5}>
            <Typography color="text.secondary">
              Upload a firmware artifact directly to object storage and verify it with the firmware API.
            </Typography>
            <TextField
              label="Vendor Name"
              value={vendorName}
              onChange={(event) => setVendorName(event.target.value)}
              required
              disabled={isWorking}
              inputProps={{ maxLength: 128 }}
            />
            <TextField
              label="Model Name"
              value={modelName}
              onChange={(event) => setModelName(event.target.value)}
              required
              disabled={isWorking}
              inputProps={{ maxLength: 128 }}
            />
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
            <TextField
              label="Stored File Name"
              value={fileName}
              onChange={(event) => setFileName(event.target.value)}
              helperText="Defaults to the selected file name and may be changed before upload"
              required
              disabled={isWorking}
              inputProps={{ maxLength: 128 }}
            />
            <TextField label="Checksum Algorithm" value={DEFAULT_CHECKSUM_ALGORITHM} disabled />

            <Stack direction="row" spacing={3} alignItems="center">
              <Button type="submit" variant="contained" className="museo-slab capital-case" disabled={isWorking}>
                Upload Firmware
              </Button>
              {(isWorking || stage === 'complete') && (
                <Stack direction="row" spacing={1.5} alignItems="center">
                  <Box sx={{ position: 'relative', display: 'inline-flex' }}>
                    <CircularProgress
                      variant="determinate"
                      value={stage === 'complete' || stage === 'verifying' ? 100 : progress}
                      aria-label="Firmware upload progress"
                    />
                    <Box
                      sx={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    >
                      <Typography variant="caption" color="text.secondary">{`${
                        stage === 'complete' || stage === 'verifying' ? 100 : progress
                      }%`}</Typography>
                    </Box>
                  </Box>
                  <Typography>{stageLabel[stage]}</Typography>
                </Stack>
              )}
            </Stack>

            {errorMessage && <ErrorMessageText role="alert">{errorMessage}</ErrorMessageText>}
            {successMessage && <SuccessMessageText role="status">{successMessage}</SuccessMessageText>}
          </Stack>
        </Box>
      </Paper>
    </Box>
  )
}

export default AdminFirmwareTab

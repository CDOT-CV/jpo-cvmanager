import { Box, CircularProgress, Stack, Typography } from '@mui/material'
import { stageLabel, UploadStage } from './signedUpload'

type FirmwareUploadProgressProps = {
  stage: UploadStage
  progress: number
}

// Shows upload progress while a signed firmware upload runs and once it completes
const FirmwareUploadProgress = ({ stage, progress }: FirmwareUploadProgressProps) => {
  if (stage === 'idle') return null
  const value = stage === 'complete' || stage === 'verifying' ? 100 : progress

  return (
    <Stack direction="row" spacing={1.5} alignItems="center">
      <Box sx={{ position: 'relative', display: 'inline-flex' }}>
        <CircularProgress variant="determinate" value={value} aria-label="Firmware upload progress" />
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Typography variant="caption" color="text.secondary">{`${value}%`}</Typography>
        </Box>
      </Box>
      <Typography>{stageLabel[stage]}</Typography>
    </Stack>
  )
}

export default FirmwareUploadProgress

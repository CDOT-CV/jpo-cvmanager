import { FirmwareUploadUrl } from '../../models/Firmware'
import { calculateFileChecksum, uploadFileToSignedUrl } from './firmwareUpload'

export const DEFAULT_CHECKSUM_ALGORITHM = 'CRC32C'

export type UploadStage = 'idle' | 'checksum' | 'requesting-url' | 'uploading' | 'verifying' | 'complete'

export const stageLabel: Record<UploadStage, string> = {
  idle: 'Ready to upload',
  checksum: 'Calculating checksum…',
  'requesting-url': 'Requesting upload URL…',
  uploading: 'Uploading firmware…',
  verifying: 'Verifying uploaded firmware…',
  complete: 'Upload complete',
}

// The file details every signed upload request carries, whatever else a firmware
// workflow adds to identify the firmware
export type SignedUploadFile = {
  file_name: string
  content_length: number
  content_type: string
  checksum_algorithm: string
  checksum: string
}

type SignedUploadOptions<Verification extends { status: string }> = {
  file: File
  requestUploadUrl: (upload: SignedUploadFile) => Promise<FirmwareUploadUrl>
  completeUpload: (uploadId: string) => Promise<Verification>
  onStage: (stage: UploadStage) => void
  onProgress: (percentage: number) => void
  // Describes an existing object when storage rejects the upload as a duplicate
  conflictMessage?: string
}

/**
 * Uploads a firmware file through a signed URL. The API signs an upload for the
 * checksum and size calculated here, the browser uploads directly to storage, and
 * the API then verifies the stored file.
 */
export const runSignedUpload = async <Verification extends { status: string }>({
  file,
  requestUploadUrl,
  completeUpload,
  onStage,
  onProgress,
  conflictMessage,
}: SignedUploadOptions<Verification>): Promise<Verification> => {
  onStage('checksum')
  const checksum = await calculateFileChecksum(file, DEFAULT_CHECKSUM_ALGORITHM)

  onStage('requesting-url')
  const instructions = await requestUploadUrl({
    file_name: file.name,
    content_length: file.size,
    content_type: file.type || 'application/octet-stream',
    checksum_algorithm: DEFAULT_CHECKSUM_ALGORITHM,
    checksum,
  })

  onStage('uploading')
  if (conflictMessage) {
    await uploadFileToSignedUrl(file, instructions, onProgress, conflictMessage)
  } else {
    await uploadFileToSignedUrl(file, instructions, onProgress)
  }

  onStage('verifying')
  const verification = await completeUpload(instructions.upload_id)
  if (verification.status !== 'VERIFIED') {
    throw new Error('The firmware API did not verify the uploaded file.')
  }

  onStage('complete')
  return verification
}

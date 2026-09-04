export type ChecksumAlgorithm = string

export type FirmwareUploadUrlRequest = {
  vendor_name: string
  model_name: string
  version: string
  file_name: string
  content_length: number
  content_type: string
  checksum_algorithm: ChecksumAlgorithm
  checksum: string
}

export type FirmwareUploadUrl = {
  upload_id: string
  upload_url: string
  method: string
  object_name: string
  expires_at: string
  required_headers: Record<string, string>
}

export type FirmwareUploadVerification = {
  upload_id: string
  status: 'PENDING' | 'VERIFIED' | 'FAILED' | 'EXPIRED'
  object_name: string
  content_length: number
  checksum_algorithm: string
  checksum: string
  provider_object_version: string | null
  verified_at: string | null
}

export type ChecksumAlgorithm = string

export type FirmwareObject = {
  object_id: string
  object_name: string
  content_length: number
  updated_at: string | number | null
  provider_object_version: string | null
  upload_id: string | null
  firmware_id: number | null
  upload_status: string | null
  verification_status: 'VERIFIED' | 'UNVERIFIED' | 'UNTRACKED' | 'CHANGED'
}

export type FirmwareObjectPage = {
  provider: string
  objects: FirmwareObject[]
}

export type FirmwareUploadModelOption = {
  model_id: number
  name: string
}

export type FirmwareUploadManufacturerOption = {
  manufacturer_id: number
  name: string
  models: FirmwareUploadModelOption[]
}

export type FirmwareUploadOptions = {
  manufacturers: FirmwareUploadManufacturerOption[]
}

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

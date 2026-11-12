import { FirmwareUploadUrl } from './Firmware'

export type ObuFirmwareVerificationStatus =
  | 'VERIFIED'
  | 'CHANGED'
  | 'MISSING'
  | 'UNVERIFIED'
  | 'UNTRACKED'
  | 'LOCAL'
  | 'UNKNOWN'

export type ObuFirmwareDeviceType = 'OBU' | 'RSU'

export type ObuFirmwareObject = {
  object_id: string
  object_name: string
  storage_provider: string | null
  provider_object_version: string | null
  manufacturer: string | null
  file_name: string
  device_type: ObuFirmwareDeviceType | null
  variant: string | null
  release_version: string | null
  writable_system_partition: boolean | null
  secure_boot: boolean | null
  content_length: number | null
  updated_at: string | null
  registered_at: string | null
  upload_id: string | null
  upload_status: string | null
  obu_firmware_id: number | null
  published: boolean
  published_at: string | null
  published_by: string | null
  verification_status: ObuFirmwareVerificationStatus
}

export type ObuFirmwareObjectPage = {
  provider: string | null
  storage_available: boolean
  objects: ObuFirmwareObject[]
  total_elements: number
}

export type ObuFirmwareObjectQuery = {
  page?: number
  size?: number
  manufacturer?: string
  search?: string
  published?: boolean
  sort?: string
}

export type ObuFirmwareManufacturerOption = {
  manufacturer_id: number
  name: string
  file_extension: string
  file_name_pattern: string
  max_file_name_length: number
  example_file_name: string
}

export type ObuFirmwareUploadOptions = {
  manufacturers: ObuFirmwareManufacturerOption[]
}

export type ObuFirmwareUploadUrlRequest = {
  manufacturer_name: string
  file_name: string
  content_length: number
  content_type: string
  checksum_algorithm: string
  checksum: string
}

export type ObuFirmwareUploadUrl = FirmwareUploadUrl

export type ObuFirmwareUploadVerification = {
  upload_id: string
  status: 'PENDING' | 'VERIFIED' | 'FAILED' | 'EXPIRED'
  object_name: string
  content_length: number
  checksum_algorithm: string
  checksum: string
  provider_object_version: string | null
  verified_at: string | null
  obu_firmware_id: number | null
}

// Publishing a release withdraws the published release of the same build, listed in replaced
export type ObuFirmwarePublication = {
  obu_firmware_id: number
  published: boolean
  published_at: string | null
  published_by: string | null
  replaced: ObuFirmwareReplacement[]
}

export type ObuFirmwareReplacement = {
  obu_firmware_id: number
  file_name: string
}

export type ObuOtaRequest = {
  request_id: number
  obu_sn: string
  request_datetime: string
  origin_ip: string
  obu_firmware_version: string
  requested_firmware_version: string
  status: 'success' | 'error'
  error_message: string
  manufacturer: string
}

export type ObuOtaRequestPage = {
  items: ObuOtaRequest[]
  total_elements: number
}

export type ObuOtaRequestQuery = {
  page?: number
  size?: number
  serial?: string
  manufacturer?: string
  status?: 'success' | 'error'
  start?: string
  end?: string
  sort?: string
}

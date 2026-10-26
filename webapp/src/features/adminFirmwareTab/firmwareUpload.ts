import { FirmwareUploadUrl } from '../../models/Firmware'

const CRC32C_POLYNOMIAL = 0x82f63b78
const CHECKSUM_CHUNK_SIZE = 4 * 1024 * 1024
const FILE_SIZE_BASE = 1024
const FILE_SIZE_UNITS = ['bytes', 'KB', 'MB', 'GB'] as const

// Build the lookup table once so large files can be checksummed efficiently
const crc32cTable = new Uint32Array(256)
for (let tableIndex = 0; tableIndex < crc32cTable.length; tableIndex++) {
  let value = tableIndex
  for (let bit = 0; bit < 8; bit++) {
    value = value & 1 ? (value >>> 1) ^ CRC32C_POLYNOMIAL : value >>> 1
  }
  crc32cTable[tableIndex] = value >>> 0
}

const uint32ToBase64 = (value: number) => {
  const bytes = new Uint8Array(4)
  new DataView(bytes.buffer).setUint32(0, value, false)
  return btoa(String.fromCodePoint(...bytes))
}

const checksumChunk = (file: Blob, offset: number, crc: number): Promise<string> => {
  if (offset >= file.size) return Promise.resolve(uint32ToBase64((crc ^ 0xffffffff) >>> 0))
  // Read sequentially to keep memory bounded to one chunk, even for large artifacts.
  return file.slice(offset, offset + CHECKSUM_CHUNK_SIZE).arrayBuffer().then((buffer) => {
    let nextCrc = crc
    for (const byte of new Uint8Array(buffer)) {
      nextCrc = (nextCrc >>> 8) ^ crc32cTable[(nextCrc ^ byte) & 0xff]
    }
    return checksumChunk(file, offset + CHECKSUM_CHUNK_SIZE, nextCrc)
  }, () => {
    throw new Error('The firmware file could not be read.')
  })
}

const calculateCrc32c = (file: Blob) => {
  return checksumChunk(file, 0, 0xffffffff)
}

const checksumCalculators: Record<string, (file: Blob) => Promise<string>> = {
  CRC32C: calculateCrc32c,
}

export const getObjectStorageUploadError = (status: number) => {
  if (status === 409 || status === 412) {
    return new Error(
      'Firmware already exists for this manufacturer, model, and version. Change the version and try again.'
    )
  }
  return new Error(`Object storage rejected the file upload (HTTP ${status}).`)
}

export const formatFileSize = (bytes: number) => {
  if (!Number.isFinite(bytes) || bytes < 0) return ''
  if (bytes < FILE_SIZE_BASE) return `${bytes.toLocaleString()} ${bytes === 1 ? 'byte' : 'bytes'}`

  const unitIndex = Math.min(Math.floor(Math.log(bytes) / Math.log(FILE_SIZE_BASE)), FILE_SIZE_UNITS.length - 1)
  const value = bytes / FILE_SIZE_BASE ** unitIndex
  const formattedValue = value.toLocaleString(undefined, { maximumFractionDigits: 1 })
  return `${formattedValue} ${FILE_SIZE_UNITS[unitIndex]}`
}

// This dispatch point keeps checksum selection outside the form and leaves room
// for storage providers that require a different checksum algorithm
export const calculateFileChecksum = (file: Blob, algorithm: string) => {
  const calculator = checksumCalculators[algorithm.toUpperCase()]
  if (!calculator) throw new Error(`Checksum algorithm ${algorithm} is not supported.`)
  return calculator(file)
}

export const uploadFileToSignedUrl = (
  file: File,
  instructions: FirmwareUploadUrl,
  onProgress: (percentage: number) => void
) =>
  new Promise<void>((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open(instructions.method, instructions.upload_url)
    Object.entries(instructions.required_headers).forEach(([name, value]) => request.setRequestHeader(name, value))

    request.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
    request.onerror = () => reject(new Error('The file upload could not reach object storage.'))
    request.onabort = () => reject(new Error('The file upload was cancelled.'))
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        onProgress(100)
        resolve()
      } else {
        reject(getObjectStorageUploadError(request.status))
      }
    }

    request.send(file)
  })

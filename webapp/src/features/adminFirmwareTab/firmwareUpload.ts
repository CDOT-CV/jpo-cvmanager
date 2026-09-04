import { ChecksumAlgorithm, FirmwareUploadUrl } from '../../models/Firmware'

const CRC32C_POLYNOMIAL = 0x82f63b78
const CHECKSUM_CHUNK_SIZE = 4 * 1024 * 1024

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
  return btoa(String.fromCharCode(...bytes))
}

const readBlob = (blob: Blob) =>
  new Promise<ArrayBuffer>((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('The firmware file could not be read.'))
    reader.onload = () => resolve(reader.result as ArrayBuffer)
    reader.readAsArrayBuffer(blob)
  })

const calculateCrc32c = async (file: Blob) => {
  let crc = 0xffffffff
  for (let offset = 0; offset < file.size; offset += CHECKSUM_CHUNK_SIZE) {
    const bytes = new Uint8Array(await readBlob(file.slice(offset, offset + CHECKSUM_CHUNK_SIZE)))
    for (const byte of bytes) {
      crc = (crc >>> 8) ^ crc32cTable[(crc ^ byte) & 0xff]
    }
  }
  return uint32ToBase64((crc ^ 0xffffffff) >>> 0)
}

const checksumCalculators: Record<string, (file: Blob) => Promise<string>> = {
  CRC32C: calculateCrc32c,
}

export const getObjectStorageUploadError = (status: number) => {
  if (status === 409 || status === 412) {
    return new Error(
      'A firmware file already exists for this vendor, model, version, and file name. Change the version or stored file name and try again.'
    )
  }
  return new Error(`Object storage rejected the file upload (HTTP ${status}).`)
}

// This dispatch point keeps checksum selection outside the form and leaves room
// for storage providers that require a different checksum algorithm.
export const calculateFileChecksum = (file: Blob, algorithm: ChecksumAlgorithm) => {
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

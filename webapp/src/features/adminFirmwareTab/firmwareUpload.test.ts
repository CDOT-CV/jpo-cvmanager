import { afterEach, beforeEach, vi } from 'vitest'
import { FirmwareUploadUrl } from '../../models/Firmware'
import { calculateFileChecksum, getObjectStorageUploadError, uploadFileToSignedUrl } from './firmwareUpload'

class FakeXMLHttpRequest {
  static instances: FakeXMLHttpRequest[] = []

  method = ''
  url = ''
  status = 0
  body: Document | XMLHttpRequestBodyInit | null = null
  headers = new Map<string, string>()
  upload = {
    onprogress: null as ((event: ProgressEvent) => void) | null,
  }
  onerror: (() => void) | null = null
  onabort: (() => void) | null = null
  onload: (() => void) | null = null

  constructor() {
    FakeXMLHttpRequest.instances.push(this)
  }

  open(method: string, url: string) {
    this.method = method
    this.url = url
  }

  setRequestHeader(name: string, value: string) {
    this.headers.set(name, value)
  }

  send(body: Document | XMLHttpRequestBodyInit | null) {
    this.body = body
  }
}

const uploadInstructions: FirmwareUploadUrl = {
  upload_id: 'c8ddabda-d98c-4b2d-b719-c79f180f5801',
  upload_url: 'https://storage.googleapis.com/signed',
  method: 'PUT',
  object_name: 'Commsignia/ITS-RS4-M/y20.97.0/firmware.tar.sig',
  expires_at: '2026-09-03T23:00:00Z',
  required_headers: {
    'Content-Type': 'application/octet-stream',
    'x-goog-hash': 'crc32c=4waSgw==',
    'x-goog-if-generation-match': '0',
  },
}

describe('calculateFileChecksum', () => {
  it('calculates the canonical base64 CRC32C checksum', async () => {
    const file = new Blob(['123456789'])

    await expect(calculateFileChecksum(file, 'CRC32C')).resolves.toBe('4waSgw==')
  })

  it('calculates the CRC32C checksum for an empty file', async () => {
    const file = new Blob([])

    await expect(calculateFileChecksum(file, 'CRC32C')).resolves.toBe('AAAAAA==')
  })
})

describe('getObjectStorageUploadError', () => {
  it.each([409, 412])('describes an existing object for HTTP %s', (status) => {
    expect(getObjectStorageUploadError(status).message).toBe(
      'A firmware file already exists for this vendor, model, version, and file name. Change the version or stored file name and try again.'
    )
  })

  it('retains the status for other object-storage failures', () => {
    expect(getObjectStorageUploadError(503).message).toBe('Object storage rejected the file upload (HTTP 503).')
  })
})

describe('uploadFileToSignedUrl', () => {
  beforeEach(() => {
    FakeXMLHttpRequest.instances = []
    vi.stubGlobal('XMLHttpRequest', FakeXMLHttpRequest)
  })

  afterEach(() => vi.unstubAllGlobals())

  it('sends the file using every signed upload instruction and reports progress', async () => {
    const file = new File(['firmware'], 'firmware.tar.sig', { type: 'application/octet-stream' })
    const onProgress = vi.fn()

    const upload = uploadFileToSignedUrl(file, uploadInstructions, onProgress)
    const request = FakeXMLHttpRequest.instances[0]

    expect(request.method).toBe('PUT')
    expect(request.url).toBe(uploadInstructions.upload_url)
    expect(request.headers).toEqual(new Map(Object.entries(uploadInstructions.required_headers)))
    expect(request.body).toBe(file)

    request.upload.onprogress?.({ lengthComputable: true, loaded: 4, total: 9 } as ProgressEvent)
    expect(onProgress).toHaveBeenCalledWith(44)

    request.status = 204
    request.onload?.()

    await expect(upload).resolves.toBeUndefined()
    expect(onProgress).toHaveBeenLastCalledWith(100)
  })

  it('maps a GCS precondition failure to the existing-file error', async () => {
    const upload = uploadFileToSignedUrl(
      new File(['firmware'], 'firmware.tar.sig'),
      uploadInstructions,
      vi.fn()
    )
    const request = FakeXMLHttpRequest.instances[0]

    request.status = 412
    request.onload?.()

    await expect(upload).rejects.toThrow('A firmware file already exists')
  })

  it('reports network errors and upload cancellation distinctly', async () => {
    const networkUpload = uploadFileToSignedUrl(
      new File(['firmware'], 'firmware.tar.sig'),
      uploadInstructions,
      vi.fn()
    )
    FakeXMLHttpRequest.instances[0].onerror?.()
    await expect(networkUpload).rejects.toThrow('could not reach object storage')

    const cancelledUpload = uploadFileToSignedUrl(
      new File(['firmware'], 'firmware.tar.sig'),
      uploadInstructions,
      vi.fn()
    )
    FakeXMLHttpRequest.instances[1].onabort?.()
    await expect(cancelledUpload).rejects.toThrow('was cancelled')
  })
})

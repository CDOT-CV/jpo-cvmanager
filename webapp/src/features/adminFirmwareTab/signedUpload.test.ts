import { vi } from 'vitest'
import { calculateFileChecksum, uploadFileToSignedUrl } from './firmwareUpload'
import { runSignedUpload, UploadStage } from './signedUpload'

vi.mock('./firmwareUpload', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./firmwareUpload')>()
  return {
    ...actual,
    calculateFileChecksum: vi.fn(),
    uploadFileToSignedUrl: vi.fn(),
  }
})

const instructions = {
  upload_id: 'upload-1',
  upload_url: 'https://storage.googleapis.com/signed',
  method: 'PUT',
  object_name: 'ota/Commsignia/firmware.tar.sig',
  expires_at: '2026-10-15T12:15:00Z',
  required_headers: {},
}

describe('runSignedUpload', () => {
  const file = new File(['firmware'], 'firmware.tar.sig')

  beforeEach(() => {
    vi.mocked(calculateFileChecksum).mockReset().mockResolvedValue('4waSgw==')
    vi.mocked(uploadFileToSignedUrl).mockReset().mockResolvedValue()
  })

  it('checksums, requests a URL, uploads, and verifies in order', async () => {
    const stages: UploadStage[] = []
    const requestUploadUrl = vi.fn().mockResolvedValue(instructions)
    const completeUpload = vi.fn().mockResolvedValue({ status: 'VERIFIED', obu_firmware_id: 3 })
    const onProgress = vi.fn()

    const verification = await runSignedUpload({
      file,
      requestUploadUrl,
      completeUpload,
      onStage: (stage) => stages.push(stage),
      onProgress,
    })

    expect(stages).toEqual(['checksum', 'requesting-url', 'uploading', 'verifying', 'complete'])
    expect(calculateFileChecksum).toHaveBeenCalledWith(file, 'CRC32C')
    expect(requestUploadUrl).toHaveBeenCalledWith({
      file_name: 'firmware.tar.sig',
      content_length: 8,
      content_type: 'application/octet-stream',
      checksum_algorithm: 'CRC32C',
      checksum: '4waSgw==',
    })
    expect(uploadFileToSignedUrl).toHaveBeenCalledWith(file, instructions, onProgress)
    expect(completeUpload).toHaveBeenCalledWith('upload-1')
    expect(verification).toEqual({ status: 'VERIFIED', obu_firmware_id: 3 })
  })

  it('passes a workflow-specific conflict message to the storage upload', async () => {
    const onProgress = vi.fn()

    await runSignedUpload({
      file,
      requestUploadUrl: vi.fn().mockResolvedValue(instructions),
      completeUpload: vi.fn().mockResolvedValue({ status: 'VERIFIED' }),
      onStage: vi.fn(),
      onProgress,
      conflictMessage: 'An OBU firmware file with this name already exists.',
    })

    expect(uploadFileToSignedUrl).toHaveBeenCalledWith(
      file,
      instructions,
      onProgress,
      'An OBU firmware file with this name already exists.'
    )
  })

  it('rejects an upload the API did not verify', async () => {
    const onStage = vi.fn()

    await expect(
      runSignedUpload({
        file,
        requestUploadUrl: vi.fn().mockResolvedValue(instructions),
        completeUpload: vi.fn().mockResolvedValue({ status: 'FAILED' }),
        onStage,
        onProgress: vi.fn(),
      })
    ).rejects.toThrow('The firmware API did not verify the uploaded file.')
    expect(onStage).not.toHaveBeenCalledWith('complete')
  })

  it('stops before uploading when no URL is issued', async () => {
    await expect(
      runSignedUpload({
        file,
        requestUploadUrl: vi.fn().mockRejectedValue(new Error('already exists')),
        completeUpload: vi.fn(),
        onStage: vi.fn(),
        onProgress: vi.fn(),
      })
    ).rejects.toThrow('already exists')
    expect(uploadFileToSignedUrl).not.toHaveBeenCalled()
  })
})

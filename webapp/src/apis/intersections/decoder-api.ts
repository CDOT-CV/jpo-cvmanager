import { authApiHelper } from './api-helper-cviz'

class DecoderApi {
  async getIntersections({
    token,
    abortController,
  }: {
    token: string
    abortController?: AbortController
  }): Promise<IntersectionReferenceData[]> {
    var response = await authApiHelper.invokeApi({
      path: '/intersection/list',
      token: token,
      abortController,
      failureMessage: 'Failed to retrieve intersection list',
      tag: 'intersection',
    })
    return response ?? []
  }

  async submitDecodeRequest({
    token,
    data,
    type,
    abortController,
  }: {
    token: string
    data: string
    type?: DECODER_MESSAGE_TYPE
    abortController?: AbortController
  }): Promise<DecoderApiResponseGeneric | undefined> {
    var response = await authApiHelper.invokeApi({
      path: '/decoder/upload',
      token: token,
      method: 'POST',
      body: {
        asn1Message: data,
        type: type,
      },
      tag: 'intersection',
      abortController,
    })
    return response as DecoderApiResponseGeneric | undefined
  }

  async submitPcapDecodeRequest({
    data,
    abortController
  }: {
    data: ArrayBuffer,
    abortController?: AbortController
  }) : Promise<DecoderApiResponseGeneric | undefined> {
    console.log("submitPcapDecodeRequest.  posting " + data.byteLength + " bytes")
    const response = await authApiHelper.invokeApi({
      path: '/pcap/uper',
      method: 'POST',
      headers: { "Content-Type": 'application/octet-stream' },
      body: data,
      tag: 'intersection',
      abortController: abortController
    })
    return response as DecoderApiResponseGeneric | undefined
  }
}

export default new DecoderApi()

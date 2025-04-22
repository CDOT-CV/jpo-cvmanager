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

  async submitPcapDecodeRequest(
    token: string,
    data: ArrayBuffer,
    abortController?: AbortController
  ) : Promise<any> {
    console.log("submitPcapDecodeRequest.  posting " + data.byteLength + " bytes")
    const response = await authApiHelper.invokeApi({
      path: '/pcap/uper',
      token: token,
      method: 'POST',
      headers: { "Content-Type": 'application/octet-stream' },
      body: data,
      tag: 'intersection',
      abortController: abortController
    })
    return response
  }

  async submitBatchDecodeRequest(
    token: string,
    data: any[], 
    abortController?: AbortController
  ) : Promise<any> {
    console.log("submitBatchDecodeRequest.  posting " + data.length + " chars")
    const response = await authApiHelper.invokeApi({
      path: '/uper/json',
      token: token,
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: data,
      tag: 'intersection',
      abortController: abortController
    })
    return response
  }
}



export default new DecoderApi()

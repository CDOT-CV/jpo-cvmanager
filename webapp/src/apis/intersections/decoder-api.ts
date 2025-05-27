import { apiHelper } from '../api-helper'

class DecoderApi {
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
    var response = await apiHelper.invokeApi({
      path: 'asn1/decoder/raw',
      token: token,
      method: 'POST',
      body: {
        asn1Message: data,
        type: type,
      },
      tag: 'intersection',
      abortController,
    })
    return response?.content?.[0] as DecoderApiResponseGeneric | undefined
  }
}

export default new DecoderApi()

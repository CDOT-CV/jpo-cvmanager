import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { selectToken } from '../../generalSlices/userSlice'
import { FirmwareUploadUrl, FirmwareUploadUrlRequest, FirmwareUploadVerification } from '../../models/Firmware'
import { RootState } from '../../store'

export const firmwareApiSlice = createApi({
  reducerPath: 'firmwareApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${EnvironmentVars.CVIZ_API_SERVER_URL}/admin/firmware`,
    prepareHeaders: (headers, { getState }) => {
      const token = selectToken(getState() as RootState)
      headers.set('Accept', 'application/json')
      headers.set('Content-Type', 'application/json')
      if (token) {
        headers.set('Authorization', `Bearer ${token}`)
      }
      return headers
    },
  }),
  endpoints: (builder) => ({
    createFirmwareUploadUrl: builder.mutation<FirmwareUploadUrl, FirmwareUploadUrlRequest>({
      query: (body) => ({
        url: 'signed-upload-url',
        method: 'POST',
        body,
      }),
    }),
    completeFirmwareUpload: builder.mutation<FirmwareUploadVerification, string>({
      query: (uploadId) => ({
        url: `uploads/${encodeURIComponent(uploadId)}/complete`,
        method: 'POST',
      }),
    }),
  }),
})

export const { useCreateFirmwareUploadUrlMutation, useCompleteFirmwareUploadMutation } = firmwareApiSlice

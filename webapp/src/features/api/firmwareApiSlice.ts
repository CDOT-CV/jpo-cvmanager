import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { selectToken } from '../../generalSlices/userSlice'
import {
  FirmwareObjectPage,
  FirmwareUploadUrl,
  FirmwareUploadUrlRequest,
  FirmwareUploadVerification,
} from '../../models/Firmware'
import { RootState } from '../../store'

export const firmwareApiSlice = createApi({
  reducerPath: 'firmwareApi',
  tagTypes: ['FirmwareObjects'],
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
    listFirmwareObjects: builder.query<FirmwareObjectPage, { page_token?: string; page_size?: number }>({
      query: (params) => ({ url: 'objects', params }),
      providesTags: ['FirmwareObjects'],
    }),
    createFirmwareUploadUrl: builder.mutation<FirmwareUploadUrl, FirmwareUploadUrlRequest>({
      query: (body) => ({
        url: 'signed-upload-url',
        method: 'POST',
        body,
      }),
    }),
    completeFirmwareUpload: builder.mutation<FirmwareUploadVerification, string>({
      invalidatesTags: ['FirmwareObjects'],
      query: (uploadId) => ({
        url: `uploads/${encodeURIComponent(uploadId)}/complete`,
        method: 'POST',
      }),
    }),
  }),
})

export const { useListFirmwareObjectsQuery, useCreateFirmwareUploadUrlMutation, useCompleteFirmwareUploadMutation } =
  firmwareApiSlice

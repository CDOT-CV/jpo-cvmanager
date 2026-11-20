import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { selectToken } from '../../generalSlices/userSlice'
import {
  ObuFirmwareFileNameCheck,
  ObuFirmwareFileNameCheckQuery,
  ObuFirmwareObjectPage,
  ObuFirmwareObjectQuery,
  ObuFirmwarePublication,
  ObuFirmwareUploadOptions,
  ObuFirmwareUploadUrl,
  ObuFirmwareUploadUrlRequest,
  ObuFirmwareUploadVerification,
  ObuOtaRequestPage,
  ObuOtaRequestQuery,
} from '../../models/ObuFirmware'
import { RootState } from '../../store'

export const obuFirmwareApiSlice = createApi({
  reducerPath: 'obuFirmwareApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${EnvironmentVars.CVIZ_API_SERVER_URL}/admin`,
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
    getObuFirmwareUploadOptions: builder.query<ObuFirmwareUploadOptions, void>({
      query: () => 'obu-firmware/upload-options',
    }),
    // Applies the upload's file name checks without reserving the name
    checkObuFirmwareFileName: builder.query<ObuFirmwareFileNameCheck, ObuFirmwareFileNameCheckQuery>({
      query: (params) => ({ url: 'obu-firmware/file-name-check', params }),
      keepUnusedDataFor: 0,
    }),
    listObuFirmwareObjects: builder.query<ObuFirmwareObjectPage, ObuFirmwareObjectQuery>({
      query: (params) => ({ url: 'obu-firmware/objects', params }),
    }),
    createObuFirmwareUploadUrl: builder.mutation<ObuFirmwareUploadUrl, ObuFirmwareUploadUrlRequest>({
      query: (body) => ({ url: 'obu-firmware/signed-upload-url', method: 'POST', body }),
    }),
    completeObuFirmwareUpload: builder.mutation<ObuFirmwareUploadVerification, string>({
      query: (uploadId) => ({ url: `obu-firmware/uploads/${encodeURIComponent(uploadId)}/complete`, method: 'POST' }),
    }),
    setObuFirmwarePublished: builder.mutation<ObuFirmwarePublication, { obuFirmwareId: number; published: boolean }>({
      query: ({ obuFirmwareId, published }) => ({
        url: `obu-firmware/images/${obuFirmwareId}/publication`,
        method: 'PUT',
        body: { published },
      }),
    }),
    // A null version removes only the records, for files that are missing from storage
    // or stored on the OBU OTA server's local firmware folder
    deleteObuFirmwareObject: builder.mutation<void, { object_id: string; provider_object_version: string | null }>({
      query: ({ object_id, provider_object_version }) => ({
        url: `obu-firmware/objects/${encodeURIComponent(object_id)}${provider_object_version === null ? '/records' : ''}`,
        method: 'DELETE',
        params: provider_object_version === null ? undefined : { provider_object_version },
      }),
    }),
    listObuOtaRequests: builder.query<ObuOtaRequestPage, ObuOtaRequestQuery>({
      query: (params) => ({ url: 'obu-ota-requests', params }),
    }),
  }),
})

export const {
  useGetObuFirmwareUploadOptionsQuery,
  useLazyCheckObuFirmwareFileNameQuery,
  useLazyListObuFirmwareObjectsQuery,
  useCreateObuFirmwareUploadUrlMutation,
  useCompleteObuFirmwareUploadMutation,
  useSetObuFirmwarePublishedMutation,
  useDeleteObuFirmwareObjectMutation,
  useLazyListObuOtaRequestsQuery,
} = obuFirmwareApiSlice

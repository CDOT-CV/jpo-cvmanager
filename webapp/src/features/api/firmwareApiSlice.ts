import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { selectToken } from '../../generalSlices/userSlice'
import {
  FirmwareObjectPage,
  FirmwareRule,
  FirmwareRuleOptions,
  FirmwareUploadOptions,
  FirmwareUploadUrl,
  FirmwareUploadUrlRequest,
  FirmwareUploadVerification,
} from '../../models/Firmware'
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
    listFirmwareRules: builder.query<FirmwareRule[], void>({
      query: () => 'upgrade-rules',
    }),
    getFirmwareRuleOptions: builder.query<FirmwareRuleOptions, number>({
      query: (id) => `images/${id}/upgrade-rules`,
    }),
    assignFirmwareRules: builder.mutation<void, {
      destinationId: number
      sources: { source_id: number; expected_target_id: number | null }[]
    }>({
      query: ({ destinationId, sources }) => ({
        url: `images/${destinationId}/upgrade-rules`, method: 'PUT', body: { sources },
      }),
    }),
    deleteFirmwareRule: builder.mutation<void, { ruleId: number; expectedTargetId: number }>({
      query: ({ ruleId, expectedTargetId }) => ({
        url: `upgrade-rules/${ruleId}`, method: 'DELETE', params: { expected_target_id: expectedTargetId },
      }),
    }),
    getFirmwareUploadOptions: builder.query<FirmwareUploadOptions, void>({
      query: () => 'upload-options',
    }),
    listFirmwareObjects: builder.query<
      FirmwareObjectPage,
      { page?: number; size?: number; manufacturer?: string; search?: string; sort?: string }
    >({
      query: (params) => ({ url: 'objects', params }),
    }),
    createFirmwareUploadUrl: builder.mutation<FirmwareUploadUrl, FirmwareUploadUrlRequest>({
      query: (body) => ({
        url: 'signed-upload-url',
        method: 'POST',
        body,
      }),
    }),
    deleteFirmwareObject: builder.mutation<void, { object_id: string; provider_object_version: string | null }>({
      query: ({ object_id, provider_object_version }) => ({
        url: `objects/${encodeURIComponent(object_id)}${provider_object_version === null ? '/records' : ''}`,
        method: 'DELETE',
        params: provider_object_version === null ? undefined : { provider_object_version },
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

export const {
  useListFirmwareRulesQuery,
  useGetFirmwareRuleOptionsQuery,
  useAssignFirmwareRulesMutation,
  useDeleteFirmwareRuleMutation,
  useGetFirmwareUploadOptionsQuery,
  useLazyListFirmwareObjectsQuery,
  useCreateFirmwareUploadUrlMutation,
  useCompleteFirmwareUploadMutation,
  useDeleteFirmwareObjectMutation,
} = firmwareApiSlice

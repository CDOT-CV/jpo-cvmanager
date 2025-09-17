// Need to use the React-specific entry point to import createApi
import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import { createSelector } from '@reduxjs/toolkit'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'
import { combineUrlPaths } from '../../apis/intersections/api-helper-cviz'

const getQueryString = (query_params: Record<string, string>) => {
  // filter out undefined values from query params
  const filteredQueryParams: Record<string, string> = { ...query_params }
  Object.keys(filteredQueryParams).forEach((key) => query_params[key] === undefined && delete query_params[key])
  const queryString = new URLSearchParams(query_params).toString()
  return `${queryString ? `?${queryString}` : ''}`
}

// Define types for firmware management
export type FirmwareFile = {
  id: string
  filename: string
  version: string
  file_size: number
  upload_date: string
  device_type: 'RSU' | 'OBU'
  description?: string
  checksum: string
  manufacturer_id?: number
  manufacturer?: {
    manufacturer_id: number
    name: string
  }
  rules?: FirmwareRule[]
}

export type FirmwareRule = {
  id: string
  name: string
  description: string
  target_devices: string[]
  firmware_id: string
  created_date: string
  is_active: boolean
  priority: number
}

export type FirmwareStatus = {
  rsu_ip: string
  current_version: string
  target_version: string
  upgrade_status: 'idle' | 'downloading' | 'installing' | 'completed' | 'failed'
  last_updated: string
  error_message?: string
}

export type FirmwareFilesResponse = {
  success: boolean
  message?: string
  firmware_files: FirmwareFile[]
}

export type FirmwareRulesResponse = {
  success: boolean
  message?: string
  firmware_rules: FirmwareRule[]
}

export type FirmwareStatusesResponse = {
  success: boolean
  message?: string
  firmware_statuses: FirmwareStatus[]
}

export type FirmwareUploadRequest = {
  file: File
  device_type: 'RSU' | 'OBU'
  description?: string
  manufacturer_id: number
}

export type Manufacturer = {
  manufacturer_id: number
  name: string
}

export type FirmwareRuleCreateRequest = {
  name: string
  description: string
  target_devices: string[]
  firmware_id: string
  priority: number
}

// Define a service using a base URL and expected endpoints
export const firmwareApiSlice = createApi({
  reducerPath: 'firmwareApi',
  baseQuery: fetchBaseQuery({
    baseUrl: combineUrlPaths(EnvironmentVars.getBaseApiUrl(), '/admin-firmware'),
    prepareHeaders: (headers, { getState, endpoint }) => {
      const token = selectToken(getState() as RootState)

      // Specify endpoints that do not require a token. These names must match the keys in the endpoints object below.
      const endpointsWithoutToken = []

      if (token && !endpointsWithoutToken.includes(endpoint)) {
        headers.set('Authorization', `Bearer ${token}`)
      }

      return headers
    },
  }),
  tagTypes: ['FirmwareFiles', 'FirmwareRules', 'FirmwareStatuses', 'Manufacturers'],
  endpoints: (builder) => ({
    getManufacturers: builder.query<{ success: boolean; manufacturers: Manufacturer[] }, undefined>({
      query: () => {
        return `/manufacturers`
      },
      providesTags: ['Manufacturers'],
    }),
    getFirmwareFiles: builder.query<FirmwareFilesResponse, string>({
      query: (deviceType) => {
        return `/files?device_type=${deviceType}`
      },
      providesTags: ['FirmwareFiles'],
    }),
    getFirmwareRules: builder.query<FirmwareRulesResponse, undefined>({
      query: () => {
        return `/rules`
      },
      providesTags: ['FirmwareRules'],
    }),
    getFirmwareStatuses: builder.query<FirmwareStatusesResponse, string>({
      query: (deviceType) => {
        return `/statuses?device_type=${deviceType}`
      },
      providesTags: ['FirmwareStatuses'],
    }),
    getRsuFirmwareStatuses: builder.query<FirmwareStatusesResponse, undefined>({
      query: () => {
        return `/statuses?device_type=RSU`
      },
      providesTags: ['FirmwareStatuses'],
    }),
    getObuFirmwareStatuses: builder.query<FirmwareStatusesResponse, undefined>({
      query: () => {
        return `/statuses?device_type=OBU`
      },
      providesTags: ['FirmwareStatuses'],
    }),
    uploadFirmwareFile: builder.mutation<{ success: boolean; message: string }, FirmwareUploadRequest>({
      query: ({ file, device_type, description, manufacturer_id }) => {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('device_type', device_type)
        formData.append('manufacturer_id', manufacturer_id.toString())
        if (description) {
          formData.append('description', description)
        }

        return {
          url: '/upload',
          method: 'POST',
          body: formData,
          // Don't set Content-Type header, let browser set it for FormData
        }
      },
      invalidatesTags: ['FirmwareFiles'],
    }),
    deleteFirmwareFile: builder.mutation<
      { success: boolean; message: string },
      { firmwareId: string; deviceType: string; removedBy: string }
    >({
      query: ({ firmwareId, deviceType, removedBy }) => ({
        url: `/files${getQueryString({ firmware_id: firmwareId, device_type: deviceType, removed_by: removedBy })}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['FirmwareFiles'],
    }),
    createFirmwareRule: builder.mutation<{ success: boolean; message: string }, FirmwareRuleCreateRequest>({
      query: (body) => ({
        url: '/rules',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
      }),
      invalidatesTags: ['FirmwareRules'],
    }),
    deleteFirmwareRule: builder.mutation<{ success: boolean; message: string }, { ruleId: string }>({
      query: ({ ruleId }) => ({
        url: `/rules${getQueryString({ rule_id: ruleId })}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['FirmwareRules'],
    }),
  }),
})

// Export hooks for usage in functional components, which are
// auto-generated based on the defined endpoints
export const {
  useGetManufacturersQuery,
  useGetFirmwareFilesQuery,
  useGetFirmwareRulesQuery,
  useGetFirmwareStatusesQuery,
  useGetRsuFirmwareStatusesQuery,
  useGetObuFirmwareStatusesQuery,
  useUploadFirmwareFileMutation,
  useDeleteFirmwareFileMutation,
  useCreateFirmwareRuleMutation,
  useDeleteFirmwareRuleMutation,
  useLazyGetManufacturersQuery,
  useLazyGetFirmwareFilesQuery,
  useLazyGetFirmwareRulesQuery,
  useLazyGetFirmwareStatusesQuery,
  useLazyGetRsuFirmwareStatusesQuery,
  useLazyGetObuFirmwareStatusesQuery,
} = firmwareApiSlice

// Selectors
const selectRsuFirmwareFilesResult = firmwareApiSlice.endpoints.getFirmwareFiles.select('RSU')
const selectObuFirmwareFilesResult = firmwareApiSlice.endpoints.getFirmwareFiles.select('OBU')
const selectFirmwareRulesResult = firmwareApiSlice.endpoints.getFirmwareRules.select(undefined)
const selectRsuFirmwareStatusesResult = firmwareApiSlice.endpoints.getRsuFirmwareStatuses.select(undefined)
const selectObuFirmwareStatusesResult = firmwareApiSlice.endpoints.getObuFirmwareStatuses.select(undefined)

export const selectRsuFirmwareFiles = createSelector(
  selectRsuFirmwareFilesResult,
  (result) => result.data?.firmware_files ?? []
)

export const selectObuFirmwareFiles = createSelector(
  selectObuFirmwareFilesResult,
  (result) => result.data?.firmware_files ?? []
)

export const selectFirmwareRules = createSelector(
  selectFirmwareRulesResult,
  (result) => result.data?.firmware_rules ?? []
)

export const selectRsuFirmwareStatuses = createSelector(
  selectRsuFirmwareStatusesResult,
  (result) => result.data?.firmware_statuses ?? []
)

export const selectObuFirmwareStatuses = createSelector(
  selectObuFirmwareStatusesResult,
  (result) => result.data?.firmware_statuses ?? []
)

export const selectFirmwareStatusByRsuIp = (rsuIp: string) =>
  createSelector(selectRsuFirmwareStatuses, (firmwareStatuses) =>
    firmwareStatuses.find((status) => status.rsu_ip === rsuIp)
  )

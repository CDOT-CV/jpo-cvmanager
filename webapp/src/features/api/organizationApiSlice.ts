import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'
import { getQueryString } from './intersectionApiSlice'
import { AdminRsu } from '../../models/Rsu'

// Tag type constants
export const RSU_LIST_TAG = 'RsuList' as const
export const AVAILABLE_RSU_LIST_TAG = 'AvailableRsuList' as const
export const RSU_TAG = 'Rsu' as const
export const USER_LIST_TAG = 'UserList' as const
export const AVAILABLE_USER_LIST_TAG = 'AvailableUserList' as const
export const USER_TAG = 'User' as const

export const organizationApiSlice = createApi({
  reducerPath: 'organizationApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${EnvironmentVars.CVIZ_API_SERVER_URL}/organizations`,
    prepareHeaders: (headers, { getState, endpoint }) => {
      const currentState = getState() as RootState
      const token = selectToken(currentState)

      // Endpoint names must match the keys in the endpoints objects below
      const endpointsWithoutToken = []
      if (token && !endpointsWithoutToken.includes(endpoint)) {
        headers.set('Authorization', `Bearer ${token}`)
      }

      return headers
    },
  }),
  tagTypes: [RSU_LIST_TAG, AVAILABLE_RSU_LIST_TAG, RSU_TAG, USER_LIST_TAG, AVAILABLE_USER_LIST_TAG, USER_TAG],
  endpoints: (builder) => ({
    getAllRsuIpsInOrganization: builder.query<string[], string>({
      query: (organization) => {
        return {
          url: 'rsus',
          headers: {
            Organization: organization,
          },
        }
      },
      providesTags: [RSU_LIST_TAG],
    }),
    getRsuOrganizations: builder.query<string[], string>({
      query: (rsuIp) => {
        return {
          url: `rsus/${rsuIp}`,
        }
      },
      providesTags: (result, error, rsuIp) => [{ type: RSU_TAG, id: rsuIp }],
    }),
    getAllRsusNotInOrganization: builder.query<AdminRsu[], string>({
      query: (organization) => {
        return {
          url: 'rsus/available',
          headers: {
            Organization: organization,
          },
        }
      },
      providesTags: [AVAILABLE_RSU_LIST_TAG],
    }),
    getAllUserEmailsInOrganization: builder.query<string[], string>({
      query: (organization) => {
        return {
          url: 'users',
          headers: {
            Organization: organization,
          },
        }
      },
      providesTags: [USER_LIST_TAG],
    }),
    getUserOrganizations: builder.query<string[], string>({
      query: (email) => {
        return {
          url: `users/${email}`,
        }
      },
      providesTags: (result, error, email) => [{ type: USER_TAG, id: email }],
    }),
    getAllUsersNotInOrganization: builder.query<AdminUser[], string>({
      query: (organization) => {
        return {
          url: 'users/available',
          headers: {
            Organization: organization,
          },
        }
      },
      providesTags: [AVAILABLE_USER_LIST_TAG],
    }),
  }),
})

export const {
  useGetAllRsuIpsInOrganizationQuery,
  useLazyGetAllRsuIpsInOrganizationQuery,
  useGetAllRsusNotInOrganizationQuery,
  useLazyGetAllRsusNotInOrganizationQuery,
  useGetRsuOrganizationsQuery,
  useLazyGetRsuOrganizationsQuery,
  useGetAllUserEmailsInOrganizationQuery,
  useLazyGetAllUserEmailsInOrganizationQuery,
  useGetAllUsersNotInOrganizationQuery,
  useLazyGetAllUsersNotInOrganizationQuery,
  useGetUserOrganizationsQuery,
  useLazyGetUserOrganizationsQuery,
} = organizationApiSlice

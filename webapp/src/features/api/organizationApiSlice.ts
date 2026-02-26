import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'
import { getQueryString } from './intersectionApiSlice'
import { AdminRsu } from '../../models/Rsu'

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
  tagTypes: ['RsuList', 'AvailableRsuList', 'Rsu', 'UserList', 'AvailableUserList', 'User'],
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
      providesTags: ['RsuList'],
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
      providesTags: ['AvailableRsuList'],
    }),
    getRsuOrganizations: builder.query<string[], string>({
      query: (rsuIp) => {
        return {
          url: `rsus${getQueryString({
            rsu_ip: rsuIp,
          })}`,
        }
      },
      providesTags: (result, error, rsuIp) => [{ type: 'Rsu', id: rsuIp }],
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
      providesTags: ['UserList'],
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
      providesTags: ['AvailableUserList'],
    }),
    getUserOrganizations: builder.query<string[], string>({
      query: (email) => {
        return {
          url: `users${getQueryString({
            email: email,
          })}`,
        }
      },
      providesTags: (result, error, email) => [{ type: 'User', id: email }],
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

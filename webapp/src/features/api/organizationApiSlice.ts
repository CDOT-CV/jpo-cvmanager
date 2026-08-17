import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'
import { AdminRsu } from '../../models/Rsu'

// Tag type constants
export const ORGANIZATION_API_RSU_LIST_TAG = 'RsuList' as const
export const ORGANIZATION_API_AVAILABLE_RSU_LIST_TAG = 'AvailableRsuList' as const
export const ORGANIZATION_API_RSU_TAG = 'Rsu' as const
export const ORGANIZATION_API_USER_LIST_TAG = 'UserList' as const
export const ORGANIZATION_API_AVAILABLE_USER_LIST_TAG = 'AvailableUserList' as const
export const ORGANIZATION_API_USER_TAG = 'User' as const
export const ORGANIZATION_API_ORG_TAG = 'Organization' as const
export const ORGANIZATION_API_ORG_LIST_ID = 'LIST' as const

export const organizationApiSlice = createApi({
  reducerPath: 'organizationApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${EnvironmentVars.CVIZ_API_SERVER_URL}/organizations`,
    prepareHeaders: (headers, { getState }) => {
      const currentState = getState() as RootState
      const token = selectToken(currentState)

      headers.set('Authorization', `Bearer ${token}`)
      return headers
    },
  }),
  tagTypes: [
    ORGANIZATION_API_RSU_LIST_TAG,
    ORGANIZATION_API_AVAILABLE_RSU_LIST_TAG,
    ORGANIZATION_API_RSU_TAG,
    ORGANIZATION_API_USER_LIST_TAG,
    ORGANIZATION_API_AVAILABLE_USER_LIST_TAG,
    ORGANIZATION_API_USER_TAG,
    ORGANIZATION_API_ORG_TAG,
  ],
  endpoints: (builder) => ({
    getAllRsuIpsInOrganization: builder.query<string[], number>({
      query: (orgId) => {
        return {
          url: 'rsus',
          headers: {
            Organization: orgId?.toString(),
          },
        }
      },
      providesTags: [ORGANIZATION_API_RSU_LIST_TAG],
    }),
    getRsuOrganizations: builder.query<string[], string>({
      query: (rsuIp) => {
        return {
          url: `rsus/${rsuIp}`,
        }
      },
      providesTags: (result, error, rsuIp) => [{ type: ORGANIZATION_API_RSU_TAG, id: rsuIp }],
    }),
    getAllRsusNotInOrganization: builder.query<AdminRsu[], number>({
      query: (orgId) => {
        return {
          url: 'rsus/available',
          headers: {
            Organization: orgId?.toString(),
          },
        }
      },
      providesTags: [ORGANIZATION_API_AVAILABLE_RSU_LIST_TAG],
    }),
    getAllUserEmailsInOrganization: builder.query<string[], number>({
      query: (orgId) => {
        return {
          url: 'users',
          headers: {
            Organization: orgId?.toString(),
          },
        }
      },
      providesTags: [ORGANIZATION_API_USER_LIST_TAG],
    }),
    getUserOrganizations: builder.query<string[], string>({
      query: (email) => {
        return {
          url: `users/${email}`,
        }
      },
      providesTags: (result, error, email) => [{ type: ORGANIZATION_API_USER_TAG, id: email }],
    }),
    getAllUsersNotInOrganization: builder.query<AdminUser[], number>({
      query: (orgId) => {
        return {
          url: 'users/available',
          headers: {
            Organization: orgId?.toString(),
          },
        }
      },
      providesTags: [ORGANIZATION_API_AVAILABLE_USER_LIST_TAG],
    }),
    getOrganizations: builder.query<OrganizationDto[], void>({
      query: () => ({
        url: '',
      }),
      providesTags: (result) =>
        result
          ? [
              ...result.map(({ name }) => ({ type: ORGANIZATION_API_ORG_TAG, id: name })),
              { type: ORGANIZATION_API_ORG_TAG, id: ORGANIZATION_API_ORG_LIST_ID },
            ]
          : [{ type: ORGANIZATION_API_ORG_TAG, id: ORGANIZATION_API_ORG_LIST_ID }],
    }),
    patchOrganization: builder.mutation<OrganizationDto, OrganizationPatch>({
      query: (patch) => ({
        url: '',
        method: 'PATCH',
        body: patch,
      }),
      invalidatesTags: (result, error, vars) => [
        { type: ORGANIZATION_API_ORG_TAG, id: vars.id },
        { type: ORGANIZATION_API_ORG_TAG, id: ORGANIZATION_API_ORG_LIST_ID },
        ...((((vars.rsus_to_add?.length ?? 0 > 0) || vars.rsus_to_remove?.length) ?? 0 > 0)
          ? [
              ORGANIZATION_API_RSU_LIST_TAG,
              ORGANIZATION_API_AVAILABLE_RSU_LIST_TAG,
              ...(vars.rsus_to_add?.map((ip) => ({ type: ORGANIZATION_API_RSU_TAG, id: ip })) ?? []),
              ...(vars.rsus_to_remove?.map((ip) => ({ type: ORGANIZATION_API_RSU_TAG, id: ip })) ?? []),
            ]
          : []),
        ...((((((vars.users_to_add?.length ?? 0 > 0) || vars.users_to_remove?.length) ?? 0 > 0) ||
          vars.users_to_modify?.length) ??
        0 > 0)
          ? [
              ORGANIZATION_API_USER_LIST_TAG,
              ORGANIZATION_API_AVAILABLE_USER_LIST_TAG,
              ...(vars.users_to_add?.map(({ email }) => ({ type: ORGANIZATION_API_USER_TAG, id: email })) ?? []),
              ...(vars.users_to_remove?.map((email) => ({ type: ORGANIZATION_API_USER_TAG, id: email })) ?? []),
              ...(vars.users_to_modify?.map(({ email }) => ({ type: ORGANIZATION_API_USER_TAG, id: email })) ?? []),
            ]
          : []),
      ],
    }),
    deleteOrganization: builder.mutation<void, string>({
      query: (orgName) => ({
        url: `/${orgName}`,
        method: 'DELETE',
      }),
      invalidatesTags: (_, __, orgName) => [
        { type: ORGANIZATION_API_ORG_TAG, id: orgName },
        { type: ORGANIZATION_API_ORG_TAG, id: ORGANIZATION_API_ORG_LIST_ID },
      ],
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
  useGetOrganizationsQuery,
  useLazyGetOrganizationsQuery,
  usePatchOrganizationMutation,
  useDeleteOrganizationMutation,
} = organizationApiSlice

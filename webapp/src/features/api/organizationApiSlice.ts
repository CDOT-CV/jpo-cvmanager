import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { selectToken } from '../../generalSlices/userSlice'
import { AdminRsu } from '../../models/Rsu'
import { RootState } from '../../store'
import { hasItems } from './organizationPatchInvalidation'

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

      if (token) {
        headers.set('Authorization', `Bearer ${token}`)
      }

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
        // Generate list of internal tags to invalidate on organization patch, including RSUs and users if they are added, removed, or modified.
        { type: ORGANIZATION_API_ORG_TAG, id: vars.id },
        { type: ORGANIZATION_API_ORG_TAG, id: ORGANIZATION_API_ORG_LIST_ID },

        // If RSUs list it changed, change org rsu list and available RSU list accordingly.
        ...(hasItems(vars.rsus_to_add) || hasItems(vars.rsus_to_remove)
          ? [
              ORGANIZATION_API_RSU_LIST_TAG,
              ORGANIZATION_API_AVAILABLE_RSU_LIST_TAG,
              ...(vars.rsus_to_add?.map((ip) => ({ type: ORGANIZATION_API_RSU_TAG, id: ip })) ?? []),
              ...(vars.rsus_to_remove?.map((ip) => ({ type: ORGANIZATION_API_RSU_TAG, id: ip })) ?? []),
            ]
          : []),

        // TIM deposit / SNMP monitoring flags apply to all of the organization's RSUs, so refresh the org RSU list.
        ...(vars.tim_deposit !== undefined || vars.snmp_monitoring !== undefined
          ? [ORGANIZATION_API_RSU_LIST_TAG]
          : []),

        // If users list it changed, change org user list and available user list accordingly.
        ...(hasItems(vars.users_to_add) || hasItems(vars.users_to_remove) || hasItems(vars.users_to_modify)
          ? [
              ORGANIZATION_API_USER_LIST_TAG,
              ORGANIZATION_API_AVAILABLE_USER_LIST_TAG,
              ...(vars.users_to_add?.map(({ email }) => ({ type: ORGANIZATION_API_USER_TAG, id: email })) ?? []),
              ...(vars.users_to_remove?.map((email) => ({ type: ORGANIZATION_API_USER_TAG, id: email })) ?? []),
              ...(vars.users_to_modify?.map(({ email }) => ({ type: ORGANIZATION_API_USER_TAG, id: email })) ?? []),
            ]
          : []),

        // Intersection changes are handled in a different slice so cannot be directly invalidated here,
        // those are invalidated in src/features/api/organizationPatchInvalidation.ts via getIntersectionInvalidationTags
      ],
    }),
    deleteOrganization: builder.mutation<void, number>({
      query: (orgId) => ({
        url: `/${orgId}`,
        method: 'DELETE',
      }),
      invalidatesTags: (_, __, orgId) => [
        { type: ORGANIZATION_API_ORG_TAG, id: orgId },
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

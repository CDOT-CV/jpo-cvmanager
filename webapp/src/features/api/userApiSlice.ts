import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'
import { getQueryString } from './intersectionApiSlice'
import { PaginatedQueryParams, PaginatedResponse } from '../../models/pagination'

export interface GetUsersParams extends PaginatedQueryParams {
  organization: string
}

export const userApiSlice = createApi({
  reducerPath: 'userApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${EnvironmentVars.CVIZ_API_SERVER_URL}/users`,
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
  tagTypes: ['User', 'AllowedSelections'],
  endpoints: (builder) => ({
    getUsers: builder.query<PaginatedResponse<AdminUser>, GetUsersParams>({
      query: ({ organization, page = 0, size = 100, sort = 'ip,asc', search = '' }) => {
        return {
          url: `${getQueryString({
            page: page.toString(),
            size: size.toString(),
            sort: sort,
            search: search,
          })}`,
          headers: {
            Organization: organization,
          },
        }
      },
      providesTags: (result) =>
        result
          ? [...result.content.map(({ email }) => ({ type: 'User' as const, id: email })), { type: 'User', id: 'LIST' }]
          : [{ type: 'User', id: 'LIST' }],
    }),
    getUser: builder.query<AdminUser, string>({
      query: (email) => {
        return {
          url: `${getQueryString({
            email,
          })}`,
        }
      },
      providesTags: (result, error, email) => [{ type: 'User', id: email }],
    }),
    getUserAllowedSelections: builder.query<AdminUserAllowedSelections, void>({
      query: () => {
        return {
          url: 'allowed-selections',
        }
      },
      providesTags: (result, error) => ['AllowedSelections'],
    }),
    patchUser: builder.mutation<void, { email: string; patch: Partial<AdminUser> }>({
      query: ({ email, patch }) => ({
        url: `${getQueryString({
          email,
        })}`,
        method: 'PATCH',
        body: { origin_ip: email, ...patch },
      }),
      invalidatesTags: (result, error, { email }) => [
        { type: 'User', id: email },
        { type: 'User', id: 'LIST' },
      ],
    }),
    deleteUser: builder.mutation<void, string>({
      query: (email) => ({
        url: `${getQueryString({
          email,
        })}`,
        method: 'DELETE',
      }),
      invalidatesTags: (result, error, email) => [
        { type: 'User', id: email },
        { type: 'User', id: 'LIST' },
      ],
    }),
    deleteMultipleUsers: builder.mutation<void, string[]>({
      query: (emails) => ({
        url: '/batch',
        method: 'DELETE',
        body: emails,
      }),
      invalidatesTags: (result, error, emails) => [
        ...emails.map((email) => ({ type: 'User' as const, id: email })),
        { type: 'User', id: 'LIST' },
      ],
    }),
  }),
})

export const {
  useGetUsersQuery,
  useLazyGetUsersQuery,
  useGetUserQuery,
  useLazyGetUserQuery,
  useGetUserAllowedSelectionsQuery,
  useLazyGetUserAllowedSelectionsQuery,
  usePatchUserMutation,
  useDeleteUserMutation,
  useDeleteMultipleUsersMutation,
} = userApiSlice

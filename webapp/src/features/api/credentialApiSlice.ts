import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'
import { getQueryString } from './intersectionConfigSlice'
import { rsuApiSlice, RSU_API_RSU_ALLOWED_SELECTIONS_TAG } from './rsuApiSlice'

/** 'rsu' is the SSH credential used to log in to an RSU; 'snmp' is its SNMP credential. */
export type CredentialType = 'rsu' | 'snmp'

/** A credential as listed for one organization. The API never includes the password here. */
export interface CredentialSummary {
  nickname: string
  username: string
  /** Name of the organization that owns the credential and may manage it. */
  ownerOrganization: string
  /** IPs of the RSUs in the listed organization that use the credential. */
  rsuIps: string[]
}

export interface ListCredentialsParams {
  type: CredentialType
  organization: string
}

export interface CreateCredentialParams {
  type: CredentialType
  nickname: string
  username: string
  password: string
  organization: string
}

export interface UpdateCredentialParams {
  type: CredentialType
  nickname: string
  username?: string
  /** Leave undefined or empty to keep the current password. */
  password?: string
}

export interface DeleteCredentialParams {
  type: CredentialType
  nickname: string
}

// Tag type constants
export const CREDENTIAL_API_TAG = 'Credential' as const
const listTag = (type: CredentialType) => ({ type: CREDENTIAL_API_TAG, id: `${type}:LIST` })

/**
 * The RSU add/edit credential dropdowns are cached by the RSU API, so they would not show a created
 * credential (or would still show a deleted one) until that cache expired.
 */
const refreshRsuAllowedSelections = async (
  _arg: unknown,
  { dispatch, queryFulfilled }: { dispatch: (action: any) => unknown; queryFulfilled: Promise<unknown> }
) => {
  try {
    await queryFulfilled
  } catch {
    return
  }
  dispatch(rsuApiSlice.util.invalidateTags([RSU_API_RSU_ALLOWED_SELECTIONS_TAG]))
}

export const credentialApiSlice = createApi({
  reducerPath: 'credentialApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${EnvironmentVars.CVIZ_API_SERVER_URL}/credentials`,
    prepareHeaders: (headers, { getState }) => {
      const token = selectToken(getState() as RootState)

      headers.set('Accept', 'application/json')

      if (token) {
        headers.set('Authorization', `Bearer ${token}`)
      }

      return headers
    },
  }),
  tagTypes: [CREDENTIAL_API_TAG],
  endpoints: (builder) => ({
    // Credentials the organization owns, plus those used by RSUs associated with it
    listCredentials: builder.query<CredentialSummary[], ListCredentialsParams>({
      query: ({ type, organization }) => ({
        url: `/${type}/list${getQueryString({ organization })}`,
      }),
      providesTags: (result, error, { type }) => [listTag(type)],
    }),
    createCredential: builder.mutation<void, CreateCredentialParams>({
      query: ({ type, ...body }) => ({
        url: `/${type}/create`,
        method: 'POST',
        body,
      }),
      // The response echoes the credential including its password; do not keep it in the store
      transformResponse: () => undefined,
      invalidatesTags: (result, error, { type }) => [listTag(type)],
      onQueryStarted: refreshRsuAllowedSelections,
    }),
    updateCredential: builder.mutation<void, UpdateCredentialParams>({
      query: ({ type, nickname, username, password }) => ({
        url: `/${type}/update`,
        method: 'POST',
        // The API stores an empty string as the new password, so a blank password must be omitted
        body: { nickname, ...(username ? { username } : {}), ...(password ? { password } : {}) },
      }),
      transformResponse: () => undefined,
      invalidatesTags: (result, error, { type }) => [listTag(type)],
      onQueryStarted: refreshRsuAllowedSelections,
    }),
    deleteCredential: builder.mutation<void, DeleteCredentialParams>({
      query: ({ type, nickname }) => ({
        url: `/${type}/delete`,
        method: 'POST',
        body: { nickname },
      }),
      invalidatesTags: (result, error, { type }) => [listTag(type)],
      onQueryStarted: refreshRsuAllowedSelections,
    }),
  }),
})

export const {
  useListCredentialsQuery,
  useCreateCredentialMutation,
  useUpdateCredentialMutation,
  useDeleteCredentialMutation,
} = credentialApiSlice

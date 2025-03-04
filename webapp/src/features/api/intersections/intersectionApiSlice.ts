// Need to use the React-specific entry point to import createApi
import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import { createSelector } from '@reduxjs/toolkit'
import EnvironmentVars from '../../../EnvironmentVars'
import { RootState } from '../../../store'
import { selectToken } from '../../../generalSlices/userSlice'
import { selectSelectedIntersectionId } from '../../../generalSlices/intersectionSlice'

const getQueryString = (query_params: Record<string, any>, keys_to_ignore: string[] = []) => {
  // filter out undefined values from query params
  const filteredQueryParams: Record<string, any> = Object.entries(query_params)
    .filter(([_, value]) => value != null)
    .filter(([key]) => !keys_to_ignore.includes(key))
    .reduce((acc, [key, value]) => {
      if (value instanceof Date) {
        acc[key] = value.getTime().toString()
      } else {
        acc[key] = value.toString()
      }
      return acc
    }, {} as Record<string, string>)

  const queryString = new URLSearchParams(filteredQueryParams).toString()
  return `${queryString ? `?${queryString}` : ''}`
}

type IntersectionApiSpatQueryParams = {
  intersectionId: number
  roadRegulatorId: number
  startTime?: Date
  endTime?: Date
  compact?: boolean
}

type IntersectionApiMapQueryParams = {
  intersectionId: number
  roadRegulatorId: number
  startTime?: Date
  endTime?: Date
}

type IntersectionApiBsmQueryParams = {
  vehicleId?: number
  startTime?: Date
  endTime?: Date
  long?: number
  lat?: number
  distance?: number
}

type IntersectionApiBsmMessageCountsQueryParams = {
  intersectionId: number
  startTime?: Date
  endTime?: Date
  latitude?: number
  longitude?: number
  distance?: number
}

type IntersectionApiMessageCountsQueryParams = {
  intersectionId: number
  startTime?: Date
  endTime?: Date
}

// Define a service using a base URL and expected endpoints
export const intersectionApiSlice = createApi({
  reducerPath: 'intersectionApi',
  baseQuery: fetchBaseQuery({
    baseUrl: EnvironmentVars.CVIZ_API_SERVER_URL,
    prepareHeaders: (headers, { getState, endpoint }) => {
      const token = selectToken(getState() as RootState)

      // Specify endpoints that do not require a token or organization. These names must match the keys in the endpoints object below.
      const endpointsWithoutToken = []

      if (token && !endpointsWithoutToken.includes(endpoint)) {
        headers.set('Authorization', `Bearer ${token}`)
      }

      return headers
    },
  }),
  tagTypes: [],
  endpoints: (builder) => ({
    getAllIntersections: builder.query<ProcessedSpat[], number>({
      query: () => {
        return `intersection/list`
      },
    }),
    getLatestSpatMessages: builder.query<
      IntersectionConfig[],
      IntersectionApiSpatQueryParams & {
        signal?: AbortSignal
      }
    >({
      query: (params) => {
        return {
          url: `config/intersection/unique${getQueryString({ ...params, latest: 'true' }, ['signal'])}`,
          signal: params.signal,
        }
      },
    }),
    getSpatMessages: builder.query<
      IntersectionConfig[],
      IntersectionApiSpatQueryParams & {
        signal?: AbortSignal
      }
    >({
      query: (params) => {
        return {
          url: `/spat/json${getQueryString({ ...params, latest: false }, ['signal'])}`,
          signal: params.signal,
        }
      },
    }),
    getMapMessages: builder.query<
      IntersectionConfig[],
      IntersectionApiMapQueryParams & {
        signal?: AbortSignal
      }
    >({
      query: (params) => {
        return {
          url: `/map/json${getQueryString({ ...params, latest: false }, ['signal'])}`,
          signal: params.signal,
        }
      },
    }),
    getBsmMessages: builder.query<
      IntersectionConfig[],
      IntersectionApiBsmQueryParams & {
        signal?: AbortSignal
      }
    >({
      query: (params) => {
        return {
          url: `/bsm/json${getQueryString({ ...params, latest: false }, ['signal'])}`,
          signal: params.signal,
        }
      },
    }),
    getMessageCountBsm: builder.query<
      number,
      IntersectionApiBsmMessageCountsQueryParams & {
        signal?: AbortSignal
      }
    >({
      query: (params) => {
        return {
          url: `/bsm/count${getQueryString(params, ['signal'])}`,
          signal: params.signal,
        }
      },
    }),
    getMessageCount: builder.query<
      number,
      IntersectionApiMessageCountsQueryParams & {
        messageType: string
        signal?: AbortSignal
      }
    >({
      query: (params) => {
        return {
          url: `/${params.messageType}/count${getQueryString(params, ['signal', 'messageType'])}`,
          signal: params.signal,
        }
      },
    }),
  }),
})

// Export hooks for usage in functional components, which are
// auto-generated based on the defined endpoints
export const {
  useGetGeneralParametersQuery,
  useGetIntersectionParametersQuery,
  useUpdateDefaultParameterMutation,
  useUpdateIntersectionParameterMutation,
  useRemoveOverriddenParameterMutation,

  useLazyGetGeneralParametersQuery,
  useLazyGetIntersectionParametersQuery,
} = intersectionApiSlice

const intersectionParameters = (intersectionId: number) =>
  intersectionApiSlice.endpoints.getIntersectionParameters.select(intersectionId)
const generalParameters = intersectionApiSlice.endpoints.getGeneralParameters.select(undefined)

const selectIntersectionParametersById = (intersectionId: number) =>
  createSelector(intersectionParameters(intersectionId), (result) => result.data ?? [])

const selectGeneralParameters = createSelector(generalParameters, (result) => result.data ?? [])

export const selectParameter = (key: string) =>
  createSelector(
    (state: RootState) => selectSelectedIntersectionId(state),
    (state: RootState) => selectIntersectionParametersById(selectSelectedIntersectionId(state))(state),
    selectGeneralParameters,
    (_, intersectionParameters, generalParameters) => filterParameter(key, intersectionParameters, generalParameters)
  )

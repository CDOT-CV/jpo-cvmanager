// Need to use the React-specific entry point to import createApi
import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { combineUrlPaths } from '../../apis/intersections/api-helper-cviz'
import { getQueryString } from './intersectionApiSlice'
import { EmailSubscription, EmailSubscriptionGetResponse } from '../../models/email-subscriptions'

// Define a service using a base URL and expected endpoints
export const unsubscriptionSlice = createApi({
  reducerPath: 'userNotification',
  baseQuery: fetchBaseQuery({
    baseUrl: combineUrlPaths(EnvironmentVars.CVIZ_API_SERVER_URL, '/users'),
  }),
  tagTypes: ['userNotifications'],
  endpoints: (builder) => ({
    getEmailSubscriptions: builder.query<EmailSubscriptionGetResponse, string>({
      query: (token) => {
        return `/email-subscriptions${getQueryString({
          token: token,
        })}`
      },
      providesTags: ['userNotifications'],
      transformResponse: (response: any) => response as EmailSubscriptionGetResponse,
    }),
    updateEmailSubscriptions: builder.mutation<null, { token: string; subscriptions: EmailSubscription[] }>({
      query: ({ token, subscriptions }) => ({
        url: `/email-subscriptions${getQueryString({
          token: token,
        })}`,
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: subscriptions,
      }),
      invalidatesTags: ['userNotifications'],
    }),
  }),
})

export const { useGetEmailSubscriptionsQuery, useUpdateEmailSubscriptionsMutation } = unsubscriptionSlice

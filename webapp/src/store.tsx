import { configureStore } from '@reduxjs/toolkit'
import rsuReducer from './generalSlices/rsuSlice'
import userReducer from './generalSlices/userSlice'
import wzdxReducer from './generalSlices/wzdxSlice'
import configReducer from './generalSlices/configSlice'
import intersectionReducer from './generalSlices/intersectionSlice'
import adminAddOrganizationReducer from './features/adminAddOrganization/adminAddOrganizationSlice'
import adminAddIntersectionReducer from './features/adminAddIntersection/adminAddIntersectionSlice'
import adminEditIntersectionReducer from './features/adminEditIntersection/adminEditIntersectionSlice'
import adminIntersectionTabReducer from './features/adminIntersectionTab/adminIntersectionTabSlice'
import adminNotificationTabReducer from './features/adminNotificationTab/adminNotificationTabSlice'
import adminAddNotificationReducer from './features/adminAddNotification/adminAddNotificationSlice'
import adminEditNotificationReducer from './features/adminEditNotification/adminEditNotificationSlice'
import menuReducer from './features/menu/menuSlice'
import asn1DecoderSlice from './features/intersections/decoder/asn1-decoder-slice'
import intersectionMapReducer from './features/intersections/map/map-slice'
import intersectionMapLayerStyleReducer from './features/intersections/map/map-layer-style-slice'
import dataSelectorReducer from './features/intersections/data-selector/dataSelectorSlice'
import { emailApiSlice } from './features/api/emailApiSlice'
import { intersectionApiSlice } from './features/api/intersectionApiSlice'
import { organizationApiSlice } from './features/api/organizationApiSlice'
import { rsuCountsApiSlice } from './features/api/rsuCountsApiSlice'
import { RSU_API_RSU_LIST_ID, RSU_API_RSU_TAG, rsuApiSlice } from './features/api/rsuApiSlice'
import { scmsApiSlice } from './features/api/scmsApiSlice'
import { USER_API_USER_LIST_ID, USER_API_USER_TAG, userApiSlice } from './features/api/userApiSlice'
import { ADMIN_INTERSECTION_LIST_ID, adminIntersectionApiSlice } from './features/api/adminIntersectionApiSlice'
import { ADMIN_INTERSECTION_AVAILABLE_LIST_ID, ADMIN_INTERSECTION_TAG } from './features/api/adminIntersectionApiSlice'
import mapSliceReducer from './pages/mapSlice'
import timeSyncReducer from './generalSlices/timeSyncSlice'
import haasSliceReducer from './generalSlices/haasAlertSlice'

export const setupStore = (preloadedState?: Partial<any>) => {
  return configureStore({
    reducer: {
      rsu: rsuReducer,
      user: userReducer,
      wzdx: wzdxReducer,
      config: configReducer,
      intersection: intersectionReducer,
      adminAddOrganization: adminAddOrganizationReducer,
      adminAddIntersection: adminAddIntersectionReducer,
      adminEditIntersection: adminEditIntersectionReducer,
      adminIntersectionTab: adminIntersectionTabReducer,
      adminNotificationTab: adminNotificationTabReducer,
      adminAddNotification: adminAddNotificationReducer,
      adminEditNotification: adminEditNotificationReducer,
      menu: menuReducer,
      intersectionMap: intersectionMapReducer,
      intersectionMapLayerStyle: intersectionMapLayerStyleReducer,
      dataSelector: dataSelectorReducer,
      map: mapSliceReducer,
      asn1Decoder: asn1DecoderSlice,
      timeSync: timeSyncReducer,
      haas: haasSliceReducer,
      [emailApiSlice.reducerPath]: emailApiSlice.reducer,
      [intersectionApiSlice.reducerPath]: intersectionApiSlice.reducer,
      [organizationApiSlice.reducerPath]: organizationApiSlice.reducer,
      [rsuCountsApiSlice.reducerPath]: rsuCountsApiSlice.reducer,
      [rsuApiSlice.reducerPath]: rsuApiSlice.reducer,
      [scmsApiSlice.reducerPath]: scmsApiSlice.reducer,
      [userApiSlice.reducerPath]: userApiSlice.reducer,
      [adminIntersectionApiSlice.reducerPath]: adminIntersectionApiSlice.reducer,
    },
    preloadedState,
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware({
        thunk: true,
        serializableCheck: false,
        immutableCheck: false,
      })
        .concat(emailApiSlice.middleware)
        .concat(intersectionApiSlice.middleware)
        .concat(organizationApiSlice.middleware)
        .concat(rsuCountsApiSlice.middleware)
        .concat(rsuApiSlice.middleware)
        .concat(scmsApiSlice.middleware)
        .concat(userApiSlice.middleware)
        .concat(adminIntersectionApiSlice.middleware)
        .concat((api) => (next) => (action) => {
          const result = next(action)
          // After any patchOrganization succeeds, invalidate adminIntersectionApiSlice
          // caches so the available-intersections dropdown and org intersection lists refresh.
          if (organizationApiSlice.endpoints.patchOrganization.matchFulfilled(action)) {
            if (
              ((action.meta.arg.originalArgs.intersections_to_add?.length ?? 0 > 0) ||
                action.meta.arg.originalArgs.intersections_to_remove?.length) ??
              0 > 0
            ) {
              console.log(
                'Invalidating Intersection Tag',
                ...(action.meta.arg.originalArgs.intersections_to_add?.map((i) => ({
                  type: ADMIN_INTERSECTION_TAG,
                  id: i,
                })) ?? [])
              )
              api.dispatch(
                adminIntersectionApiSlice.util.invalidateTags([
                  // List of intersections not in org
                  { type: ADMIN_INTERSECTION_TAG, id: ADMIN_INTERSECTION_LIST_ID },
                  { type: ADMIN_INTERSECTION_TAG, id: ADMIN_INTERSECTION_AVAILABLE_LIST_ID },
                  // Intersection info (includes organizations list)
                  ...(action.meta.arg.originalArgs.intersections_to_add?.map((i) => ({
                    type: ADMIN_INTERSECTION_TAG,
                    id: i,
                  })) ?? []),
                  // Intersection info (includes organizations list)
                  ...(action.meta.arg.originalArgs.intersections_to_remove?.map((i) => ({
                    type: ADMIN_INTERSECTION_TAG,
                    id: i,
                  })) ?? []),
                ])
              )
            }
            if (
              ((((action.meta.arg.originalArgs.users_to_add?.length ?? 0 > 0) ||
                action.meta.arg.originalArgs.users_to_modify?.length) ??
                0 > 0) ||
                action.meta.arg.originalArgs.users_to_remove?.length) ??
              0 > 0
            ) {
              api.dispatch(
                userApiSlice.util.invalidateTags([
                  { type: USER_API_USER_TAG, id: USER_API_USER_LIST_ID },
                  // User info (includes organizations and roles list)
                  ...(action.meta.arg.originalArgs.users_to_add?.map((u) => ({
                    type: USER_API_USER_TAG,
                    id: u.email,
                  })) ?? []),
                  // User info (includes organizations and roles list)
                  ...(action.meta.arg.originalArgs.users_to_modify?.map((u) => ({
                    type: USER_API_USER_TAG,
                    id: u.email,
                  })) ?? []),
                  // User info (includes organizations and roles list)
                  ...(action.meta.arg.originalArgs.users_to_remove?.map((u) => ({
                    type: USER_API_USER_TAG,
                    id: u,
                  })) ?? []),
                ])
              )
            }
            if (
              ((action.meta.arg.originalArgs.rsus_to_add?.length ?? 0 > 0) ||
                action.meta.arg.originalArgs.rsus_to_remove?.length) ??
              0 > 0
            ) {
              api.dispatch(
                rsuApiSlice.util.invalidateTags([
                  { type: RSU_API_RSU_TAG, id: RSU_API_RSU_LIST_ID },
                  // Rsu info (includes organizations list)
                  ...(action.meta.arg.originalArgs.rsus_to_add?.map((r) => ({ type: RSU_API_RSU_TAG, id: r })) ?? []),
                  // Rsu info (includes organizations list)
                  ...(action.meta.arg.originalArgs.rsus_to_remove?.map((r) => ({
                    type: RSU_API_RSU_TAG,
                    id: r,
                  })) ?? []),
                ])
              )
            }
          }
          return result
        }),
    devTools: true,
  })
}

type AppStore = ReturnType<typeof setupStore>
export type AppState = ReturnType<AppStore['getState']>

export type AppDispatch = ReturnType<typeof setupStore>['dispatch']

export type RootState = ReturnType<ReturnType<typeof setupStore>['getState']>

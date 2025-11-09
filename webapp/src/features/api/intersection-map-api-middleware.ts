import { createListenerMiddleware } from '@reduxjs/toolkit'
import { intersectionMapApiSlice } from './intersectionMapApiSlice'
import { setCurrentSsmData, setCurrentSrmData } from '../intersections/map/map-slice'

export const intersectionMapApiMiddleware = createListenerMiddleware()

// Subscribe to SSM query updates
intersectionMapApiMiddleware.startListening({
  matcher: intersectionMapApiSlice.endpoints.getSsmWithinTimeWindow.matchFulfilled,
  effect: (action, listenerApi) => {
    const { payload: ssmData, meta } = action
    const { arg: queryArgs } = meta

    listenerApi.dispatch(setCurrentSsmData(ssmData))
  },
})

// Subscribe to SRM query updates
intersectionMapApiMiddleware.startListening({
  matcher: intersectionMapApiSlice.endpoints.getSrmWithinTimeWindow.matchFulfilled,
  effect: (action, listenerApi) => {
    const { payload: srmData, meta } = action
    const { arg: queryArgs } = meta

    listenerApi.dispatch(setCurrentSrmData(srmData))
  },
})

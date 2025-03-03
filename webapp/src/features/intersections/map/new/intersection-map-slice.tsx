import { createSlice, PayloadAction } from '@reduxjs/toolkit'
import { RootState } from '../../../../store'
import { MapRef, ViewState } from 'react-map-gl'
import React from 'react'

export type MAP_LAYERS =
  | 'map-message'
  | 'map-message-labels'
  | 'connecting-lanes'
  | 'connecting-lanes-labels'
  | 'invalid-lane-collection'
  | 'bsm'
  | 'signal-states'

const initialState = {
  mapRef: React.createRef() as React.MutableRefObject<MapRef>,
  layersVisible: {
    'map-message': false,
    'map-message-labels': false,
    'connecting-lanes': false,
    'connecting-lanes-labels': false,
    'invalid-lane-collection': false,
    bsm: false,
    'signal-states': false,
  } as Record<MAP_LAYERS, boolean>,
  allInteractiveLayerIds: ['map-message', 'connecting-lanes', 'signal-states', 'bsm'] as string[],
  viewState: {
    latitude: 39.587905,
    longitude: -105.0907089,
    zoom: 19,
  } as Partial<ViewState>,
  currentMapMessages: { type: 'FeatureCollection', features: [] } as { [key: number]: ProcessedMap },
  currentSpatMessages: { type: 'FeatureCollection', features: [] } as { [key: number]: ProcessedSpat },
  currentNotificationMessages: [] as MessageMonitor.Notification[],
  currentBsmMessages: { type: 'FeatureCollection', features: [] } as BsmFeatureCollection,
  showPopupOnHover: false as boolean,
  hoveredFeature: null as any,
  selectedFeature: null as any,
  cursor: 'default' as string,
}

export const intersectionMapSlice = createSlice({
  name: 'intersectionMap',
  initialState: {
    loading: false,
    value: initialState,
  },
  reducers: {
    setMapRef: (state, action: PayloadAction<React.MutableRefObject<MapRef>>) => {
      state.value.mapRef = action.payload
    },
    setViewState: (state, action: PayloadAction<Partial<ViewState>>) => {
      state.value.viewState = action.payload
    },
    onMapClick: (state, action: PayloadAction<{ point: mapboxgl.Point; lngLat: mapboxgl.LngLat }>) => {
      const features = state.value.mapRef.current.queryRenderedFeatures(action.payload.point, {
        // layers: state.value.allInteractiveLayerIds,
      })
      const feature = features?.[0]
      if (feature && state.value.allInteractiveLayerIds.includes(feature.layer.id as MAP_LAYERS)) {
        state.value.selectedFeature = { clickedLocation: action.payload.lngLat, feature }
      } else {
        state.value.selectedFeature = undefined
      }
    },
    onMapMouseMove: (
      state,
      action: PayloadAction<{ features: mapboxgl.MapboxGeoJSONFeature[] | undefined; lngLat: mapboxgl.LngLat }>
    ) => {
      const feature = action.payload.features?.[0]
      if (feature && state.value.allInteractiveLayerIds.includes(feature.layer.id as MAP_LAYERS)) {
        state.value.hoveredFeature = { clickedLocation: action.payload.lngLat, feature }
      }
    },
    onMapMouseEnter: (
      state,
      action: PayloadAction<{ features: mapboxgl.MapboxGeoJSONFeature[] | undefined; lngLat: mapboxgl.LngLat }>
    ) => {
      state.value.cursor = 'pointer'
      const feature = action.payload.features?.[0]
      if (feature && state.value.allInteractiveLayerIds.includes(feature.layer.id as MAP_LAYERS)) {
        state.value.hoveredFeature = { clickedLocation: action.payload.lngLat, feature }
      } else {
        state.value.hoveredFeature = undefined
      }
    },
    onMapMouseLeave: (state) => {
      state.value.cursor = ''
      state.value.hoveredFeature = undefined
    },
    clearSelectedFeature: (state) => {
      state.value.selectedFeature = undefined
    },
    clearHoveredFeature: (state) => {
      state.value.hoveredFeature = undefined
    },
  },
  extraReducers: (builder) => {
    builder
  },
})

export const selectLoading = (state: RootState) => state.intersectionMap.loading

export const selectMapRef = (state: RootState) => state.intersectionMap.value.mapRef
export const selectLayersVisible = (state: RootState) => state.intersectionMap.value.layersVisible
export const selectAllInteractiveLayerIds = (state: RootState) => state.intersectionMap.value.allInteractiveLayerIds
export const selectViewState = (state: RootState) => state.intersectionMap.value.viewState
export const selectCurrentMapMessages = (state: RootState) => state.intersectionMap.value.currentMapMessages
export const selectCurrentSpatMessages = (state: RootState) => state.intersectionMap.value.currentSpatMessages
export const selectCurrentBsmMessages = (state: RootState) => state.intersectionMap.value.currentBsmMessages
export const selectCurrentNotificationMessages = (state: RootState) =>
  state.intersectionMap.value.currentNotificationMessages
export const selectShowPopupOnHover = (state: RootState) => state.intersectionMap.value.showPopupOnHover
export const selectHoveredFeature = (state: RootState) => state.intersectionMap.value.hoveredFeature
export const selectSelectedFeature = (state: RootState) => state.intersectionMap.value.selectedFeature
export const selectCursor = (state: RootState) => state.intersectionMap.value.cursor

export const {
  setMapRef,
  setViewState,
  onMapClick,
  onMapMouseMove,
  onMapMouseEnter,
  onMapMouseLeave,
  clearSelectedFeature,
  clearHoveredFeature,
} = intersectionMapSlice.actions

export default intersectionMapSlice.reducer

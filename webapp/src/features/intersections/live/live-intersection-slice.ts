import { createSlice, PayloadAction } from '@reduxjs/toolkit'
import { RootState } from '../../../store'
import LiveIntersectionApi from '../../../apis/intersections/live/live-intersection-api'

const initialState = {
  maps: {} as { [key: number]: ProcessedMap },
  spats: {} as { [key: number]: ProcessedSpat },
  bsms: {} as { [key: number]: BsmFeature },
}

export const startBsmSamples = (n: number) => {
  for (let i = 0; i < n; i++) {
    LiveIntersectionApi.startMockedBsmData(i.toString(), 100)
  }
}

const liveIntersectionSlice = createSlice({
  name: 'liveIntersection',
  initialState,
  reducers: {
    addMapMessage: (state, action: PayloadAction<ProcessedMap>) => {
      state.maps[action.payload.properties.intersectionId] = action.payload
    },
    addMapMultiple: (state, action: PayloadAction<ProcessedMap[]>) => {
      action.payload.forEach((map) => {
        state.maps[map.properties.intersectionId] = map
      })
    },
    addSpatData: (state, action: PayloadAction<ProcessedSpat>) => {
      state.spats[action.payload.intersectionId] = action.payload
    },
    addSpatMultiple: (state, action: PayloadAction<ProcessedSpat[]>) => {
      action.payload.forEach((spat) => {
        state.spats[spat.intersectionId] = spat
      })
    },
    addBsmData: (state, action: PayloadAction<BsmFeature>) => {
      state.bsms[action.payload.properties.id] = action.payload
    },
    addBsmMultiple: (state, action: PayloadAction<BsmFeature[]>) => {
      action.payload.forEach((bsm) => {
        state.bsms[bsm.properties.id] = bsm
      })
    },
  },
})

export const { addMapMessage, addMapMultiple, addSpatData, addSpatMultiple, addBsmData, addBsmMultiple } =
  liveIntersectionSlice.actions
export default liveIntersectionSlice.reducer

export const selectLiveMapData = (state: RootState) => Object.values(state.liveIntersection.maps)
export const selectLiveSpatData = (state: RootState) => Object.values(state.liveIntersection.spats)
export const selectLiveBsmData = (state: RootState) => Object.values(state.liveIntersection.bsms)

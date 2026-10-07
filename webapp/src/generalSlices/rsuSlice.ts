import { createAsyncThunk, createSlice, PayloadAction } from '@reduxjs/toolkit'
import RsuApi from '../apis/rsu-api'
import {
  RsuInfo,
  RsuMapInfo,
  RsuMapInfoIpList,
} from '../models/RsuApi'
import { RootState } from '../store'
import { selectToken, selectOrganizationName } from './userSlice'
import { MessageType } from '../models/MessageTypes'
import { DateTime } from 'luxon'

const currentDate = DateTime.local()

const initialState = {
  selectedRsu: null as RsuInfo,
  rsuData: [] as RsuInfo[],
  geoMsgType: 'BSM' as MessageType | undefined,
  rsuMapData: {} as RsuMapInfo['geojson'],
  mapList: [] as RsuMapInfoIpList,
  mapDate: '' as RsuMapInfo['date'],
  displayMap: false,
  // TODO: lowering the default start date to 3 hours ago to reduce the number of messages returned
  // this is a temporary fix until the Processed BSM messages in mongo   are stored without duplicates
  geoMsgStart: currentDate.minus({ hours: 3 }).toString(),
  geoMsgEnd: currentDate.toString(),
  addGeoMsgPoint: false,
  geoMsgCoordinates: [] as number[][],
  geoMsgData: [] as Array<GeoJSON.Feature<GeoJSON.Geometry>>,
  geoMsgDateError: false,
  geoMsgFilter: false,
  geoMsgFilterStep: 60,
  geoMsgFilterOffset: 0,
}

export const getRsuData = createAsyncThunk(
  'rsu/getRsuData',
  async (_, { getState }) => {
    const currentState = getState() as RootState
    const token = selectToken(currentState)
    const organization = selectOrganizationName(currentState)
    const rsuInfo = await RsuApi.getRsuInfo(token, organization)

    return rsuInfo.rsuList
  },
  {
    condition: (_, { getState }) => selectToken(getState() as RootState) != undefined,
  }
)

export const _getRsuInfo = createAsyncThunk('rsu/_getRsuInfo', async (_, { getState }) => {
  const currentState = getState() as RootState
  const token = selectToken(currentState)
  const organization = selectOrganizationName(currentState)
  const rsuInfo = await RsuApi.getRsuInfo(token, organization)
  const rsuData = rsuInfo.rsuList

  return rsuData
})

export const rsuSlice = createSlice({
  name: 'rsu',
  initialState: {
    loading: false,
    currentRequestId: null as string | null,
    value: initialState,
  },
  reducers: {
    selectRsu: (state, action: PayloadAction<RsuInfo>) => {
      state.value.selectedRsu = action.payload
    },
    toggleMapDisplay: (state) => {
      state.value.displayMap = !state.value.displayMap
    },
    clearGeoMsg: (state) => {
      state.value.geoMsgCoordinates = []
      state.value.geoMsgData = []
      state.value.geoMsgDateError = false
    },
    toggleGeoMsgPointSelect: (state) => {
      state.value.addGeoMsgPoint = !state.value.addGeoMsgPoint
    },
    updateGeoMsgPoints: (state, action: PayloadAction<number[][]>) => {
      state.value.geoMsgCoordinates = action.payload
    },
    updateGeoMsgDate: (state, action: PayloadAction<{ type: 'start' | 'end'; date: string }>) => {
      if (action.payload.type === 'start') state.value.geoMsgStart = action.payload.date
      else state.value.geoMsgEnd = action.payload.date
    },
    triggerGeoMsgDateError: (state) => {
      state.value.geoMsgDateError = true
    },
    changeGeoMsgType: (state, action: PayloadAction<MessageType | undefined>) => {
      state.value.geoMsgType = action.payload
    },
    setGeoMsgFilter: (state, action: PayloadAction<boolean>) => {
      state.value.geoMsgFilter = action.payload
    },
    setGeoMsgFilterStep: (state, action: PayloadAction<number>) => {
      state.value.geoMsgFilterStep = action.payload
    },
    setGeoMsgFilterOffset: (state, action: PayloadAction<number>) => {
      state.value.geoMsgFilterOffset = action.payload
    },
    setGeoMsgDataResult: (state, action: PayloadAction<Array<GeoJSON.Feature<GeoJSON.Geometry>>>) => {
      state.value.geoMsgData = action.payload
      state.value.geoMsgFilter = true
      state.value.geoMsgFilterStep = 60
      state.value.geoMsgFilterOffset = 0
    },
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(getRsuData.pending, (state, action) => {
        state.loading = true
        state.currentRequestId = action.meta.requestId
        state.value.rsuData = []
      })
      .addCase(getRsuData.fulfilled, (state, action) => {
        if (state.currentRequestId !== action.meta.requestId) return
        state.value.rsuData = action.payload
        state.loading = false
        state.currentRequestId = null
      })
      .addCase(getRsuData.rejected, (state, action) => {
        if (state.currentRequestId !== action.meta.requestId) return
        state.loading = false
        state.currentRequestId = null
      })
      .addCase(_getRsuInfo.fulfilled, (state, action) => {
        state.value.rsuData = action.payload
      })
  },
})

export const selectLoading = (state: RootState) => state.rsu.loading

export const selectSelectedRsu = (state: RootState) => state.rsu.value.selectedRsu
export const selectRsuManufacturer = (state: RootState) => state.rsu.value.selectedRsu?.properties?.manufacturer_name
export const selectRsuIpv4 = (state: RootState) => state.rsu.value.selectedRsu?.properties?.ipv4_address
export const selectRsuPrimaryRoute = (state: RootState) => state.rsu.value.selectedRsu?.properties?.primary_route
export const selectRsuData = (state: RootState) => state.rsu.value.rsuData
export const selectGeoMsgType = (state: RootState) => state.rsu.value.geoMsgType
export const selectRsuMapData = (state: RootState) => state.rsu.value.rsuMapData
export const selectMapList = (state: RootState) => state.rsu.value.mapList
export const selectMapDate = (state: RootState) => state.rsu.value.mapDate
export const selectDisplayMap = (state: RootState) => state.rsu.value.displayMap
export const selectGeoMsgStart = (state: RootState) => state.rsu.value.geoMsgStart
export const selectGeoMsgEnd = (state: RootState) => state.rsu.value.geoMsgEnd
export const selectAddGeoMsgPoint = (state: RootState) => state.rsu.value.addGeoMsgPoint
export const selectGeoMsgCoordinates = (state: RootState) => state.rsu.value.geoMsgCoordinates
export const selectGeoMsgData = (state: RootState) => state.rsu.value.geoMsgData
export const selectGeoMsgDateError = (state: RootState) => state.rsu.value.geoMsgDateError
export const selectGeoMsgFilter = (state: RootState) => state.rsu.value.geoMsgFilter
export const selectGeoMsgFilterStep = (state: RootState) => state.rsu.value.geoMsgFilterStep
export const selectGeoMsgFilterOffset = (state: RootState) => state.rsu.value.geoMsgFilterOffset

export const {
  selectRsu,
  toggleMapDisplay,
  clearGeoMsg,
  toggleGeoMsgPointSelect,
  updateGeoMsgPoints,
  updateGeoMsgDate,
  triggerGeoMsgDateError,
  changeGeoMsgType,
  setGeoMsgFilter,
  setGeoMsgFilterStep,
  setGeoMsgFilterOffset,
  setGeoMsgDataResult,
  setLoading,
} = rsuSlice.actions

export default rsuSlice.reducer

import { PayloadAction, createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { selectToken } from '../../../generalSlices/userSlice'
import { v4 as uuidv4 } from 'uuid'
import { RootState } from '../../../store'
import DecoderApi from '../../../apis/intersections/decoder-api'
import { getTimestamp } from '../map/map-component'
import {
  pullInitialData,
  resetMapView,
  selectInitialSourceDataType,
  selectIntersectionId,
  selectLoadOnNull,
  selectRoadRegulatorId,
  selectSourceDataType,
  setDecoderModeEnabled,
  setMapProps,
  handleNewMapMessageData,
  handleImportedMapMessageData,
} from '../map/map-slice'

import { 
  parseMapSignalGroups,
  parseBsmToGeojson,
} from '../map/utilities/message-utils'

const initialState = {
  dialogOpen: false,
  pcapData: [],
  decodedJsonData: [],
  pcapDataStats: {
    totalCount: 0,
    firstTimestamp: 0,
    lastTimestamp: 0,
    mapCount: 0,
    uniqueMapCount: 0,
    spatCount: 0,
    bsmCount: 0,
    ssmCount: 0,
    srmCount: 0,
    unknownCount: 0
  },
  uniqueMaps: [] as TimestampedOdeData[],
  selectedMap: {} as TimestampedOdeData,
  importedData: {} as {
    mapData: ProcessedMap[],
    bsmData: OdeBsmData[],
    spatData: ProcessedSpat[],
    notificationData: any
  }
}



export const onPcapFileUploaded = createAsyncThunk(
  'pcapDecoder/onPcapFileUploaded',
  async (contents: ArrayBuffer, { getState, dispatch }) => {
    console.log("onPcapFileUploaded")
    const currentState = getState() as RootState
    const authToken = selectToken(currentState)!
    const response = await submitPcapDecoderRequest(authToken, contents)
    
    // Start decode all to JSON asynchroously.  Don't await because it is slower.
    // TODO: Grey out the download button until this is done
    dispatch(decodeAllToJson(response))

    dispatch(decodeUniqueMaps(response))
    
    return response
  }
)


const submitPcapDecoderRequest = (authToken: string, contents: ArrayBuffer) => {
  console.log("submitPcapDecoderRequest")  
  return DecoderApi.submitPcapDecodeRequest(authToken, contents);
}

export const decodeAllToJson = createAsyncThunk(
  'pcapDecoder/decodeAllToJson',
  async (hexData: any[], {getState, dispatch}) => {
    console.log("decodeAlltoJson")
    const currentState = getState() as RootState
    const authToken = selectToken(currentState)!
    const response = await DecoderApi.submitBatchDecodeRequest(authToken, hexData)
    return response
  }
)

export const decodeUniqueMaps = createAsyncThunk(
  'pcapDecoder/decodeUniqueMaps',
  async (hexData: any[], {getState, dispatch}) => {
    console.log("decodeUniqueMaps")
    const pcapDataArr = Object.values(hexData)
    const allMaps = pcapDataArr.filter(item => item.type === 'MAP')
    const uniqueMapHexSet = new Set()
    const uniqueMaps = []
    allMaps.forEach(item => {
      if (!uniqueMapHexSet.has(item.hex)) {
        uniqueMapHexSet.add(item.hex)
        uniqueMaps.push(item)
      }
    })
    console.log(uniqueMaps)
    const currentState = getState() as RootState
    const authToken = selectToken(currentState)!
    const response = await DecoderApi.submitBatchDecodeRequest(authToken, uniqueMaps)
    return response
  }
)


export const pcapDecoderModeToggled = createAsyncThunk(
  'pcapDecoder/pcapDecoderModeToggled',
  async (enabled: boolean, { getState, dispatch }) => {

    console.log('pcapDecoder/pcapSecoderModeToggled')

    const initialSourceDataType = selectInitialSourceDataType(getState() as RootState)
    const intersectionId = selectIntersectionId(getState() as RootState)
    const roadRegulatorId = selectRoadRegulatorId(getState() as RootState)
    const loadOnNull = selectLoadOnNull(getState() as RootState)

    if (enabled) {
      dispatch(resetMapView())
      dispatch(setDecoderModeEnabled(true))
    } else {
      dispatch(resetMapView())
      dispatch(setDecoderModeEnabled(false))
      dispatch(
        setMapProps({
          sourceData: {
            map: [],
            spat: [],
            bsm: [],
          },
          sourceDataType: initialSourceDataType,
          intersectionId,
          roadRegulatorId,
          loadOnNull,
        })
      )
      dispatch(pullInitialData())
    }
  }
)

export const updateMap = createAsyncThunk(
  'pcapDecoder/updateMap',
  async(_, { getState, dispatch }) => {
    console.log("pcapDecoder/updateMap")
    const timestampedData = selectSelectedMap(getState() as RootState)
    const selectedMap = timestampedData?.odeData as ProcessedMap
    dispatch(handleNewMapMessageData({
      mapData: selectedMap, 
      connectingLanes: selectedMap?.connectingLanesFeatureCollection, 
      mapSignalGroups: parseMapSignalGroups(selectedMap), 
      mapTime: timestampedData?.timestamp ?? Date.now()
    }))
  }
)

export const loadAllData = createAsyncThunk(
  'pcapDecoder/loadAllData',
  async(_, { getState, dispatch }) => {
    console.log("pcapDecoder/loadAllData")
    const importedData = selectImportedData(getState() as RootState)
    dispatch(handleImportedMapMessageData(importedData))
  }
)

export const pcapDecoderSlice = createSlice({
  name: 'pcapDecoder',
  initialState: {
    loading: false,
    value: initialState,
  },
  reducers: {
    setPcapDecoderDialogOpen: (state, action: PayloadAction<boolean>) => {
        console.log("setPcapDecoderDialogOpen: " + action.payload)
        state.value.dialogOpen = action.payload
    },
    onMapSelected: (state, action: PayloadAction<any>) => {
      const intersectionId = action.payload
      console.log("onMapSelected " + intersectionId)
      state.value.selectedMap = state.value.uniqueMaps.find(function(timestampedData) {
        const aMap = timestampedData.odeData as ProcessedMap
        return aMap.properties.intersectionId == intersectionId
      })
      console.log("selectedMap: ")
      console.log(state.value.selectedMap)
    }
  },
  extraReducers: (builder) => {
    builder.addCase(onPcapFileUploaded.fulfilled, (state, action) => {
      console.debug("onPcapFileUploaded.fulfilled reducer")
      try {
        if (action.payload) {
          state.value.pcapData = action.payload

          const pcapDataArr = Object.values(state.value.pcapData)
          const firstTimestamp = pcapDataArr[0].timestamp
          const lastTimestamp = pcapDataArr.slice(-1)[0].timestamp
          const allMaps = pcapDataArr.filter(item => item.type === 'MAP')
          const mapCount = allMaps.length
          const uniqueMapHex = new Set(allMaps.map(item => item.hex))
          const uniqueMapCount = uniqueMapHex.size
          const spatCount = pcapDataArr.filter(item => item.type === 'SPAT').length
          const bsmCount = pcapDataArr.filter(item => item.type === 'BSM').length
          const ssmCount = pcapDataArr.filter(item => item.type === 'SSM').length
          const srmCount = pcapDataArr.filter(item => item.type === 'SRM').length
          const unknownCount = pcapDataArr.filter(item => item.type === 'UNKNOWN').length
          
          state.value.pcapDataStats = {
            totalCount: pcapDataArr.length,
            firstTimestamp: firstTimestamp,
            lastTimestamp: lastTimestamp,
            mapCount: mapCount,
            uniqueMapCount: uniqueMapCount,
            spatCount: spatCount,
            bsmCount: bsmCount,
            ssmCount: ssmCount,
            srmCount: srmCount,
            unknownCount: unknownCount
          }
        } else {
          console.error("onPcapFileUploaded.fulfilled: action.payload is undefined")
        }
      } catch (e) {
        console.error("onPcapFileUploaded.fulfilled")
        console.error(e)
      }
    });
    builder.addCase(decodeAllToJson.fulfilled, (state, action) => {
      try {
        console.debug("decodeAllToJson.fulfilled reducer")
        state.value.decodedJsonData = action.payload
        const jsonDataArr = Object.values(state.value.decodedJsonData)
        const decodedMaps = jsonDataArr.filter(item => item.type === 'MAP')
        const decodedSpats = jsonDataArr.filter(item => item.type === 'SPAT')
        const decodedBsms = jsonDataArr.filter(item => item.type === 'BSM')
        state.value.importedData = {
          mapData: decodedMaps as ProcessedMap[],
          bsmData: decodedBsms as OdeBsmData[],
          spatData: decodedSpats as ProcessedSpat[],
          notificationData: []
        }
      } catch (e) {
        console.error("decodeAllToJson.fulfilled")
        console.error(e)
      }
    });
    builder.addCase(decodeUniqueMaps.fulfilled, (state, action) => {
      try {
        console.debug("decodeUniqueMaps.fulfilled reducer")
        state.value.uniqueMaps = action.payload
      } catch (e) {
        console.error("decodeUniqueMaps.fulfilled")
        console.error(e)
      }
    });
  },
})

export const {
  setPcapDecoderDialogOpen,
  onMapSelected,
} = pcapDecoderSlice.actions


export const selectPcapData = (state: RootState) => state.pcapDecoder.value.pcapData
export const selectPcapDataStats = (state: RootState) => state.pcapDecoder.value.pcapDataStats
export const selectDecodedJsonData = (state: RootState) => state.pcapDecoder.value.decodedJsonData
export const selectDialogOpen = (state: RootState) => state.pcapDecoder.value.dialogOpen
export const selectUniqueMaps = (state: RootState) => state.pcapDecoder.value.uniqueMaps
export const selectSelectedMap = (state: RootState) => state.pcapDecoder.value.selectedMap
export const selectImportedData = (state: RootState) => state.pcapDecoder.value.importedData

export default pcapDecoderSlice.reducer

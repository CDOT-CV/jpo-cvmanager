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
  handleNewMapMessageData
} from '../map/map-slice'

import { parseMapSignalGroups } from '../map/utilities/message-utils'

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
  uniqueMaps: [] as ProcessedMap[],
  selectedMap: {} as ProcessedMap,
}



export const onPcapFileUploaded = createAsyncThunk(
  'pcapDecoder/onPcapFileUploaded',
  async (contents: ArrayBuffer, { getState, dispatch }) => {
    console.log("onPcapFileUploaded")
    
    const response = await submitPcapDecoderRequest(contents)
    
    // Start decode all to JSON asynchroously.  Don't await because it is slower.
    // TODO: Grey out the download button until this is done
    dispatch(decodeAllToJson(response))

    dispatch(decodeUniqueMaps(response))
    
    return response
  }
)


const submitPcapDecoderRequest = (contents: ArrayBuffer) => {
  console.log("submitPcapDecoderRequest")  
  return DecoderApi.submitPcapDecodeRequest({data: contents});
}

export const decodeAllToJson = createAsyncThunk(
  'pcapDecoder/decodeAllToJson',
  async (hexData: any[], {getState, dispatch}) => {
    console.log("decodeAlltoJson")
    const response = await DecoderApi.submitBatchDecodeRequest({data: hexData})
    return response
  }
)

export const decodeUniqueMaps = createAsyncThunk(
  'pcapDecoder/decodeUniqueMaps',
  async (hexData: any[], {getState, dispatch}) => {
    console.log("decodeUniqueMaps")
    const pcapDataArr = Object.values(hexData)
    const allMaps = pcapDataArr.filter(item => item.type === 'MAP')
    const uniqueMapHexSet = new Set(allMaps.map(item => item.hex))
    const uniqueMaps = []
    uniqueMapHexSet.forEach(hex => uniqueMaps.push({type: "MAP", timestamp: 0, hex: hex}))
    console.log(uniqueMaps)
    const response = await DecoderApi.submitBatchDecodeRequest({data: uniqueMaps})
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
    console.log("updateMap")
    const selectedMap = selectSelectedMap(getState() as RootState)
    dispatch(handleNewMapMessageData({
      mapData: selectedMap, 
      connectingLanes: selectedMap.connectingLanesFeatureCollection, 
      mapSignalGroups: parseMapSignalGroups(selectedMap), 
      mapTime: Date.now() // TODO Populate with the correct timestamp
    }))
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
      state.value.selectedMap = state.value.uniqueMaps.find(function(aMap) {
        return aMap.properties.intersectionId == intersectionId
      })
      console.log("selectedMap: ")
      console.log(state.value.selectedMap)
    }
  },
  extraReducers: (builder) => {
    builder.addCase(onPcapFileUploaded.fulfilled, (state, action) => {
      console.debug("onPcapFileUploaded.fulfilled reducer")
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
    });
    builder.addCase(decodeAllToJson.fulfilled, (state, action) => {
      console.debug("decodeAllToJson.fulfilled reducer")
      state.value.decodedJsonData = action.payload
    });
    builder.addCase(decodeUniqueMaps.fulfilled, (state, action) => {
      console.debug("decodeUniqueMaps.fulfilled reducer")
      state.value.uniqueMaps = action.payload
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

export default pcapDecoderSlice.reducer

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
} from '../map/map-slice'

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
  uniqueMaps: [] as ProcessedMap[]
}



export const onPcapFileUploaded = createAsyncThunk(
  'pcapDecoder/onPcapFileUploaded',
  async (contents: ArrayBuffer, { getState, dispatch }) => {
    console.log("onPcapFileUploaded")
    const response = await submitPcapDecoderRequest(contents)
    dispatch(decodeAllToJson(response))
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
    //const hexData = selectPcapData(getState() as RootState)
    const response = await DecoderApi.submitBatchDecodeRequest({data: hexData})
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
  },
})

export const {
  setPcapDecoderDialogOpen,
} = pcapDecoderSlice.actions


export const selectPcapData = (state: RootState) => state.pcapDecoder.value.pcapData
export const selectPcapDataStats = (state: RootState) => state.pcapDecoder.value.pcapDataStats
export const selectDecodedJsonData = (state: RootState) => state.pcapDecoder.value.decodedJsonData
export const selectDialogOpen = (state: RootState) => state.pcapDecoder.value.dialogOpen

export default pcapDecoderSlice.reducer

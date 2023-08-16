import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import RsuApi from '../apis/rsu-api'
import { selectToken, selectOrganizationName } from './userSlice'
const { DateTime } = require('luxon')

const initialState = {
  selectedRsu: null,
  rsuData: [],
  rsuOnlineStatus: {},
  rsuCounts: {},
  countList: [],
  currentSort: '',
  startDate: '',
  endDate: '',
  messageLoading: false,
  warningMessage: false,
  msgType: 'BSM',
  msgViewerType: 'BSM',
  rsuMapData: {},
  mapList: [],
  mapDate: '',
  displayMap: false,
  msgStart: '',
  msgEnd: '',
  addMsgPoint: false,
  msgCoordinates: [],
  msgData: [],
  msgDateError: false,
  msgFilter: false,
  msgFilterStep: 60,
  msgFilterOffset: 0,
  issScmsStatusData: {},
  ssmDisplay: false,
  srmSsmList: [],
  selectedSrm: [],
  heatMapData: {
    type: 'FeatureCollection',
    features: [],
  },
}

export const updateMessageType = (messageType) => async (dispatch) => {
  dispatch(changeMessageType(messageType))
  dispatch(updateRowData({ message: messageType }))
}

export const getRsuData = createAsyncThunk(
  'rsu/getRsuData',
  async (_, { getState, dispatch }) => {
    const currentState = getState()
    const token = selectToken(currentState)
    const organization = selectOrganizationName(currentState)

    await Promise.all([
      dispatch(_getRsuInfo({ token, organization })),
      dispatch(
        _getRsuOnlineStatus({
          token,
          organization,
          rsuOnlineStatusState: currentState.rsu.value.rsuOnlineStatus,
        })
      ),
      dispatch(_getRsuCounts({ token, organization })),
      dispatch(
        _getRsuMapInfo({
          token,
          organization,
          startDate: currentState.rsu.value.startDate,
          endDate: currentState.rsu.value.endDate,
        })
      ),
    ])
  },
  {
    condition: (_, { getState }) => selectToken(getState()),
  }
)

export const getRsuInfoOnly = createAsyncThunk('rsu/getRsuInfoOnly', async (_, { getState }) => {
  const currentState = getState()
  const token = selectToken(currentState)
  const organization = selectOrganizationName(currentState)
  const rsuInfo = await RsuApi.getRsuInfo(token, organization)
  const rsuData = rsuInfo.rsuList
  return rsuData
})

export const getRsuLastOnline = createAsyncThunk('rsu/getRsuLastOnline', async (rsu_ip, { getState }) => {
  const currentState = getState()
  const token = selectToken(currentState)
  const organization = selectOrganizationName(currentState)
  const rsuLastOnline = await RsuApi.getRsuOnline(token, organization, '', { rsu_ip })
  return rsuLastOnline
})

export const _getRsuInfo = createAsyncThunk('rsu/_getRsuInfo', async (_, { getState }) => {
  const currentState = getState()
  const token = selectToken(currentState)
  const organization = selectOrganizationName(currentState)
  const rsuInfo = await RsuApi.getRsuInfo(token, organization)
  const rsuData = rsuInfo.rsuList

  return rsuData
})

export const _getRsuOnlineStatus = createAsyncThunk(
  'rsu/_getRsuOnlineStatus',
  async (rsuOnlineStatusState, { getState }) => {
    const currentState = getState()
    const token = selectToken(currentState)
    const organization = selectOrganizationName(currentState)
    const rsuOnlineStatus = (await RsuApi.getRsuOnline(token, organization)) ?? rsuOnlineStatusState

    return rsuOnlineStatus
  }
)

export const _getRsuCounts = createAsyncThunk('rsu/_getRsuCounts', async (_, { getState }) => {
  const currentState = getState()
  const token = selectToken(currentState)
  const organization = selectOrganizationName(currentState)

  const query_params = {
    message: currentState.rsu.value.msgType,
    start: currentState.rsu.value.startDate,
    end: currentState.rsu.value.endDate,
  }
  console.debug('getRsuCounts query params:', query_params)
  const rsuCounts =
    (await RsuApi.getRsuCounts(token, organization, '', query_params)) ?? currentState.rsu.value.rsuCounts
  const countList = Object.entries(rsuCounts).map(([key, value]) => {
    return {
      key: key,
      rsu: key,
      road: value.road,
      count: value.count,
    }
  })

  return { rsuCounts, countList }
})

export const _getRsuMapInfo = createAsyncThunk('rsu/_getRsuMapInfo', async ({ startDate, endDate }, { getState }) => {
  const currentState = getState()
  const token = selectToken(currentState)
  const organization = selectOrganizationName(currentState)
  let local_date = DateTime.local()
  let localEndDate = endDate === '' ? local_date.toString() : endDate
  let localStartDate = startDate === '' ? local_date.minus({ days: 1 }).toString() : startDate

  const rsuMapData = await RsuApi.getRsuMapInfo(token, organization, '', {
    ip_list: 'True',
  })

  return {
    endDate: localEndDate,
    startDate: localStartDate,
    rsuMapData,
  }
})

export const getSsmSrmData = createAsyncThunk('rsu/getSsmSrmData', async (_, { getState }) => {
  const currentState = getState()
  const token = selectToken(currentState)
  return await RsuApi.getSsmSrmData(token)
})

export const getIssScmsStatus = createAsyncThunk(
  'rsu/getIssScmsStatus',
  async (_, { getState }) => {
    const currentState = getState()
    const token = selectToken(currentState)
    const organization = selectOrganizationName(currentState)

    return await RsuApi.getIssScmsStatus(token, organization)
  },
  {
    condition: (_, { getState }) => selectToken(getState()),
  }
)

export const updateRowData = createAsyncThunk(
  'rsu/updateRowData',
  async (data, { getState }) => {
    const currentState = getState()
    const token = selectToken(currentState)
    const organization = selectOrganizationName(currentState)

    const msgType = data.hasOwnProperty('message') ? data['message'] : currentState.rsu.value.msgType
    const startDate = data.hasOwnProperty('start') ? data['start'] : currentState.rsu.value.startDate
    const endDate = data.hasOwnProperty('end') ? data['end'] : currentState.rsu.value.endDate

    const warningMessage = new Date(endDate).getTime() - new Date(startDate).getTime() > 86400000

    const rsuCountsData = await RsuApi.getRsuCounts(token, organization, '', {
      message: msgType,
      start: startDate,
      end: endDate,
    })

    var countList = Object.entries(rsuCountsData).map(([key, value]) => {
      return {
        key: key,
        rsu: key,
        road: value.road,
        count: value.count,
      }
    })

    return {
      msgType,
      startDate,
      endDate,
      warningMessage,
      rsuCounts: rsuCountsData,
      countList,
    }
  },
  {
    condition: (_, { getState }) => selectToken(getState()),
  }
)

// export const updateBsmData = createAsyncThunk(
//   'rsu/updateBsmData',
//   async (_, { getState }) => {
//     const currentState = getState()
//     const token = selectToken(currentState)
//     console.log('--------message selected------------')
//     try {
//       const bsmMapData = await RsuApi.postBsmData(
//         token,
//         JSON.stringify({
//           start: currentState.rsu.value.msgStart,
//           end: currentState.rsu.value.msgEnd,
//           geometry: currentState.rsu.value.msgCoordinates,
//         }),
//         ''
//       )
//       return bsmMapData
//     } catch (err) {
//       console.error(err)
//     }
//   },
//   {
//     // Will guard thunk from being executed
//     condition: (_, { getState }) => {
//       const { rsu } = getState()
//       const valid = rsu.value.msgStart !== '' && rsu.value.msgEnd !== '' && rsu.value.msgCoordinates.length > 2
//       return valid
//     },
//   }
// )
export const updateGeoMsgData = createAsyncThunk(
  'rsu/updateGeoMsgData',
  async (_, { getState }) => {
    const currentState = getState()
    const token = selectToken(currentState)

    try {
      const psmMapData = await RsuApi.postGeoMsgData(
        token,
        JSON.stringify({
          msg_type: currentState.msgType,
          start: currentState.rsu.value.msgStart,
          end: currentState.rsu.value.msgEnd,
          geometry: currentState.rsu.value.msgCoordinates,
        }),
        ''
      )
      return psmMapData
    } catch (err) {
      console.error(err)
    }
  },
  {
    // Will guard thunk from being executed
    condition: (_, { getState }) => {
      const { rsu } = getState()
      console.log(
        'time',
        rsu.value.msgStart,
        ' : ',
        rsu.value.msgEnd,
        ' Coordinate length: ',
        rsu.value.msgCoordinates.length,
        ' msgType ',
        rsu.value.msgType
      )
      const valid =
        rsu.value.msgStart !== '' &&
        rsu.value.msgEnd !== '' &&
        rsu.value.msgCoordinates.length > 2 &&
        rsu.value.msgType !== ''
      return valid
    },
  }
)

export const getMapData = createAsyncThunk(
  'rsu/getMapData',
  async (_, { getState }) => {
    const currentState = getState()
    const token = selectToken(currentState)
    const organization = selectOrganizationName(currentState)
    const selectedRsu = selectSelectedRsu(currentState)

    const rsuMapData = await RsuApi.getRsuMapInfo(token, organization, '', {
      ip_address: selectedRsu.properties.ipv4_address,
    })
    return {
      rsuMapData: rsuMapData.geojson,
      mapDate: rsuMapData.date,
    }
  },
  {
    condition: (_, { getState }) => selectToken(getState()),
  }
)

export const rsuSlice = createSlice({
  name: 'rsu',
  initialState: {
    loading: false,
    requestOut: false,
    value: initialState,
  },
  reducers: {
    selectRsu: (state, action) => {
      state.value.selectedRsu = action.payload
    },
    toggleMapDisplay: (state) => {
      state.value.displayMap = !state.value.displayMap
    },
    clearMsg: (state) => {
      state.value.msgCoordinates = []
      state.value.msgData = []
      state.value.msgStart = ''
      state.value.msgEnd = ''
      state.value.msgDateError = false
    },
    toggleSsmSrmDisplay: (state) => {
      state.value.ssmDisplay = !state.value.ssmDisplay
    },
    setSelectedSrm: (state, action) => {
      state.value.selectedSrm = Object.keys(action.payload).length === 0 ? [] : [action.payload]
    },
    toggleMsgPointSelect: (state) => {
      state.value.addMsgPoint = !state.value.addMsgPoint
    },
    updateMsgPoints: (state, action) => {
      state.value.msgCoordinates = action.payload
    },
    updateMsgDate: (state, action) => {
      if (action.payload.type === 'start') state.value.msgStart = action.payload.date
      else state.value.msgEnd = action.payload.date
    },
    triggerMsgDateError: (state) => {
      state.value.msgDateError = true
    },
    changeMessageType: (state, action) => {
      state.value.msgType = action.payload
    },
    changeMessageViewerType: (state, action) => {
      state.value.msgViewerType = action.payload
    },
    setMsgFilter: (state, action) => {
      state.value.msgFilter = action.payload
    },
    setMsgFilterStep: (state, action) => {
      state.value.msgFilterStep = action.payload
    },
    setMsgFilterOffset: (state, action) => {
      state.value.msgFilterOffset = action.payload
    },
    setLoading: (state, action) => {
      state.loading = action.payload
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(getRsuData.pending, (state) => {
        state.loading = true
        state.value.rsuData = []
        state.value.rsuOnlineStatus = {}
        state.value.rsuCounts = {}
        state.value.countList = []
        state.value.heatMapData = {
          type: 'FeatureCollection',
          features: [],
        }
      })
      .addCase(getRsuData.fulfilled, (state) => {
        console.debug('getRsuData.fulfilled', state.value)
        const heatMapFeatures = []
        state.value.rsuData.forEach((rsu) => {
          heatMapFeatures.push({
            type: 'Feature',
            geometry: {
              type: 'Point',
              coordinates: [rsu.geometry.coordinates[0], rsu.geometry.coordinates[1]],
            },
            properties: {
              ipv4_address: rsu.properties.ipv4_address,
              count:
                rsu.properties.ipv4_address in state.value.rsuCounts
                  ? state.value.rsuCounts[rsu.properties.ipv4_address].count
                  : 0,
            },
          })
        })
        state.value.heatMapData.features = heatMapFeatures
        console.debug('heatMapData', heatMapFeatures)
        state.loading = false
      })
      .addCase(getRsuData.rejected, (state) => {
        state.loading = false
      })
      .addCase(getRsuInfoOnly.pending, (state) => {
        state.loading = true
      })
      .addCase(getRsuInfoOnly.fulfilled, (state) => {
        state.loading = false
      })
      .addCase(getRsuInfoOnly.rejected, (state) => {
        state.loading = false
      })
      .addCase(getRsuLastOnline.pending, (state) => {
        state.loading = true
      })
      .addCase(getRsuLastOnline.fulfilled, (state, action) => {
        state.loading = false
        if (state.value.rsuOnlineStatus.hasOwnProperty(action.payload.ip)) {
          state.value.rsuOnlineStatus[action.payload.ip]['last_online'] = action.payload.last_online
        }
      })
      .addCase(getRsuLastOnline.rejected, (state) => {
        state.loading = false
      })
      .addCase(_getRsuInfo.fulfilled, (state, action) => {
        state.value.rsuData = action.payload
      })
      .addCase(_getRsuOnlineStatus.fulfilled, (state, action) => {
        state.value.rsuOnlineStatus = action.payload
      })
      .addCase(_getRsuCounts.fulfilled, (state, action) => {
        state.value.rsuCounts = action.payload.rsuCounts
        state.value.countList = action.payload.countList
      })
      .addCase(_getRsuMapInfo.fulfilled, (state, action) => {
        state.value.startDate = action.payload.startDate
        state.value.endDate = action.payload.endDate
        state.value.mapList = action.payload.rsuMapData
      })
      .addCase(getSsmSrmData.pending, (state) => {
        state.loading = true
      })
      .addCase(getSsmSrmData.rejected, (state) => {
        state.loading = false
      })
      .addCase(getSsmSrmData.fulfilled, (state, action) => {
        state.value.srmSsmList = action.payload
      })
      .addCase(getIssScmsStatus.fulfilled, (state, action) => {
        state.value.issScmsStatusData = action.payload ?? state.value.issScmsStatusData
      })
      .addCase(updateRowData.pending, (state) => {
        state.value.requestOut = true
        state.value.messageLoading = false
      })
      .addCase(updateRowData.fulfilled, (state, action) => {
        if (action.payload === null) return
        state.value.rsuCounts = action.payload.rsuCounts
        state.value.countList = action.payload.countList
        console.debug('updateRowData.fulfilled', action.payload)
        state.value.heatMapData.features.forEach((feat, index) => {
          state.value.heatMapData.features[index].properties.count =
            feat.properties.ipv4_address in action.payload.rsuCounts
              ? action.payload.rsuCounts[feat.properties.ipv4_address].count
              : 0
        })
        state.value.warningMessage = action.payload.warningMessage
        state.value.requestOut = false
        state.value.messageLoading = false
        state.value.msgType = action.payload.msgType
        state.value.startDate = action.payload.startDate
        state.value.endDate = action.payload.endDate
      })
      .addCase(updateRowData.rejected, (state) => {
        state.value.requestOut = false
        state.value.messageLoading = false
      })
      .addCase(updateGeoMsgData.pending, (state) => {
        state.loading = true
        state.value.addMsgPoint = false
        state.value.msgDateError =
          new Date(state.value.msgEnd).getTime() - new Date(state.value.msgStart).getTime() > 86400000
      })
      .addCase(updateGeoMsgData.fulfilled, (state, action) => {
        state.value.msgData = action.payload.body
        state.loading = false
        state.value.msgFilter = true
        state.value.msgFilterStep = 60
        state.value.msgFilterOffset = 0
      })
      .addCase(updateGeoMsgData.rejected, (state) => {
        state.loading = false
      })
      .addCase(getMapData.pending, (state) => {
        state.loading = true
      })
      .addCase(getMapData.fulfilled, (state, action) => {
        state.loading = false
        state.value.rsuMapData = action.payload.rsuMapData
        let date = new Date(action.payload.mapDate)
        state.value.mapDate = date.toLocaleString()
      })
      .addCase(getMapData.rejected, (state) => {
        state.loading = false
      })
  },
})

export const selectLoading = (state) => state.rsu.loading
export const selectRequestOut = (state) => state.rsu.requestOut

export const selectSelectedRsu = (state) => state.rsu.value.selectedRsu
export const selectRsuManufacturer = (state) => state.rsu.value.selectedRsu?.properties?.manufacturer_name
export const selectRsuIpv4 = (state) => state.rsu.value.selectedRsu?.properties?.ipv4_address
export const selectRsuPrimaryRoute = (state) => state.rsu.value.selectedRsu?.properties?.primary_route
export const selectRsuData = (state) => state.rsu.value.rsuData
export const selectRsuOnlineStatus = (state) => state.rsu.value.rsuOnlineStatus
export const selectRsuCounts = (state) => state.rsu.value.rsuCounts
export const selectCountList = (state) => state.rsu.value.countList
export const selectCurrentSort = (state) => state.rsu.value.currentSort
export const selectStartDate = (state) => state.rsu.value.startDate
export const selectEndDate = (state) => state.rsu.value.endDate
export const selectMessageLoading = (state) => state.rsu.value.messageLoading
export const selectWarningMessage = (state) => state.rsu.value.warningMessage
export const selectMsgType = (state) => state.rsu.value.msgType
export const selectMsgViewerType = (state) => state.rsu.value.msgViewerType
export const selectRsuMapData = (state) => state.rsu.value.rsuMapData
export const selectMapList = (state) => state.rsu.value.mapList
export const selectMapDate = (state) => state.rsu.value.mapDate
export const selectDisplayMap = (state) => state.rsu.value.displayMap
export const selectMsgStart = (state) => state.rsu.value.msgStart
export const selectMsgEnd = (state) => state.rsu.value.msgEnd
export const selectAddMsgPoint = (state) => state.rsu.value.addMsgPoint
export const selectMsgCoordinates = (state) => state.rsu.value.msgCoordinates
export const selectMsgData = (state) => state.rsu.value.msgData
export const selectMsgDateError = (state) => state.rsu.value.msgDateError
export const selectMsgFilter = (state) => state.rsu.value.msgFilter
export const selectMsgFilterStep = (state) => state.rsu.value.msgFilterStep
export const selectMsgFilterOffset = (state) => state.rsu.value.msgFilterOffset
export const selectIssScmsStatusData = (state) => state.rsu.value.issScmsStatusData
export const selectSsmDisplay = (state) => state.rsu.value.ssmDisplay
export const selectSrmSsmList = (state) => state.rsu.value.srmSsmList
export const selectSelectedSrm = (state) => state.rsu.value.selectedSrm
export const selectHeatMapData = (state) => state.rsu.value.heatMapData

export const {
  selectRsu,
  toggleMapDisplay,
  clearMsg,
  toggleSsmSrmDisplay,
  setSelectedSrm,
  toggleMsgPointSelect,
  updateMsgPoints,
  updateMsgDate,
  triggerMsgDateError,
  changeMessageType,
  changeMessageViewerType,
  setMsgFilter,
  setMsgFilterStep,
  setMsgFilterOffset,
  setLoading,
} = rsuSlice.actions

export default rsuSlice.reducer

import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { RootState } from '../../../../store'
import { IMessage } from '@stomp/stompjs'
import { getTimestamp } from '../utilities/message-utils'
import { selectToken } from '../../../../generalSlices/userSlice'
import { selectRsuMapData } from '../../../../generalSlices/rsuSlice'
import { downloadAllData } from '../utilities/file-utilities'

export type MAP_QUERY_PARAMS = {
  startDate: Date
  endDate: Date
  eventDate: Date
  vehicleId?: string
  intersectionId?: number
  roadRegulatorId?: number
  isDefault?: boolean
}

export type IMPORTED_MAP_MESSAGE_DATA = {
  mapData: ProcessedMap[]
  bsmData: OdeBsmData[]
  spatData: ProcessedSpat[]
  notificationData: any
}

export type RAW_MESSAGE_DATA_EXPORT = {
  map?: ProcessedMap[]
  spat?: ProcessedSpat[]
  bsm?: BsmFeatureCollection
  notification?: MessageMonitor.Notification
  event?: MessageMonitor.Event
  assessment?: Assessment
}

export type BSM_COUNTS_CHART_DATA = MessageMonitor.MinuteCount & {
  minutesAfterMidnight: number
  timestamp: string
}

interface MinimalClient {
  connect: (headers: unknown, connectCallback: () => void, errorCallback?: (error: string) => void) => void
  subscribe: (destination: string, callback: (message: IMessage) => void) => void
  disconnect: (disconnectCallback: () => void) => void
}

export type MAP_PROPS = {
  sourceData:
    | MessageMonitor.Notification
    | MessageMonitor.Event
    | Assessment
    | timestamp
    | {
        map: ProcessedMap[]
        spat: ProcessedSpat[]
        bsm: OdeBsmData[]
      }
    | undefined
  sourceDataType: 'notification' | 'event' | 'assessment' | 'timestamp' | undefined
  intersectionId: number | undefined
  roadRegulatorId: number | undefined
  loadOnNull?: boolean
}

const initialState = {
  sourceData: undefined as MAP_PROPS['sourceData'],
  initialSourceDataType: undefined as MAP_PROPS['sourceDataType'],
  sourceDataType: undefined as MAP_PROPS['sourceDataType'],
  intersectionId: undefined as MAP_PROPS['intersectionId'],
  roadRegulatorId: undefined as MAP_PROPS['roadRegulatorId'],
  loadOnNull: true as MAP_PROPS['loadOnNull'],

  queryParams: {
    startDate: new Date(Date.now() - 1000 * 60 * 1),
    endDate: new Date(Date.now() + 1000 * 60 * 1),
    eventDate: new Date(Date.now()),
    vehicleId: undefined,
    intersectionId: undefined,
    roadRegulatorId: undefined,
  } as MAP_QUERY_PARAMS,

  totalBufferedMapData: {} as { [key: number]: { [epochMillis: number]: ProcessedMap } },
  totalBufferedSpatData: {} as { [key: number]: { [epochMillis: number]: ProcessedSpat } },
  totalBufferedBsmData: {} as { [epochMillis: number]: BsmFeature },

  playbackModeActive: false,
  timeWindowSeconds: 60,
  renderTimeInterval: [0, 0],
  rawData: {} as RAW_MESSAGE_DATA_EXPORT,
  importedMessageData: undefined as IMPORTED_MAP_MESSAGE_DATA | undefined,
  loadInitialDataTimeoutId: undefined as NodeJS.Timeout | undefined,
  wsClient: undefined as MinimalClient | undefined,
  liveDataActive: false,
  liveDataRestart: -1,
  liveDataRestartTimeoutId: undefined as NodeJS.Timeout | undefined,
  pullInitialDataAbortControllers: [] as AbortController[],
  abortAllFutureRequests: false,
  decoderModeEnabled: false,
}

type timestamp = {
  timestamp: number
}

export const generateQueryParams = (
  sourceData: MAP_PROPS['sourceData'],
  sourceDataType: MAP_PROPS['sourceDataType'],
  decoderModeEnabled: boolean
) => {
  const startOffset = 1000 * 60 * 1
  const endOffset = 1000 * 60 * 1

  switch (sourceDataType) {
    case 'notification':
      const notification = sourceData as MessageMonitor.Notification
      return {
        startDate: new Date(notification.notificationGeneratedAt - startOffset),
        endDate: new Date(notification.notificationGeneratedAt + endOffset),
        eventDate: new Date(notification.notificationGeneratedAt),
        vehicleId: undefined,
        isDefault: false,
      }
    case 'event':
      const event = sourceData as MessageMonitor.Event
      return {
        startDate: new Date(event.eventGeneratedAt - startOffset),
        endDate: new Date(event.eventGeneratedAt + endOffset),
        eventDate: new Date(event.eventGeneratedAt),
        vehicleId: undefined,
        isDefault: false,
      }
    case 'assessment':
      const assessment = sourceData as Assessment
      return {
        startDate: new Date(assessment.assessmentGeneratedAt - startOffset),
        endDate: new Date(assessment.assessmentGeneratedAt + endOffset),
        eventDate: new Date(assessment.assessmentGeneratedAt),
        vehicleId: undefined,
        isDefault: false,
      }
    case 'timestamp':
      const ts = (sourceData as timestamp).timestamp
      return {
        startDate: new Date(ts - startOffset),
        endDate: new Date(ts + endOffset),
        eventDate: new Date(ts),
        vehicleId: undefined,
        isDefault: false,
      }
    default:
      if (decoderModeEnabled) {
        let startDate = undefined as number | undefined
        let endDate = undefined as number | undefined

        for (const spat of (sourceData as { spat: ProcessedSpat[] }).spat) {
          if (!startDate || spat.utcTimeStamp < startDate) {
            startDate = getTimestamp(spat.utcTimeStamp)
          }
          if (!endDate || getTimestamp(spat.utcTimeStamp) > endDate) {
            endDate = getTimestamp(spat.utcTimeStamp)
          }
        }
        return {
          startDate: new Date(startDate ?? Date.now()),
          endDate: new Date(endDate ?? Date.now() + 1),
          eventDate: new Date((startDate ?? Date.now()) / 2 + (endDate ?? Date.now() + 1) / 2),
          vehicleId: undefined,
          isDefault: false,
        }
      }
      return {
        startDate: new Date(Date.now() - startOffset),
        endDate: new Date(Date.now() + endOffset),
        eventDate: new Date(Date.now()),
        vehicleId: undefined,
        isDefault: true,
      }
  }
}

const getNewSliderTimeValue = (startDate: Date, sliderValue: number, timeWindowSeconds: number) => {
  return {
    start: new Date((startDate.getTime() / 1000 + sliderValue - timeWindowSeconds) * 1000),
    end: new Date((startDate.getTime() / 1000 + sliderValue) * 1000),
  }
}

export const pullInitialData = createAsyncThunk(
  'intersectionMapController/pullInitialData',
  async (_, { getState, dispatch }) => {
    const currentState = getState() as RootState
    const authToken = selectToken(currentState)!
    const importedMessageData = selectImportedMessageData(currentState)
    const queryParams = selectQueryParams(currentState)
    const sourceData = selectSourceData(currentState)
    const decoderModeEnabled = selectDecoderModeEnabled(currentState)

    // Logic:
    // 1. If intersectionId is -1, we have no MAP message with which to display data - reset the view.
    //

    if (
      queryParams.intersectionId === -1 &&
      (!decoderModeEnabled || (sourceData as { map: ProcessedMap[] })?.map?.length === 0)
    ) {
      dispatch(resetMapView())
      if (!decoderModeEnabled) {
        console.log('Intersection ID is -1. Not attempting to pull initial map data.')
        return
      }
    }
    dispatch(resetInitialDataAbortControllers())
    dispatch(setAbortAllFutureRequests(false))
    console.debug('Pulling Initial Data')
    let rawMap: ProcessedMap[] = []
    let rawSpat: ProcessedSpat[] = []
    let rawBsm: OdeBsmData[] = []
    let abortController = new AbortController()
    if (decoderModeEnabled) {
      rawMap = (sourceData as { map: ProcessedMap[] }).map.map((map) => ({
        ...map,
        properties: {
          ...map.properties,
          odeReceivedAt: getTimestamp(map.properties.odeReceivedAt),
        },
      }))
      rawSpat = (sourceData as { spat: ProcessedSpat[] }).spat.map((spat) => ({
        ...spat,
        utcTimeStamp: getTimestamp(spat.utcTimeStamp),
      }))
      rawBsm = (sourceData as { bsm: OdeBsmData[] }).bsm.map((bsm) => ({
        ...bsm,
        metadata: {
          ...bsm.metadata,
          odeReceivedAt: getTimestamp(bsm.metadata.odeReceivedAt),
        },
      }))
      if (rawSpat && rawSpat.length != 0 && rawMap && rawMap.length != 0) {
        const sortedSpatData = rawSpat.sort((x, y) => x.utcTimeStamp - y.utcTimeStamp)
        const startTime = new Date(sortedSpatData[0].utcTimeStamp)
        const endTime = new Date(sortedSpatData[sortedSpatData.length - 1].utcTimeStamp)
        if (
          queryParams.startDate.getTime() !== startTime.getTime() ||
          queryParams.endDate.getTime() !== endTime.getTime()
        ) {
          dispatch(
            updateQueryParams({
              ...generateQueryParams({ map: [], spat: rawSpat, bsm: [] }, null, decoderModeEnabled),
              intersectionId: rawMap[0].properties.intersectionId,
              roadRegulatorId: -1,
            })
          )
        }
      }
    } else if (queryParams.isDefault == true) {
      console.debug('Default query params. Checking latest SPAT data')
      abortController = new AbortController()
      dispatch(addInitialDataAbortController(abortController))
      if (selectAbortAllFutureRequests(getState() as RootState)) {
        return
      }
      const latestSpats = await MessageMonitorApi.getSpatMessages({
        token: authToken,
        intersectionId: queryParams.intersectionId,
        roadRegulatorId: queryParams.roadRegulatorId,
        latest: true,
        abortController,
      })
      if (latestSpats && latestSpats.length > 0) {
        dispatch(
          updateQueryParams({
            state: currentState.intersectionMapController,
            ...generateQueryParams(
              { timestamp: getTimestamp(latestSpats.at(-1)?.utcTimeStamp) },
              'timestamp',
              decoderModeEnabled
            ),
            intersectionId: queryParams.intersectionId,
            roadRegulatorId: queryParams.roadRegulatorId,
          })
        )
        return
      } else {
        dispatch(
          updateQueryParams({
            state: currentState.intersectionMapController,
            ...generateQueryParams({ timestamp: Date.now() }, 'timestamp', decoderModeEnabled),
            intersectionId: queryParams.intersectionId,
            roadRegulatorId: queryParams.roadRegulatorId,
          })
        )
        return
      }
    } else if (importedMessageData == undefined) {
      if (selectAbortAllFutureRequests(getState() as RootState)) {
        return
      }
      // ######################### Retrieve MAP Data #########################
      abortController = new AbortController()
      dispatch(addInitialDataAbortController(abortController))
      const rawMapPromise = MessageMonitorApi.getMapMessages({
        token: authToken,
        intersectionId: queryParams.intersectionId!,
        roadRegulatorId: queryParams.roadRegulatorId!,
        endTime: queryParams.endDate,
        latest: true,
        abortController,
      })
      toast.promise(rawMapPromise, {
        loading: `Loading MAP Data`,
        success: `Successfully got MAP Data`,
        error: `Failed to get MAP data. Please see console`,
      })
      rawMap = await rawMapPromise
    } else {
      rawMap = importedMessageData.mapData
      rawSpat = importedMessageData.spatData.sort((a, b) => a.utcTimeStamp - b.utcTimeStamp)
      rawBsm = importedMessageData.bsmData
    }

    if (decoderModeEnabled) {
      let bsmGeojson = parseBsmToGeojson(rawBsm)
      bsmGeojson = {
        ...bsmGeojson,
        features: [...[...bsmGeojson.features].sort((a, b) => b.properties.odeReceivedAt - a.properties.odeReceivedAt)],
      }
      dispatch(renderEntireMap({ currentMapData: [], currentSpatData: [], currentBsmData: bsmGeojson }))
    }
    if (!rawMap || rawMap.length == 0) {
      console.info('NO MAP MESSAGES WITHIN TIME')
      return
    }

    const latestMapMessage: ProcessedMap = rawMap.at(-1)!
    const mapCoordinates: OdePosition3D = latestMapMessage?.properties.refPoint

    const mapSignalGroupsLocal = parseMapSignalGroups(latestMapMessage)
    dispatch(
      handleNewMapMessageData({
        mapData: latestMapMessage,
        connectingLanes: latestMapMessage.connectingLanesFeatureCollection,
        mapSignalGroups: mapSignalGroupsLocal,
        mapTime: latestMapMessage.properties.odeReceivedAt as unknown as number,
      })
    )

    if (importedMessageData == undefined && !decoderModeEnabled) {
      if (selectAbortAllFutureRequests(getState() as RootState)) {
        return
      }
      // ######################### Retrieve SPAT Data #########################
      abortController = new AbortController()
      dispatch(addInitialDataAbortController(abortController))
      const rawSpatPromise = MessageMonitorApi.getSpatMessages({
        token: authToken,
        intersectionId: queryParams.intersectionId!,
        roadRegulatorId: queryParams.roadRegulatorId!,
        startTime: queryParams.startDate,
        endTime: queryParams.endDate,
        abortController,
      })
      toast.promise(rawSpatPromise, {
        loading: `Loading SPAT Data`,
        success: `Successfully got SPAT Data`,
        error: `Failed to get SPAT data. Please see console`,
      })
      rawSpat = (await rawSpatPromise)
        .sort((a, b) => a.utcTimeStamp - b.utcTimeStamp)
        .map((spat) => ({
          ...spat,
          utcTimeStamp: getTimestamp(spat.utcTimeStamp),
        }))

      if (selectAbortAllFutureRequests(getState() as RootState)) {
        return
      }
      dispatch(getBsmDailyCounts())
      dispatch(getSurroundingEvents())
      dispatch(getSurroundingNotifications())
    }

    // ######################### SPAT Signal Groups #########################
    const spatSignalGroupsLocal = parseSpatSignalGroups(rawSpat)
    dispatch(setSpatSignalGroups(spatSignalGroupsLocal))

    // ######################### BSMs #########################
    if (selectAbortAllFutureRequests(getState() as RootState)) {
      return
    }
    if (!importedMessageData && !decoderModeEnabled) {
      abortController = new AbortController()
      dispatch(addInitialDataAbortController(abortController))
      const rawBsmPromise = MessageMonitorApi.getBsmMessages({
        token: authToken,
        vehicleId: queryParams.vehicleId,
        startTime: queryParams.startDate,
        endTime: queryParams.endDate,
        long: mapCoordinates.longitude,
        lat: mapCoordinates.latitude,
        distance: 500,
        abortController,
      })
      toast.promise(rawBsmPromise, {
        loading: `Loading BSM Data`,
        success: `Successfully got BSM Data`,
        error: `Failed to get BSM data. Please see console`,
      })
      rawBsm = await rawBsmPromise
    }
    let bsmGeojson = parseBsmToGeojson(rawBsm)
    bsmGeojson = {
      ...bsmGeojson,
      features: [...[...bsmGeojson.features].sort((a, b) => b.properties.odeReceivedAt - a.properties.odeReceivedAt)],
    }
    dispatch(renderEntireMap({ currentMapData: rawMap, currentSpatData: rawSpat, currentBsmData: bsmGeojson }))
    return
  },
  {
    condition: (_, { getState }) =>
      selectToken(getState() as RootState) != undefined &&
      selectQueryParams(getState() as RootState).intersectionId != undefined &&
      selectQueryParams(getState() as RootState).roadRegulatorId != undefined &&
      (selectSourceData(getState() as RootState) != undefined || selectLoadOnNull(getState() as RootState) == true),
  }
)

export const renderEntireMap = createAsyncThunk(
  'intersectionMapController/renderEntireMap',
  async (
    args: { currentMapData: ProcessedMap[]; currentSpatData: ProcessedSpat[]; currentBsmData: BsmFeatureCollection },
    { getState, dispatch }
  ) => {
    const { currentMapData, currentSpatData, currentBsmData } = args
    const currentState = getState() as RootState

    const queryParams = selectQueryParams(currentState)
    const sourceData = selectSourceData(currentState)
    const sourceDataType = selectSourceDataType(currentState)
    const decoderModeEnabled = selectDecoderModeEnabled(currentState)

    // Still render BSMs if decoderModeEnabled is true, even if there are no map messages.
    // The condition guard eliminates sourceDataType != exact && currentMapData.length == 0
    if (decoderModeEnabled && currentMapData.length == 0) {
      const uniqueIds = new Set(currentBsmData.features.map((bsm) => bsm.properties?.id))
      // generate equally spaced unique colors for each uniqueId
      const colors = generateColorDictionary(uniqueIds)
      dispatch(setBsmLegendColors(colors))
      // add color to each feature
      const bsmLayerStyle = generateMapboxStyleExpression(colors)
      dispatch(setBsmCircleColor(bsmLayerStyle))

      return {
        bsmData: currentBsmData,
        rawData: { bsm: currentBsmData },
        sliderValue: Math.min(
          getTimeRange(queryParams.startDate, queryParams.eventDate ?? new Date()),
          getTimeRange(queryParams.startDate, queryParams.endDate)
        ),
      }
    }

    // ######################### MAP Data #########################
    const latestMapMessage: ProcessedMap = currentMapData.at(-1)
    const mapSignalGroupsLocal = parseMapSignalGroups(latestMapMessage)
    dispatch(
      handleNewMapMessageData({
        mapData: latestMapMessage,
        connectingLanes: latestMapMessage.connectingLanesFeatureCollection,
        mapSignalGroups: mapSignalGroupsLocal,
        mapTime: latestMapMessage.properties.odeReceivedAt as unknown as number,
      })
    )

    // ######################### SPAT Signal Groups #########################
    const spatSignalGroupsLocal = parseSpatSignalGroups(currentSpatData)
    dispatch(setSpatSignalGroups(spatSignalGroupsLocal))

    // ######################### Message Data #########################
    const rawData = {}
    rawData['map'] = currentMapData
    rawData['spat'] = currentSpatData
    rawData['bsm'] = currentBsmData
    if (sourceDataType == 'notification') {
      rawData['notification'] = sourceData as MessageMonitor.Notification
    } else if (sourceDataType == 'event') {
      rawData['event'] = sourceData as MessageMonitor.Event
    } else if (sourceDataType == 'assessment') {
      rawData['assessment'] = sourceData as Assessment
    }
    return {
      bsmData: currentBsmData,
      rawData: rawData,
      sliderValue: Math.min(
        getTimeRange(queryParams.startDate, queryParams.eventDate ?? new Date()),
        getTimeRange(queryParams.startDate, queryParams.endDate)
      ),
    }
  },
  {
    condition: (
      args: { currentMapData: ProcessedMap[]; currentSpatData: ProcessedSpat[]; currentBsmData: BsmFeatureCollection },
      { getState }
    ) => args.currentMapData.length != 0 || selectDecoderModeEnabled(getState() as RootState),
  }
)

export const updateBsmData = createAsyncThunk(
  'intersectionMapController/updateBsmData',
  async (bsmFC: BsmFeatureCollection, { getState, dispatch }) => {
    const uniqueIds = new Set(bsmFC.features.map((bsm) => bsm.properties?.id))
    // generate equally spaced unique colors for each uniqueId
    const colors = generateColorDictionary(uniqueIds)
    dispatch(setBsmLegendColors(colors))
    // add color to each feature
    const bsmLayerStyle = generateMapboxStyleExpression(colors)
    dispatch(setBsmCircleColor(bsmLayerStyle))
    return bsmFC
  }
)

export const updateTrailedBsmData = createAsyncThunk(
  'intersectionMapController/updateTrailedBsmData',
  async (_, { getState, dispatch }) => {
    const currentState = getState() as RootState
    const bsmData = selectBsmData(currentState)
    const renderTimeInterval = selectRenderTimeInterval(currentState)
    const bsmTrailLength = selectBsmTrailLength(currentState)

    const filteredBsms: BsmFeature[] = bsmData?.features?.filter(
      (feature) =>
        feature.properties?.odeReceivedAt >= renderTimeInterval[0] &&
        feature.properties?.odeReceivedAt <= renderTimeInterval[1]
    )
    const sortedBsms = filteredBsms.sort((a, b) => b.properties.odeReceivedAt - a.properties.odeReceivedAt)

    const uniqueIds = new Set(filteredBsms.map((bsm) => bsm.properties?.id).sort())
    // generate equally spaced unique colors for each uniqueId
    const colors = generateColorDictionary(uniqueIds)
    dispatch(setBsmLegendColors(colors))
    // add color to each feature
    const bsmLayerStyle = generateMapboxStyleExpression(colors)
    dispatch(setBsmCircleColor(bsmLayerStyle))

    const lastBsms: BsmFeature[] = []
    const bsmCounts: { [id: string]: number } = {}
    for (let i = 0; i < sortedBsms.length; i++) {
      const id = sortedBsms[i].properties?.id
      if (bsmCounts[id] == undefined) {
        bsmCounts[id] = 0
      }
      if (bsmCounts[id] < bsmTrailLength) {
        lastBsms.push(sortedBsms[i])
        bsmCounts[id]++
      }
    }
    return { ...bsmData, features: lastBsms }
  }
)

export const renderIterative_Map = createAsyncThunk(
  'intersectionMapController/renderIterative_Map',
  async (newMapData: ProcessedMap[], { getState, dispatch }) => {
    const currentState = getState() as RootState
    const queryParams = selectQueryParams(currentState)
    const currentMapData: { [key: number]: ProcessedMap } = selectCurrentMapData(currentState)

    const start = Date.now()
    const OLDEST_DATA_TO_KEEP = queryParams.eventDate.getTime() - queryParams.startDate.getTime() // milliseconds

    const currTimestamp = getTimestamp(newMapData.at(-1)!.properties.odeReceivedAt) / 1000

    // for each map message, check if it is older than the oldest data to keep

    let oldIndex = 0
    Object.entries(currentMapData).forEach(([key, map]) => {
      if (map.properties.odeReceivedAt < currTimestamp - OLDEST_DATA_TO_KEEP) {
        oldIndex++
      }
    })
    for (let i = 0; i < currentMapData.length; i++) {
      if ((currentMapData[i].properties.odeReceivedAt as unknown as number) < currTimestamp - OLDEST_DATA_TO_KEEP) {
        oldIndex = i
      } else {
        break
      }
    }
    const currentMapDataLocal = currentMapData.slice(oldIndex, currentMapData.length).concat(newMapData)

    // ######################### MAP Data #########################
    const latestMapMessage: ProcessedMap = currentMapDataLocal.at(-1)!
    if (latestMapMessage != null) {
      setViewState({
        latitude: latestMapMessage?.properties.refPoint.latitude,
        longitude: latestMapMessage?.properties.refPoint.longitude,
        zoom: 19,
      })
    }

    // ######################### SPAT Signal Groups #########################
    const mapSignalGroupsLocal = parseMapSignalGroups(latestMapMessage)

    console.debug('MAP RENDER TIME:', Date.now() - start, 'ms')
    const previousMapMessage: ProcessedMap | undefined = currentMapData.at(-1)
    if (
      latestMapMessage != null &&
      (latestMapMessage.properties.refPoint.latitude != previousMapMessage?.properties.refPoint.latitude ||
        latestMapMessage.properties.refPoint.longitude != previousMapMessage?.properties.refPoint.longitude)
    ) {
      setViewState({
        latitude: latestMapMessage?.properties.refPoint.latitude,
        longitude: latestMapMessage?.properties.refPoint.longitude,
        zoom: 19,
      })
    }
    dispatch(setRawData({ map: currentMapDataLocal }))
    return {
      currentMapData: currentMapDataLocal,
      connectingLanes: latestMapMessage.connectingLanesFeatureCollection,
      mapData: latestMapMessage,
      mapTime: currTimestamp,
      mapSignalGroups: mapSignalGroupsLocal,
    }
  },
  {
    condition: (newMapData: ProcessedMap[], { getState }) => newMapData.length != 0,
  }
)

export const renderIterative_Spat = createAsyncThunk(
  'intersectionMapController/renderIterative_Spat',
  async (newSpatData: ProcessedSpat[], { getState, dispatch }) => {
    const currentState = getState() as RootState
    const queryParams = selectQueryParams(currentState)
    const currentSpatSignalGroups: SpatSignalGroups = selectSpatSignalGroups(currentState) ?? {}
    const currentProcessedSpatData: ProcessedSpat[] = selectCurrentSpatData(currentState) ?? []

    const start = Date.now()
    const OLDEST_DATA_TO_KEEP = queryParams.eventDate.getTime() - queryParams.startDate.getTime() // milliseconds
    if (newSpatData.length == 0) {
      console.warn('Did not attempt to render map (iterative SPAT), no new SPAT messages available:', newSpatData)
      return { signalGroups: currentSpatSignalGroups, raw: currentProcessedSpatData }
    }
    // Inject and filter spat data
    // 2024-01-09T00:24:28.354Z
    newSpatData = newSpatData.map((spat) => ({
      ...spat,
      utcTimeStamp: getTimestamp(spat.utcTimeStamp),
    }))
    const currTimestamp = getTimestamp(newSpatData.at(-1)!.utcTimeStamp)

    let oldIndex = 0
    const currentSpatSignalGroupsArr = Object.keys(currentSpatSignalGroups).map((key) => ({
      key,
      sigGroup: currentSpatSignalGroups[key],
    }))
    for (let i = 0; i < currentSpatSignalGroupsArr.length; i++) {
      if (Number(currentSpatSignalGroupsArr[i].key) < currTimestamp - OLDEST_DATA_TO_KEEP) {
        oldIndex = i
      } else {
        break
      }
    }
    const newSpatSignalGroups = parseSpatSignalGroups(newSpatData)
    const newSpatSignalGroupsArr = Object.keys(newSpatSignalGroups).map((key) => ({
      key,
      sigGroup: newSpatSignalGroups[key],
    }))
    const filteredSpatSignalGroupsArr = currentSpatSignalGroupsArr
      .slice(oldIndex, currentSpatSignalGroupsArr.length)
      .concat(newSpatSignalGroupsArr)
    const currentSpatSignalGroupsLocal = filteredSpatSignalGroupsArr.reduce((acc, curr) => {
      acc[curr.key] = curr.sigGroup
      return acc
    }, {} as SpatSignalGroups)

    // Update current processed spat data
    oldIndex = 0
    for (let i = 0; i < currentProcessedSpatData.length; i++) {
      if (currentProcessedSpatData[i].utcTimeStamp < currTimestamp - OLDEST_DATA_TO_KEEP) {
        oldIndex = i
      } else {
        break
      }
    }
    const currentProcessedSpatDataLocal = currentProcessedSpatData
      .slice(oldIndex, currentProcessedSpatData.length)
      .concat(newSpatData)
    console.debug('SPAT RENDER TIME:', Date.now() - start, 'ms')
    return { signalGroups: currentSpatSignalGroupsLocal, raw: currentProcessedSpatDataLocal }
  },
  {
    condition: (newSpatData: ProcessedSpat[], { getState }) => newSpatData.length != 0,
  }
)

export const renderIterative_Bsm = createAsyncThunk(
  'intersectionMapController/renderIterative_Bsm',
  async (newBsmData: OdeBsmData[], { getState, dispatch }) => {
    const currentState = getState() as RootState
    const queryParams = selectQueryParams(currentState)
    const currentBsmData: BsmFeatureCollection = selectCurrentBsmData(currentState)

    const OLDEST_DATA_TO_KEEP = queryParams.eventDate.getTime() - queryParams.startDate.getTime() // milliseconds
    // Inject and filter spat data
    const currTimestamp = new Date(newBsmData.at(-1)!.metadata.odeReceivedAt as unknown as string).getTime() / 1000
    let oldIndex = 0
    for (let i = 0; i < currentBsmData.features.length; i++) {
      if (Number(currentBsmData.features[i].properties.odeReceivedAt) < currTimestamp - OLDEST_DATA_TO_KEEP) {
        oldIndex = i
      } else {
        break
      }
    }
    const newBsmGeojson = parseBsmToGeojson(newBsmData)
    const currentBsmGeojson = {
      ...currentBsmData,
      features: currentBsmData.features.slice(oldIndex, currentBsmData.features.length).concat(newBsmGeojson.features),
    }

    dispatch(updateBsmData(currentBsmGeojson))
    dispatch(setRawData({ bsm: currentBsmGeojson }))
    return currentBsmGeojson
  },
  {
    condition: (newBsmData: OdeBsmData[], { getState }) => newBsmData.length != 0,
  }
)

export const getBsmDailyCounts = createAsyncThunk(
  'intersectionMapController/getBsmDailyCounts',
  async (_, { getState, dispatch }) => {
    const currentState = getState() as RootState
    const authToken = selectToken(currentState)!
    const queryParams = selectQueryParams(currentState)

    const dayStart = new Date(queryParams.startDate)
    dayStart.setHours(0, 0, 0, 0)
    const dayEnd = new Date(queryParams.startDate)
    dayEnd.setHours(23, 59, 59, 0)

    if (selectAbortAllFutureRequests(getState() as RootState)) {
      return
    }
    const abortController = new AbortController()
    dispatch(addInitialDataAbortController(abortController))
    const bsmEventsByMinutePromise = EventsApi.getBsmByMinuteEvents({
      token: authToken,
      intersectionId: queryParams.intersectionId!,
      startTime: dayStart,
      endTime: dayEnd,
      test: false,
      abortController,
    })
    toast.promise(bsmEventsByMinutePromise, {
      loading: `Loading BSM Event Counts`,
      success: `Successfully got BSM event counts`,
      error: `Failed to get BSM event counts. Please see console`,
    })
    return bsmEventsByMinutePromise
  },
  {
    condition: (_, { getState }) =>
      selectToken(getState() as RootState) != undefined &&
      selectQueryParams(getState() as RootState).intersectionId != undefined &&
      selectQueryParams(getState() as RootState).roadRegulatorId != undefined,
  }
)

export const getSurroundingEvents = createAsyncThunk(
  'intersectionMapController/getSurroundingEvents',
  async (_, { getState, dispatch }) => {
    const currentState = getState() as RootState
    const authToken = selectToken(currentState)!
    const queryParams = selectQueryParams(currentState)

    if (selectAbortAllFutureRequests(getState() as RootState)) {
      return
    }
    const abortController = new AbortController()
    dispatch(addInitialDataAbortController(abortController))
    const surroundingEventsPromise = EventsApi.getAllEvents(
      authToken,
      queryParams.intersectionId!,
      queryParams.roadRegulatorId!,
      queryParams.startDate,
      queryParams.endDate,
      abortController
    )
    return surroundingEventsPromise
  },
  {
    condition: (_, { getState }) =>
      selectToken(getState() as RootState) != undefined &&
      selectQueryParams(getState() as RootState).intersectionId != undefined &&
      selectQueryParams(getState() as RootState).roadRegulatorId != undefined,
  }
)

export const getSurroundingNotifications = createAsyncThunk(
  'intersectionMapController/getSurroundingNotifications',
  async (_, { getState, dispatch }) => {
    const currentState = getState() as RootState
    const authToken = selectToken(currentState)!
    const queryParams = selectQueryParams(currentState)

    if (selectAbortAllFutureRequests(getState() as RootState)) {
      return
    }
    const abortController = new AbortController()
    dispatch(addInitialDataAbortController(abortController))
    const surroundingNotificationsPromise = NotificationApi.getAllNotifications({
      token: authToken,
      intersectionId: queryParams.intersectionId!,
      roadRegulatorId: queryParams.roadRegulatorId!,
      startTime: queryParams.startDate,
      endTime: queryParams.endDate,
      abortController,
    })
    return surroundingNotificationsPromise
  },
  {
    condition: (_, { getState }) =>
      selectToken(getState() as RootState) != undefined &&
      selectQueryParams(getState() as RootState).intersectionId != undefined &&
      selectQueryParams(getState() as RootState).roadRegulatorId != undefined,
  }
)

export const initializeLiveStreaming = createAsyncThunk(
  'intersectionMapController/initializeLiveStreaming',
  async (
    args: { token: string; roadRegulatorId: number; intersectionId: number; numRestarts?: number },
    { getState, dispatch }
  ) => {
    const { token, roadRegulatorId, intersectionId, numRestarts = 0 } = args
    // Connect to WebSocket when component mounts
    const liveDataActive = selectLiveDataActive(getState() as RootState)
    const wsClient = selectWsClient(getState() as RootState)

    dispatch(onTimeQueryChanged({ eventTime: new Date(), timeBefore: 10, timeAfter: 0, timeWindowSeconds: 2 }))
    dispatch(resetMapView())

    if (!liveDataActive) {
      console.debug('Not initializing live streaming because liveDataActive is false')
      return
    }

    let protocols = ['v10.stomp', 'v11.stomp']
    protocols.push(token)
    const url = `${EnvironmentVars.CVIZ_API_WS_URL}/stomp`
    console.debug('Connecting to STOMP endpoint: ' + url + ' with token: ' + token)

    // Stomp Client Documentation: https://stomp-js.github.io/stomp-websocket/codo/extra/docs-src/Usage.md.html
    let client = Stomp.client(url, protocols)
    client.debug = (e) => {
      console.debug('STOMP Debug: ' + e)
    }

    // Topics are in the format /live/{roadRegulatorID}/{intersectionID}/{spat,map,bsm}
    let spatTopic = `/live/${roadRegulatorId}/${intersectionId}/spat`
    let mapTopic = `/live/${roadRegulatorId}/${intersectionId}/map`
    let bsmTopic = `/live/${roadRegulatorId}/${intersectionId}/bsm` // TODO: Filter by road regulator ID
    let spatTime = Date.now()
    let mapTime = Date.now()
    let bsmTime = Date.now()
    let connectionStartTime = Date.now()
    client.connect(
      {},
      () => {
        client.subscribe(spatTopic, function (mes: IMessage) {
          const spatMessage: ProcessedSpat = JSON.parse(mes.body)
          console.debug('Received SPaT message ' + (Date.now() - spatTime) + ' ms')
          spatTime = Date.now()
          dispatch(renderIterative_Spat(spatMessage))
          dispatch(maybeUpdateSliderValue())
        })

        client.subscribe(mapTopic, function (mes: IMessage) {
          const mapMessage: ProcessedMap = JSON.parse(mes.body)
          console.debug('Received MAP message ' + (Date.now() - mapTime) + ' ms')
          mapTime = Date.now()
          dispatch(renderIterative_Map(mapMessage))
          dispatch(maybeUpdateSliderValue())
        })

        client.subscribe(bsmTopic, function (mes: IMessage) {
          const bsmData: OdeBsmData = JSON.parse(mes.body)
          console.debug('Received BSM message ' + (Date.now() - bsmTime) + ' ms')
          bsmTime = Date.now()
          dispatch(renderIterative_Bsm(bsmData))
          dispatch(maybeUpdateSliderValue())
        })
      },
      (error) => {
        console.error('Live Streaming ERROR connecting to live data Websocket: ' + error)
      }
    )

    client.onDisconnect = (frame) => {
      console.debug(
        'Live Streaming Disconnected from STOMP endpoint: ' +
          frame +
          ' (numRestarts: ' +
          numRestarts +
          ', wsClient: ' +
          wsClient +
          ')'
      )
      if (numRestarts < 5 && liveDataActive) {
        let numRestartsLocal = numRestarts
        if (Date.now() - connectionStartTime > 10000) {
          numRestartsLocal = 0
        }
        console.debug('Attempting to reconnect to STOMP endpoint (numRestarts: ' + numRestartsLocal + ')')

        if (numRestartsLocal == 0) {
          dispatch(
            initializeLiveStreaming({
              token,
              roadRegulatorId,
              intersectionId,
              numRestarts: 0,
            })
          )
        } else {
          dispatch(
            setLiveDataRestartTimeoutId(
              setTimeout(() => {
                dispatch(setLiveDataRestart(numRestartsLocal + 1))
              }, numRestartsLocal * 2000)
            )
          )
        }
      } else {
        cleanUpLiveStreaming()
      }
    }

    client.onStompError = (frame) => {
      console.error('Live Streaming STOMP ERROR', frame)
    }

    client.onWebSocketClose = (frame) => {
      console.error(
        'Live Streaming STOMP WebSocket Close: ' +
          frame +
          ' (numRestarts: ' +
          numRestarts +
          ', wsClient: ' +
          wsClient +
          ')'
      )
      if (numRestarts < 5 && liveDataActive) {
        let numRestartsLocal = numRestarts
        if (Date.now() - connectionStartTime > 10000) {
          numRestartsLocal = 0
        }
        console.debug('Attempting to reconnect to STOMP endpoint (numRestarts: ' + numRestartsLocal + ')')

        if (numRestartsLocal == 0) {
          dispatch(
            initializeLiveStreaming({
              token,
              roadRegulatorId,
              intersectionId,
              numRestarts: 0,
            })
          )
        } else {
          dispatch(
            setLiveDataRestartTimeoutId(
              setTimeout(() => {
                dispatch(setLiveDataRestart(numRestartsLocal + 1))
              }, numRestartsLocal * 2000)
            )
          )
        }
      } else {
        dispatch(cleanUpLiveStreaming())
      }
    }

    client.onWebSocketError = (frame) => {
      // TODO: Consider restarting connection on error
      console.error('Live Streaming STOMP WebSocket Error', frame)
    }

    return client
  }
)

export const updateRenderedMapState = createAsyncThunk(
  'intersectionMapController/updateRenderedMapState',
  async (_, { getState, dispatch }) => {
    const currentState = getState() as RootState
    const timeFilterBsms = selectTimeFilterBsms(currentState)
    const spatSignalGroups = selectSpatSignalGroups(currentState)
    const mapSignalGroups = selectMapSignalGroups(currentState)
    const renderTimeInterval = selectRenderTimeInterval(currentState)
    const bsmData = selectBsmData(currentState)
    const surroundingEvents = selectSurroundingEvents(currentState)
    const surroundingNotifications = selectSurroundingNotifications(currentState)

    if (timeFilterBsms == false) {
      dispatch(setCurrentBsms(bsmData))
    }
    if (!mapSignalGroups || !spatSignalGroups) {
      console.debug('BSM Loading: No map or SPAT data', mapSignalGroups, spatSignalGroups)
      return
    }

    let currentSignalGroups: SpatSignalGroup[] | undefined
    let signalStateData: SignalStateFeatureCollection | undefined
    let spatTime: number | undefined

    // retrieve filtered SPATs
    let closestSignalGroup: { spat: SpatSignalGroup[]; datetime: number } | null = null
    for (const datetime in spatSignalGroups) {
      const datetimeNum = Number(datetime) / 1000 // milliseconds to seconds
      if (datetimeNum >= renderTimeInterval[0] && datetimeNum <= renderTimeInterval[1]) {
        if (
          closestSignalGroup === null ||
          Math.abs(datetimeNum - renderTimeInterval[1]) < Math.abs(closestSignalGroup.datetime - renderTimeInterval[1])
        ) {
          closestSignalGroup = { datetime: datetimeNum, spat: spatSignalGroups[datetime] }
        }
      }
    }
    if (closestSignalGroup !== null) {
      currentSignalGroups = closestSignalGroup.spat
      signalStateData = generateSignalStateFeatureCollection(mapSignalGroups!, closestSignalGroup.spat)
      spatTime = closestSignalGroup.datetime
    }

    // retrieve filtered BSMs
    if (timeFilterBsms !== false) {
      dispatch(updateTrailedBsmData())
    }

    const filteredEvents: MessageMonitor.Event[] = surroundingEvents.filter(
      (event) =>
        event.eventGeneratedAt / 1000 >= renderTimeInterval[0] && event.eventGeneratedAt / 1000 <= renderTimeInterval[1]
    )

    const filteredNotifications: MessageMonitor.Notification[] = surroundingNotifications.filter(
      (notification) =>
        notification.notificationGeneratedAt / 1000 >= renderTimeInterval[0] &&
        notification.notificationGeneratedAt / 1000 <= renderTimeInterval[1]
    )

    return {
      currentSignalGroups: closestSignalGroup?.spat,
      signalStateData: closestSignalGroup
        ? generateSignalStateFeatureCollection(mapSignalGroups!, closestSignalGroup?.spat)
        : undefined,
      spatTime: closestSignalGroup?.datetime,
      filteredSurroundingEvents: filteredEvents,
      filteredSurroundingNotifications: filteredNotifications,
    }
  },
  {
    condition: (_, { getState }) =>
      Boolean(
        (selectMapSignalGroups(getState() as RootState)?.features.length != 0 &&
          selectSpatSignalGroups(getState() as RootState)) ||
          selectBsmData(getState() as RootState)?.features.length != 0
      ),
  }
)

const compareQueryParams = (oldParams: MAP_QUERY_PARAMS, newParams: MAP_QUERY_PARAMS) => {
  return (
    oldParams.startDate.getTime() != newParams.startDate.getTime() ||
    oldParams.endDate.getTime() != newParams.endDate.getTime() ||
    oldParams.eventDate.getTime() != newParams.eventDate.getTime() ||
    oldParams.vehicleId != newParams.vehicleId ||
    oldParams.intersectionId != newParams.intersectionId ||
    oldParams.roadRegulatorId != newParams.roadRegulatorId ||
    oldParams.isDefault != newParams.isDefault
  )
}

const generateRenderTimeInterval = (startDate: Date, sliderValue: number, timeWindowSeconds: number) => {
  const startTime = startDate.getTime() / 1000

  const filteredStartTime = startTime + sliderValue / 10 - timeWindowSeconds
  const filteredEndTime = startTime + sliderValue / 10

  return [filteredStartTime, filteredEndTime]
}

export const downloadMapData = createAsyncThunk(
  'intersectionMapController/downloadMapData',
  async (_, { getState }) => {
    const currentState = getState() as RootState
    const rawData = selectRawData(currentState)!
    const queryParams = selectQueryParams(currentState)

    return downloadAllData(rawData, queryParams)
  },
  {
    condition: (_, { getState }) =>
      selectToken(getState() as RootState) != undefined &&
      selectQueryParams(getState() as RootState).intersectionId != undefined &&
      selectQueryParams(getState() as RootState).roadRegulatorId != undefined,
  }
)

export const renderRsuData = createAsyncThunk(
  'intersectionMapController/renderRsuData',
  async (_, { getState, dispatch }) => {
    const currentState = getState() as RootState
    const rsuMapData = selectRsuMapData(currentState)

    dispatch(resetMapView())

    dispatch(
      renderEntireMap({
        currentMapData: [rsuMapData as unknown as ProcessedMap],
        currentSpatData: [],
        currentBsmData: { type: 'FeatureCollection', features: [] },
      })
    )

    return
  },
  {
    condition: (_, { getState }) =>
      selectToken(getState() as RootState) != undefined &&
      selectQueryParams(getState() as RootState).intersectionId != undefined &&
      selectQueryParams(getState() as RootState).roadRegulatorId != undefined,
  }
)

export const intersectionMapControllerSlice = createSlice({
  name: 'intersectionMapController',
  initialState: {
    loading: false,
    value: initialState,
  },
  reducers: {},
  extraReducers: (builder) => {
    builder
  },
})

export const selectLoading = (state: RootState) => state.intersectionMapController.loading

export const selectSourceData = (state: RootState) => state.intersectionMapController.value.sourceData
export const selectInitialSourceDataType = (state: RootState) =>
  state.intersectionMapController.value.initialSourceDataType
export const selectSourceDataType = (state: RootState) => state.intersectionMapController.value.sourceDataType
export const selectIntersectionId = (state: RootState) => state.intersectionMapController.value.intersectionId
export const selectRoadRegulatorId = (state: RootState) => state.intersectionMapController.value.roadRegulatorId
export const selectLoadOnNull = (state: RootState) => state.intersectionMapController.value.loadOnNull
export const selectQueryParams = (state: RootState) => state.intersectionMapController.value.queryParams
export const selectPlaybackModeActive = (state: RootState) => state.intersectionMapController.value.playbackModeActive
export const selectTimeWindowSeconds = (state: RootState) => state.intersectionMapController.value.timeWindowSeconds
export const selectRenderTimeInterval = (state: RootState) => state.intersectionMapController.value.renderTimeInterval
export const selectRawData = (state: RootState) => state.intersectionMapController.value.rawData
export const selectImportedMessageData = (state: RootState) => state.intersectionMapController.value.importedMessageData
export const selectTotalBufferedMapData = (state: RootState) =>
  state.intersectionMapController.value.totalBufferedMapData
export const selectTotalBufferedSpatData = (state: RootState) =>
  state.intersectionMapController.value.totalBufferedSpatData
export const selectTotalBufferedBsmData = (state: RootState) =>
  state.intersectionMapController.value.totalBufferedBsmData
export const selectLiveDataActive = (state: RootState) => state.intersectionMapController.value.liveDataActive
export const selectLiveDataRestart = (state: RootState) => state.intersectionMapController.value.liveDataRestart
export const selectLiveDataRestartTimeoutId = (state: RootState) =>
  state.intersectionMapController.value.liveDataRestartTimeoutId
export const selectPullInitialDataAbortControllers = (state: RootState) =>
  state.intersectionMapController.value.pullInitialDataAbortControllers
export const selectAbortAllFutureRequests = (state: RootState) =>
  state.intersectionMapController.value.abortAllFutureRequests
export const selectDecoderModeEnabled = (state: RootState) => state.intersectionMapController.value.decoderModeEnabled
export const selectTimeFilterBsms = (state: RootState) => !state.intersectionMapController.value.decoderModeEnabled

export const {} = intersectionMapControllerSlice.actions

export default intersectionMapControllerSlice.reducer

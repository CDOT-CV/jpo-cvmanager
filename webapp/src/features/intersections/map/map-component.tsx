import React, { useState, useEffect, useMemo } from 'react'
import Map, { Source, Layer, MapRef } from 'react-map-gl'

import { Container, Col } from 'reactstrap'

import { Paper, Box } from '@mui/material'

import ControlPanel from './control-panel'
import { SidePanel } from './side-panel'
import { CustomPopup } from './popup'
import { useDispatch, useSelector } from 'react-redux'
import { selectToken } from '../../../generalSlices/userSlice'
import {
  selectBsmLayerStyle,
  selectConnectingLanesLabelsLayerStyle,
  selectConnectingLanesLayerStyle,
  selectMapMessageLabelsLayerStyle,
  selectMapMessageLayerStyle,
  selectMarkerLayerStyle,
  selectSignalStateLayerStyle,
  selectSrmLayerStyle,
} from './map-layer-style-slice'
import {
  MAP_PROPS,
  cleanUpLiveStreaming,
  clearHoveredFeature,
  clearSelectedFeature,
  generateQueryParams,
  incrementSliderValue,
  initializeLiveStreaming,
  onMapClick,
  onMapMouseEnter,
  onMapMouseLeave,
  onMapMouseMove,
  pullInitialData,
  renderRsuData,
  resetInitialDataAbortControllers,
  selectAllInteractiveLayerIds,
  selectBsmData,
  selectConnectingLanes,
  selectCurrentBsmData,
  selectCurrentBsms,
  selectCurrentMapData,
  selectCurrentSignalGroups,
  selectCurrentSpatData,
  selectCursor,
  selectDecoderModeEnabled,
  selectFilteredSurroundingEvents,
  selectFilteredSurroundingNotifications,
  selectHoveredFeature,
  selectLaneLabelsVisible,
  selectLiveDataActive,
  selectLiveDataRestart,
  selectLiveDataRestartTimeoutId,
  selectLoadInitialDataTimeoutId,
  selectMapData,
  selectMapSignalGroups,
  selectPlaybackModeActive,
  selectQueryParams,
  selectRenderTimeInterval,
  selectSelectedFeature,
  selectShowPopupOnHover,
  selectSigGroupLabelsVisible,
  selectSignalStateData,
  selectSliderValue,
  selectSpatSignalGroups,
  selectTimeWindowSeconds,
  selectViewState,
  setLoadInitialdataTimeoutId,
  setMapProps,
  setMapRef,
  setRawData,
  setViewState,
  updateQueryParams,
  updateRenderTimeInterval,
  updateRenderedMapState,
} from './map-slice'
import EnvironmentVars from '../../../EnvironmentVars'
import {
  addConnections,
  createMarkerForNotification,
  generateSignalStateFeatureCollection,
  parseMapSignalGroups,
  parseSpatSignalGroups,
} from './utilities/message-utils'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../../store'
import { MapLegend } from './map-legend'
import { selectSelectedSrm } from '../../../generalSlices/rsuSlice'
import mbStyle from '../../../styles/intersectionMapStyle.json'
import DecoderEntryDialog from '../decoder/decoder-entry-dialog'
import PcapUploadDialog from '../decoder/pcap-upload-dialog'
import { useLocation } from 'react-router-dom'
import IntersectionBaseMap from './intersection-base-map'

type timestamp = {
  timestamp: number
}

const IntersectionMap = (props: MAP_PROPS) => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const location = useLocation()

  // userSlice
  const authToken = useSelector(selectToken)

  const mapMessageLayerStyle = useSelector(selectMapMessageLayerStyle)
  const mapMessageLabelsLayerStyle = useSelector(selectMapMessageLabelsLayerStyle)
  const connectingLanesLayerStyle = useSelector(selectConnectingLanesLayerStyle)
  const connectingLanesLabelsLayerStyle = useSelector(selectConnectingLanesLabelsLayerStyle)
  const markerLayerStyle = useSelector(selectMarkerLayerStyle)
  const srmLayerStyle = useSelector(selectSrmLayerStyle)
  const bsmLayerStyle = useSelector(selectBsmLayerStyle)
  const signalStateLayerStyle = useSelector(selectSignalStateLayerStyle)

  const selectedSrm = useSelector(selectSelectedSrm)

  const allInteractiveLayerIds = useSelector(selectAllInteractiveLayerIds)
  const queryParams = useSelector(selectQueryParams)
  const currentMapData = useSelector(selectCurrentMapData)
  const currentSpatData = useSelector(selectCurrentSpatData)
  const currentBsmData = useSelector(selectCurrentBsmData)
  const mapData = useSelector(selectMapData)
  const bsmData = useSelector(selectBsmData)
  const mapSignalGroups = useSelector(selectMapSignalGroups)
  const signalStateData = useSelector(selectSignalStateData)
  const spatSignalGroups = useSelector(selectSpatSignalGroups)
  const currentSignalGroups = useSelector(selectCurrentSignalGroups)
  const currentBsms = useSelector(selectCurrentBsms)
  const connectingLanes = useSelector(selectConnectingLanes)
  const filteredSurroundingEvents = useSelector(selectFilteredSurroundingEvents)
  const filteredSurroundingNotifications = useSelector(selectFilteredSurroundingNotifications)
  const viewState = useSelector(selectViewState)
  const timeWindowSeconds = useSelector(selectTimeWindowSeconds)
  const sliderValue = useSelector(selectSliderValue)
  const renderTimeInterval = useSelector(selectRenderTimeInterval)
  const hoveredFeature = useSelector(selectHoveredFeature)
  const selectedFeature = useSelector(selectSelectedFeature)
  const sigGroupLabelsVisible = useSelector(selectSigGroupLabelsVisible)
  const laneLabelsVisible = useSelector(selectLaneLabelsVisible)
  const showPopupOnHover = useSelector(selectShowPopupOnHover)
  const cursor = useSelector(selectCursor)
  const loadInitialDataTimeoutId = useSelector(selectLoadInitialDataTimeoutId)
  const liveDataActive = useSelector(selectLiveDataActive)
  const playbackModeActive = useSelector(selectPlaybackModeActive)
  const liveDataRestartTimeoutId = useSelector(selectLiveDataRestartTimeoutId)
  const liveDataRestart = useSelector(selectLiveDataRestart)
  const decoderModeEnabled = useSelector(selectDecoderModeEnabled)

  const mapRef = React.useRef<MapRef>(null)
  const [bsmTrailLength, setBsmTrailLength] = useState<number>(5)

  useEffect(() => {
    return () => {
      dispatch(resetInitialDataAbortControllers())
    }
  }, [location.pathname, dispatch])

  useEffect(() => {
    dispatch(setMapProps(props))
  }, [props])

  // Increment sliderValue by 1 every second when playbackModeActive is true
  useEffect(() => {
    if (playbackModeActive) {
      const playbackPeriod = 100 //ms
      const playbackIncrement = Math.ceil(playbackPeriod / 100)
      const interval = setInterval(() => {
        dispatch(incrementSliderValue(playbackIncrement))
      }, 100)
      // Clear interval on component unmount
      return () => {
        clearInterval(interval)
      }
    }
    return () => {}
  }, [playbackModeActive])

  useEffect(() => {
    if (props.intersectionId != queryParams.intersectionId || props.roadRegulatorId != queryParams.roadRegulatorId) {
      dispatch(
        updateQueryParams({
          intersectionId: props.intersectionId,
          roadRegulatorId: props.roadRegulatorId,
        })
      )
      if (liveDataActive && authToken && props.roadRegulatorId && props.intersectionId) {
        cleanUpLiveStreaming()
        dispatch(
          initializeLiveStreaming({
            token: authToken,
            roadRegulatorId: props.roadRegulatorId,
            intersectionId: props.intersectionId,
          })
        )
      }
    }
  }, [props.intersectionId, props.roadRegulatorId])

  useEffect(() => {
    dispatch(
      updateQueryParams({
        ...generateQueryParams(props.sourceData, props.sourceDataType, decoderModeEnabled),
        intersectionId: props.intersectionId,
        roadRegulatorId: props.roadRegulatorId,
        resetTimeWindow: true,
      })
    )
  }, [props.sourceData])

  useEffect(() => {
    if (liveDataActive) {
      return
    }
    if (loadInitialDataTimeoutId) {
      clearTimeout(loadInitialDataTimeoutId)
    }
    const timeoutId = setTimeout(() => dispatch(pullInitialData()), 500)
    dispatch(setLoadInitialdataTimeoutId(timeoutId))
  }, [queryParams])

  useEffect(() => {
    dispatch(updateRenderedMapState())
  }, [bsmData, mapSignalGroups, renderTimeInterval, spatSignalGroups])

  useEffect(() => {
    dispatch(updateRenderTimeInterval())
  }, [sliderValue, queryParams, timeWindowSeconds])

  useEffect(() => {
    if (liveDataActive) {
      if (authToken && props.roadRegulatorId && props.intersectionId) {
        dispatch(
          initializeLiveStreaming({
            token: authToken,
            roadRegulatorId: props.roadRegulatorId,
            intersectionId: props.intersectionId,
          })
        )
        if (bsmTrailLength > 15) setBsmTrailLength(5)
        setRawData({})
      } else {
        console.error(
          'Did not attempt to update notifications. Access token missing:',
          authToken == null || authToken == undefined,
          'Intersection ID:',
          props.intersectionId,
          'Road Regulator ID:',
          props.roadRegulatorId
        )
      }
    } else {
      if (bsmTrailLength < 15) setBsmTrailLength(20)
      dispatch(cleanUpLiveStreaming())
    }
  }, [liveDataActive])

  useEffect(() => {
    if (liveDataRestart != -1 && liveDataRestart < 5 && liveDataActive) {
      if (authToken && props.roadRegulatorId && props.intersectionId) {
        dispatch(
          initializeLiveStreaming({
            token: authToken,
            roadRegulatorId: props.roadRegulatorId,
            intersectionId: props.intersectionId,
            numRestarts: liveDataRestart,
          })
        )
      }
    } else {
      dispatch(cleanUpLiveStreaming())
    }
  }, [liveDataRestart])

  useEffect(() => {
    const map = mapRef.current?.getMap()
    if (!map) return
    const images = [
      'traffic-light-icon-unknown',
      'traffic-light-icon-red-flashing',
      'traffic-light-icon-red-1',
      'traffic-light-icon-yellow-red-1',
      'traffic-light-icon-green-1',
      'traffic-light-icon-yellow-1',
    ]
    for (const image_name of images) {
      map.loadImage(`/icons/${image_name}.png`, (error, image) => {
        if (error) throw error
        if (!map.hasImage(image_name)) map.addImage(image_name, image, { sdf: true })
      })
    }
    if (mapRef.current) dispatch(setMapRef(mapRef))
  }, [mapRef])

  const renderMaps = useMemo(() => {
    return {
      type: 'FeatureCollection' as 'FeatureCollection',
      features: Object.values(currentMapData)
        .flatMap((v) => v?.mapFeatureCollection.features)
        .filter((f) => f !== undefined),
    }
  }, [currentMapData])

  const renderSpatConnectingLanes = useMemo(() => {
    return {
      type: 'FeatureCollection' as 'FeatureCollection',
      features: Object.entries(currentSpatData)
        .flatMap(([intersectionId, spat]) => {
          if (!spat) return null
          const signalGroup = Object.values(parseSpatSignalGroups([spat]))[0]
          const mapMessage = currentMapData[parseInt(intersectionId)]
          if (!mapMessage || !signalGroup) return null
          return addConnections(
            mapMessage.connectingLanesFeatureCollection,
            signalGroup,
            mapMessage.mapFeatureCollection
          )
        })
        .flatMap((v) => v?.features)
        .filter((f) => f !== null && f !== undefined),
    }
  }, [currentSpatData, currentMapData])

  const srmRenderData = useMemo(() => {
    return {
      type: 'FeatureCollection' as 'FeatureCollection',
      features: selectedSrm?.map((srm) => {
        return {
          type: 'Feature',
          geometry: {
            type: 'Point',
            coordinates: [srm.long, srm.lat],
          },
          properties: {
            requestId: srm.requestId,
            requestedId: srm.requestedId,
            status: srm.status,
            time: srm.time,
            role: srm.role,
          },
        }
      }),
    } as GeoJSON.FeatureCollection<GeoJSON.Point>
  }, [])

  const renderBsmData = useMemo(() => {
    return {
      type: 'FeatureCollection' as 'FeatureCollection',
      features: Object.values(currentBsmData)
        .filter((f) => f !== undefined)
        .flatMap((o) => Object.values(o))
        .filter((f) => f !== undefined)
        .map((feature) => ({
          ...feature,
          properties: {
            ...feature.properties,
            // , color: getBsmColor(feature)
          },
        })),
    }
  }, [currentBsmData])

  const renderNotificationData = useMemo(() => {
    if (props.sourceDataType !== 'notification' || !props.sourceData)
      return { type: 'FeatureCollection' as 'FeatureCollection', features: [] }
    const notification: MessageMonitor.Notification = props.sourceData as MessageMonitor.Notification
    const mapMessage = currentMapData[notification.intersectionID]
    if (!mapMessage) return { type: 'FeatureCollection' as 'FeatureCollection', features: [] }
    return createMarkerForNotification([0, 0], notification, mapMessage.mapFeatureCollection)
  }, [])

  const signalStateRenderData = useMemo(() => {
    return generateSignalStateFeatureCollection(mapSignalGroups!, closestSignalGroup.spat)
  }, [])

  return (
    <Container style={{ width: '100%', height: '100%', display: 'flex', padding: 0 }}>
      <Col className="mapContainer" style={{ overflow: 'hidden', width: '100%', height: '100%', position: 'relative' }}>
        <div
          style={{
            padding: '0px 0px 6px 12px',
            marginTop: '6px',
            marginLeft: '35px',
            position: 'absolute',
            zIndex: 10,
            top: 0,
            left: 0,
            width: 1200,
            // width: 'calc(100% - 500px)',
            borderRadius: '4px',
            fontSize: '16px',
            maxHeight: 'calc(100vh - 120px)',
            overflow: 'auto',
            scrollBehavior: 'auto',
          }}
        >
          <Box style={{ position: 'relative' }}>
            <Paper sx={{ pt: 1, pb: 1, opacity: 0.85 }}>
              <ControlPanel />
            </Paper>
          </Box>
        </div>
        <div
          style={{
            padding: '0px 0px 6px 12px',
            position: 'absolute',
            zIndex: 9,
            bottom: 0,
            left: 0,
            fontSize: '16px',
            overflow: 'auto',
            scrollBehavior: 'auto',
            width: '100%',
          }}
        >
          <Box style={{ position: 'relative' }}>
            <MapLegend />
          </Box>
        </div>

        <IntersectionBaseMap
          sourceData={props.sourceData}
          sourceDataType={props.sourceDataType}
          intersectionId={props.intersectionId}
          roadRegulatorId={props.roadRegulatorId}
          loadOnNull={props.loadOnNull}
        />
        <SidePanel
          laneInfo={connectingLanes}
          signalGroups={currentSignalGroups}
          bsms={currentBsms}
          events={filteredSurroundingEvents}
          notifications={filteredSurroundingNotifications}
          sourceData={props.sourceData}
          sourceDataType={props.sourceDataType}
        />
      </Col>
      <DecoderEntryDialog />
      <PcapUploadDialog />
    </Container>
  )
}

export default IntersectionMap

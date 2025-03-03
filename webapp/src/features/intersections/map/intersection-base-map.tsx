import React, { useEffect, useMemo } from 'react'
import Map, { Source, Layer, MapRef } from 'react-map-gl'

import { CustomPopup } from './popup'
import { useDispatch, useSelector } from 'react-redux'
import {
  selectBsmLayerStyle,
  selectConnectingLanesLabelsLayerStyle,
  selectConnectingLanesLayerStyle,
  selectMapMessageLabelsLayerStyle,
  selectMapMessageLayerStyle,
  selectMarkerLayerStyle,
  selectSignalStateLayerStyle,
} from './map-layer-style-slice'
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
import mbStyle from '../../../styles/intersectionMapStyle.json'
import {
  clearHoveredFeature,
  clearSelectedFeature,
  onMapClick,
  onMapMouseEnter,
  onMapMouseLeave,
  onMapMouseMove,
  selectAllInteractiveLayerIds,
  selectCurrentBsmMessages,
  selectCurrentMapMessages,
  selectCurrentNotificationMessages,
  selectCurrentSpatMessages,
  selectCursor,
  selectHoveredFeature,
  selectLayersVisible,
  selectSelectedFeature,
  selectShowPopupOnHover,
  selectViewState,
  setMapRef,
  setViewState,
} from './new/intersection-map-slice'

const IntersectionBaseMap = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()

  const mapRef = React.useRef<MapRef>(null)

  const layersVisible = useSelector(selectLayersVisible)
  const allInteractiveLayerIds = useSelector(selectAllInteractiveLayerIds)

  const viewState = useSelector(selectViewState)
  const cursor = useSelector(selectCursor)

  const currentMapMessages = useSelector(selectCurrentMapMessages)
  const currentSpatMessages = useSelector(selectCurrentSpatMessages)
  const currentBsmMessages = useSelector(selectCurrentBsmMessages)
  const currentNotificationMessages = useSelector(selectCurrentNotificationMessages)
  const showPopupOnHover = useSelector(selectShowPopupOnHover)
  const hoveredFeature = useSelector(selectHoveredFeature)
  const selectedFeature = useSelector(selectSelectedFeature)

  // map-layer-style-slice
  const mapMessageLayerStyle = useSelector(selectMapMessageLayerStyle)
  const connectingLanesLayerStyle = useSelector(selectConnectingLanesLayerStyle)
  const markerLayerStyle = useSelector(selectMarkerLayerStyle)
  const bsmLayerStyle = useSelector(selectBsmLayerStyle)
  const signalStateLayerStyle = useSelector(selectSignalStateLayerStyle)
  const mapMessageLabelsLayerStyle = useSelector(selectMapMessageLabelsLayerStyle)
  const connectingLanesLabelsLayerStyle = useSelector(selectConnectingLanesLabelsLayerStyle)

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
      features: Object.values(currentMapMessages)
        .flatMap((v) => v?.mapFeatureCollection.features)
        .filter((f) => f !== undefined),
    }
  }, [currentMapMessages])

  const renderSpatConnectingLanes = useMemo(() => {
    return {
      type: 'FeatureCollection' as 'FeatureCollection',
      features: Object.entries(currentSpatMessages)
        .flatMap(([intersectionId, spat]) => {
          if (!spat) return null
          const signalGroup = Object.values(parseSpatSignalGroups([spat]))[0]
          const mapMessage = currentMapMessages[parseInt(intersectionId)]
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
  }, [currentSpatMessages, currentMapMessages])

  //   const srmRenderData = useMemo(() => {
  //     return {
  //       type: 'FeatureCollection' as 'FeatureCollection',
  //       features: selectedSrm?.map((srm) => {
  //         return {
  //           type: 'Feature',
  //           geometry: {
  //             type: 'Point',
  //             coordinates: [srm.long, srm.lat],
  //           },
  //           properties: {
  //             requestId: srm.requestId,
  //             requestedId: srm.requestedId,
  //             status: srm.status,
  //             time: srm.time,
  //             role: srm.role,
  //           },
  //         }
  //       }),
  //     } as GeoJSON.FeatureCollection<GeoJSON.Point>
  //   }, [])

  const renderBsmData = useMemo(() => {
    return {
      type: 'FeatureCollection' as 'FeatureCollection',
      features: Object.values(currentBsmMessages)
        .filter((f) => f !== undefined)
        .flatMap((o) => Object.values(o))
        .filter((f) => f !== undefined)
        .map((feature) => ({
          ...feature,
          properties: {
            ...feature.properties,
          },
        })),
    }
  }, [currentBsmMessages])

  const renderNotificationData = useMemo(() => {
    return {
      type: 'FeatureCollection' as 'FeatureCollection',
      features: currentNotificationMessages.flatMap((notification) => {
        const mapMessage = currentMapMessages[notification.intersectionID]
        if (!mapMessage) return { type: 'FeatureCollection' as 'FeatureCollection', features: [] }
        return createMarkerForNotification([0, 0], notification, mapMessage.mapFeatureCollection).features
      }),
    }
  }, [])

  const spatSignalGroups = useMemo(() => {
    return Object.entries(currentSpatMessages).map(([intersectionId, spat]) => {
      return spat.states.map((state) => {
        return {
          signalGroup: state.signalGroup,
          state: state.stateTimeSpeed?.[0]?.eventState as SignalState,
        }
      })
    })
  }, [])

  const signalStateData = useMemo(() => {
    return {
      type: 'FeatureCollection' as 'FeatureCollection',
      features: Object.entries(spatSignalGroups).flatMap(
        ([key, signalGroups]) =>
          generateSignalStateFeatureCollection(parseMapSignalGroups(currentMapMessages[key]), signalGroups).features
      ),
    }
  }, [])

  return (
    <Map
      {...viewState}
      ref={mapRef}
      mapStyle={mbStyle as mapboxgl.Style}
      mapboxAccessToken={EnvironmentVars.MAPBOX_TOKEN}
      attributionControl={true}
      customAttribution={['<a href="https://www.cotrip.com/" target="_blank">© CDOT</a>']}
      styleDiffing
      style={{ width: '100%', height: '100%' }}
      onMove={(evt) => dispatch(setViewState(evt.viewState))}
      onClick={(e) => dispatch(onMapClick({ point: e.point, lngLat: e.lngLat }))}
      interactiveLayerIds={allInteractiveLayerIds}
      cursor={cursor}
      onMouseMove={(e) => dispatch(onMapMouseMove({ features: e.features, lngLat: e.lngLat }))}
      onMouseEnter={(e) => dispatch(onMapMouseEnter({ features: e.features, lngLat: e.lngLat }))}
      onMouseLeave={(e) => dispatch(onMapMouseLeave())}
      onLoad={(e: mapboxgl.MapboxEvent<undefined>) => {
        const map = e.target
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
            if (!map.hasImage(image_name)) map.addImage(image_name, image)
          })
        }
        if (mapRef.current) dispatch(setMapRef(mapRef))
      }}
    >
      <Source type="geojson" data={renderMaps}>
        <Layer {...mapMessageLayerStyle} />
      </Source>
      <Source type="geojson" data={renderSpatConnectingLanes}>
        <Layer {...connectingLanesLayerStyle} />
      </Source>
      {/* <Source type="geojson" data={srmRenderData}>
        <Layer {...srmLayerStyle} />
      </Source> */}
      <Source type="geojson" data={renderNotificationData}>
        <Layer {...markerLayerStyle} />
      </Source>
      <Source type="geojson" data={renderBsmData}>
        <Layer {...bsmLayerStyle} />
      </Source>
      {layersVisible['signal-states'] ?? (
        <Source type="geojson" data={signalStateData}>
          <Layer {...signalStateLayerStyle} />
        </Source>
      )}
      {layersVisible['map-message-labels'] ?? (
        <Source
          type="geojson"
          data={{
            type: 'FeatureCollection',
            features: [],
          }}
        >
          <Layer {...mapMessageLabelsLayerStyle} />
        </Source>
      )}
      <Source type="geojson" data={renderSpatConnectingLanes}>
        <Layer {...connectingLanesLabelsLayerStyle} />
      </Source>
      {selectedFeature && (
        <CustomPopup selectedFeature={selectedFeature} onClose={() => dispatch(clearSelectedFeature())} />
      )}
      {showPopupOnHover && hoveredFeature && !selectedFeature && (
        <CustomPopup selectedFeature={hoveredFeature} onClose={() => dispatch(clearHoveredFeature())} />
      )}
    </Map>
  )
}

export default IntersectionBaseMap

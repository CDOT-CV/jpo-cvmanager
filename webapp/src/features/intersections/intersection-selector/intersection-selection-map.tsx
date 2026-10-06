import React, { useCallback, useEffect, useMemo, useRef } from 'react'
import Map, { MapRef, Popup } from 'react-map-gl'
import mbStyle from '../../../styles/intersectionMapStyle.json'

import { Container } from 'reactstrap'
import EnvironmentVars from '../../../EnvironmentVars'
import {
  selectIntersections,
  selectSelectedIntersection,
  setSelectedIntersection,
} from '../../../generalSlices/intersectionSlice'
import { useDispatch, useSelector } from 'react-redux'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../../store'
import {
  INTERSECTION_ICON_ID,
  INTERSECTION_POINT_LAYER_ID,
  IntersectionMapLayer,
} from '../../../components/map-layers/IntersectionMapLayer'

const getBoundsForIntersections = (
  selectedIntersection: IntersectionReferenceData | undefined,
  intersections: IntersectionReferenceData[]
) => {
  let bounds = {
    xMin: -105.0907089,
    xMax: -105.0907089,
    yMin: 39.587905,
    yMax: 39.587905,
  }
  if (selectedIntersection != undefined && selectedIntersection.latitude != 0) {
    bounds = {
      xMin: selectedIntersection.longitude,
      xMax: selectedIntersection.longitude,
      yMin: selectedIntersection.latitude,
      yMax: selectedIntersection.latitude,
    }
  } else if (intersections.length >= 1 && intersections[0].latitude != 0) {
    bounds = {
      xMin: intersections[0].longitude,
      xMax: intersections[0].longitude,
      yMin: intersections[0].latitude,
      yMax: intersections[0].latitude,
    }
  }

  let latitude: number, longitude: number

  for (let i = 0; i < intersections.length; i++) {
    longitude = intersections[i].longitude
    latitude = intersections[i].latitude
    if (longitude >= bounds.xMin && longitude <= bounds.xMax && latitude <= bounds.yMin && latitude >= bounds.yMax) {
      if (bounds.xMin === undefined) {
        bounds = {
          xMin: longitude,
          xMax: longitude,
          yMin: latitude,
          yMax: latitude,
        }
      } else {
        bounds.xMin = longitude < bounds.xMin ? longitude : bounds.xMin
        bounds.xMax = longitude > bounds.xMax ? longitude : bounds.xMax
        bounds.yMin = latitude < bounds.yMin ? latitude : bounds.yMin
        bounds.yMax = latitude > bounds.yMax ? latitude : bounds.yMax
      }
    }
  }

  return [bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax]
}

const zoomToBounds = (mapRef: React.RefObject<MapRef>, bounds: number[], padding = 50) => {
  if (bounds) {
    const [long1, lat1, long2, lat2] = bounds
    mapRef?.current?.fitBounds(
      [
        [long1, lat1],
        [long2, lat2],
      ],
      {
        padding: {
          top: padding,
          bottom: padding,
          left: padding,
          right: padding + 300,
        },
        animate: true,
        duration: 1000,
        maxZoom: 15,
      }
    )
  }
}

const IntersectionMap = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()

  const intersections = useSelector(selectIntersections)
  const selectedIntersection = useSelector(selectSelectedIntersection)

  const initialViewState = {
    latitude: selectedIntersection?.latitude ?? 39.587905,
    longitude: selectedIntersection?.longitude ?? -105.0907089,
    zoom: 11,
  }
  const mapRef = useRef<MapRef>(null)
  const intersectionIconLoadingRef = useRef(false)

  const viewBounds = getBoundsForIntersections(selectedIntersection, intersections)
  useEffect(() => {
    zoomToBounds(mapRef, viewBounds)
  }, [])

  const intersectionPointData = useMemo<GeoJSON.FeatureCollection<GeoJSON.Point>>(
    () => ({
      type: 'FeatureCollection',
      features: intersections
        .filter((intersection) => intersection.latitude != 0)
        .map((intersection) => ({
          type: 'Feature',
          id: intersection.intersectionID,
          properties: {
            intersectionId: intersection.intersectionID,
            intersectionName: intersection.intersectionID,
          },
          geometry: {
            type: 'Point',
            coordinates: [intersection.longitude, intersection.latitude],
          },
        })),
    }),
    [intersections]
  )

  const ensureIntersectionIcon = useCallback(() => {
    const map = mapRef.current
    if (!map || map.hasImage(INTERSECTION_ICON_ID) || intersectionIconLoadingRef.current) return

    intersectionIconLoadingRef.current = true
    map.loadImage('/icons/intersection_icon.png', (error, image) => {
      intersectionIconLoadingRef.current = false
      if (error) {
        console.error('Unable to load intersection map icon:', error)
        return
      }
      if (image && !map.hasImage(INTERSECTION_ICON_ID)) {
        map.addImage(INTERSECTION_ICON_ID, image)
      }
    })
  }, [])

  return (
    <Container fluid={true} style={{ width: '100%', height: '100%', display: 'flex' }}>
      <Map
        initialViewState={initialViewState}
        ref={mapRef}
        mapStyle={mbStyle as mapboxgl.Style}
        mapboxAccessToken={EnvironmentVars.MAPBOX_TOKEN}
        attributionControl={true}
        customAttribution={['<a href="https://www.cotrip.com/" target="_blank">© CDOT</a>']}
        styleDiffing
        style={{ width: '100%', height: '100%' }}
        interactiveLayerIds={[INTERSECTION_POINT_LAYER_ID]}
        onClick={(event) => {
          const clickedIntersection = event.features?.find(
            (feature) => feature.layer.id === INTERSECTION_POINT_LAYER_ID
          )
          const intersectionId = Number(clickedIntersection?.properties?.intersectionId)
          if (clickedIntersection && Number.isFinite(intersectionId)) {
            event.originalEvent.preventDefault()
            dispatch(setSelectedIntersection(intersectionId))
          }
        }}
        onLoad={() => {
          ensureIntersectionIcon()
          zoomToBounds(mapRef, viewBounds)
        }}
        onStyleData={ensureIntersectionIcon}
      >
        <IntersectionMapLayer data={intersectionPointData} iconWidth={70} labelTextSize={20} />
        {selectedIntersection && (
          <Popup
            latitude={selectedIntersection.latitude}
            longitude={selectedIntersection.longitude}
            closeOnClick={false}
            closeButton={false}
          >
            <div>SELECTED {selectedIntersection.intersectionID}</div>
          </Popup>
        )}
      </Map>
    </Container>
  )
}

export default IntersectionMap

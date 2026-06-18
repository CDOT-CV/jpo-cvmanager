import { useEffect, useMemo, useState } from 'react'
import { Alert, Box, Container, Typography } from '@mui/material'
import { AnyAction } from '@reduxjs/toolkit'
import { useDispatch, useSelector } from 'react-redux'
import { useSearchParams } from 'react-router-dom'
import IntersectionMap from '../../../features/intersections/map/map-component'
import {
  resetMapView,
  selectCurrentSignalGroups,
  selectMapData,
  selectMapRef,
  selectSignalStateData,
  setDecoderModeEnabled,
} from '../../../features/intersections/map/map-slice'
import { setSelectedIntersectionId } from '../../../generalSlices/intersectionSlice'
import { RootState } from '../../../store'
import { headerTabHeight } from '../../../styles/index'
import { ThunkDispatch } from 'redux-thunk'

type ScreenshotBootstrapPayload = {
  intersectionId?: number
  sourceData: {
    map: ProcessedMap[]
    spat: ProcessedSpat[]
    bsm?: BsmFeatureCollection
  }
  options?: {
    requireSignalState?: boolean
  }
}

type ScreenshotSourceData = {
  map: ProcessedMap[]
  spat: ProcessedSpat[]
  bsm: BsmFeatureCollection
}

type LoadedBootstrapPayload = {
  intersectionId: number
  sourceData: ScreenshotSourceData
  options: {
    requireSignalState: boolean
  }
}

const emptyBsmCollection = (): BsmFeatureCollection => ({
  type: 'FeatureCollection',
  features: [],
})

const getIntersectionId = (payload: ScreenshotBootstrapPayload): number | undefined => {
  return (
    payload.intersectionId ??
    payload.sourceData.map.at(-1)?.properties.intersectionId ??
    payload.sourceData.spat.at(-1)?.intersectionId
  )
}

const loadBootstrapPayload = (bootstrapKey: string | null): LoadedBootstrapPayload => {
  if (!bootstrapKey) {
    throw new Error('Missing required bootstrapKey query parameter.')
  }

  const rawPayload = window.sessionStorage.getItem(bootstrapKey)
  if (!rawPayload) {
    throw new Error(`No screenshot bootstrap payload found for key "${bootstrapKey}".`)
  }

  const parsedPayload = JSON.parse(rawPayload) as ScreenshotBootstrapPayload
  if (!parsedPayload.sourceData || !Array.isArray(parsedPayload.sourceData.map) || !Array.isArray(parsedPayload.sourceData.spat)) {
    throw new Error('Screenshot bootstrap payload is missing MAP/SPaT source data.')
  }

  const intersectionId = getIntersectionId(parsedPayload)
  if (intersectionId == undefined) {
    throw new Error('Unable to determine intersection ID from screenshot bootstrap payload.')
  }

  return {
    intersectionId,
    sourceData: {
      map: parsedPayload.sourceData.map,
      spat: parsedPayload.sourceData.spat,
      bsm: parsedPayload.sourceData.bsm ?? emptyBsmCollection(),
    },
    options: {
      requireSignalState:
        parsedPayload.options?.requireSignalState ?? parsedPayload.sourceData.spat.length > 0,
    },
  }
}

const IntersectionScreenshotPage = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const [searchParams] = useSearchParams()
  const bootstrapKey = searchParams.get('bootstrapKey')

  const [bootstrapPayload, setBootstrapPayload] = useState<LoadedBootstrapPayload | undefined>(undefined)
  const [bootstrapError, setBootstrapError] = useState<string | undefined>(undefined)
  const [mapLoaded, setMapLoaded] = useState(false)

  const mapData = useSelector(selectMapData)
  const currentSignalGroups = useSelector(selectCurrentSignalGroups)
  const signalStateData = useSelector(selectSignalStateData)
  const mapRef = useSelector(selectMapRef)

  useEffect(() => {
    setBootstrapPayload(undefined)
    setBootstrapError(undefined)
    setMapLoaded(false)
    document.body.dataset.cvManagerScreenshotStatus = 'loading'

    try {
      const payload = loadBootstrapPayload(bootstrapKey)
      setBootstrapPayload(payload)
      setBootstrapError(undefined)

      dispatch(resetMapView())
      dispatch(setDecoderModeEnabled(true))
      dispatch(setSelectedIntersectionId(payload.intersectionId))
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to load screenshot bootstrap payload.'
      setBootstrapError(message)
    }

    return () => {
      dispatch(resetMapView())
      dispatch(setDecoderModeEnabled(false))
      document.body.dataset.cvManagerScreenshotStatus = 'idle'
      delete document.body.dataset.cvManagerScreenshotError
      delete document.body.dataset.cvManagerScreenshotIntersectionId
    }
  }, [bootstrapKey, dispatch])

  useEffect(() => {
    if (!bootstrapPayload) {
      return
    }

    const intervalId = window.setInterval(() => {
      const loaded = Boolean(mapRef?.current?.getMap()?.loaded())
      setMapLoaded((currentLoaded) => (currentLoaded === loaded ? currentLoaded : loaded))
    }, 100)

    return () => {
      window.clearInterval(intervalId)
    }
  }, [bootstrapPayload, mapRef])

  const ready = useMemo(() => {
    if (!bootstrapPayload || !mapData || !mapLoaded) {
      return false
    }

    if (!bootstrapPayload.options.requireSignalState) {
      return true
    }

    return Boolean((currentSignalGroups?.length ?? 0) > 0 && (signalStateData?.features?.length ?? 0) > 0)
  }, [bootstrapPayload, currentSignalGroups, mapData, mapLoaded, signalStateData])

  useEffect(() => {
    if (bootstrapError) {
      document.body.dataset.cvManagerScreenshotStatus = 'error'
      document.body.dataset.cvManagerScreenshotError = bootstrapError
      return
    }

    document.body.dataset.cvManagerScreenshotStatus = ready ? 'ready' : 'loading'
    delete document.body.dataset.cvManagerScreenshotError
    if (bootstrapPayload) {
      document.body.dataset.cvManagerScreenshotIntersectionId = String(bootstrapPayload.intersectionId)
    }
  }, [bootstrapError, bootstrapPayload, ready])

  if (bootstrapError) {
    return (
      <div className="container">
        <Box component="main" sx={{ flexGrow: 1, py: 0 }}>
          <Container
            maxWidth={false}
            style={{
              width: '100%',
              height: `calc(100vh - ${headerTabHeight}px)`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 24,
            }}
          >
            <Alert severity="error">{bootstrapError}</Alert>
          </Container>
        </Box>
      </div>
    )
  }

  if (!bootstrapPayload) {
    return (
      <div className="container">
        <Box component="main" sx={{ flexGrow: 1, py: 0 }}>
          <Container
            maxWidth={false}
            style={{
              width: '100%',
              height: `calc(100vh - ${headerTabHeight}px)`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 24,
            }}
          >
            <Typography>Loading screenshot payload…</Typography>
          </Container>
        </Box>
      </div>
    )
  }

  return (
    <div className="container">
      <Box component="main" sx={{ flexGrow: 1, py: 0 }}>
        <Container
          maxWidth={false}
          style={{
            width: '100%',
            height: `calc(100vh - ${headerTabHeight}px)`,
            display: 'flex',
            position: 'relative',
            padding: 0,
          }}
        >
          <IntersectionMap
            sourceData={bootstrapPayload.sourceData}
            sourceDataType={undefined}
            intersectionId={bootstrapPayload.intersectionId}
            loadOnNull={false}
          />
        </Container>
      </Box>
    </div>
  )
}

export default IntersectionScreenshotPage

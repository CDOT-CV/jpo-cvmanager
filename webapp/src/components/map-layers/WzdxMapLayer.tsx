import React from 'react'
import { LineLayer, SymbolLayer } from 'mapbox-gl'
import { Layer, Source } from 'react-map-gl'

const WZDX_SOURCE_ID = 'wzdx-source'
export const WZDX_LINE_LAYER_ID = 'wzdx-layer'
export const WZDX_POINT_LAYER_ID = 'wzdx-points'
export const WZDX_ICON_ID = 'wzdx-icon'

const wzdxLineLayer: LineLayer = {
  id: WZDX_LINE_LAYER_ID,
  type: 'line',
  paint: {
    'line-color': '#F29543',
    'line-width': 8,
  },
}

const wzdxPointLayer: SymbolLayer = {
  id: WZDX_POINT_LAYER_ID,
  type: 'symbol',
  source: WZDX_SOURCE_ID,
  layout: {
    'symbol-placement': 'line-center',
    'icon-image': WZDX_ICON_ID,
    // 40px wide
    'icon-size': 40 / 256,
    'icon-allow-overlap': true,
    'icon-ignore-placement': true,
    'icon-rotation-alignment': 'viewport',
  },
}

interface WzdxMapLayerProps {
  data: GeoJSON.FeatureCollection<GeoJSON.LineString>
}

export const WzdxMapLayer = React.memo(({ data }: WzdxMapLayerProps) => (
  <Source id={WZDX_SOURCE_ID} type="geojson" data={data} generateId>
    <Layer {...wzdxLineLayer} />
    <Layer {...wzdxPointLayer} />
  </Source>
))

WzdxMapLayer.displayName = 'WzdxMapLayer'

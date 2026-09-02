import React from 'react'
import { SymbolLayer } from 'mapbox-gl'
import { Layer, Source } from 'react-map-gl'

const INTERSECTION_SOURCE_ID = 'intersection-source'
export const INTERSECTION_POINT_LAYER_ID = 'intersection-points'
export const INTERSECTION_ICON_ID = 'intersection-icon'

const intersectionLabelsLayer: SymbolLayer = {
  id: 'intersection-labels',
  type: 'symbol',
  layout: {
    'text-field': ['to-string', ['get', 'intersectionName']],
    'text-size': 16,
    'text-offset': [0, 2],
    'text-variable-anchor': ['top', 'left', 'right', 'bottom'],
    'text-allow-overlap': true,
    'icon-text-fit': 'both',
  },
  paint: {
    'text-color': '#000000',
    'text-halo-color': '#ffffff',
    'text-halo-width': 5,
  },
}

const intersectionPointLayer: SymbolLayer = {
  id: INTERSECTION_POINT_LAYER_ID,
  type: 'symbol',
  source: INTERSECTION_SOURCE_ID,
  layout: {
    'icon-image': INTERSECTION_ICON_ID,
    // 40px-wide
    'icon-size': 40 / 700,
    'icon-allow-overlap': true,
    'icon-ignore-placement': true,
  },
}

interface IntersectionMapLayerProps {
  data: GeoJSON.FeatureCollection<GeoJSON.Point>
}

export const IntersectionMapLayer = React.memo(({ data }: IntersectionMapLayerProps) => (
  <Source id={INTERSECTION_SOURCE_ID} type="geojson" data={data}>
    <Layer {...intersectionLabelsLayer} />
    <Layer {...intersectionPointLayer} />
  </Source>
))

IntersectionMapLayer.displayName = 'IntersectionMapLayer'

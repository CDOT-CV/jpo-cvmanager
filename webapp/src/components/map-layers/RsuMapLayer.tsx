import React from 'react'
import { CircleLayer } from 'mapbox-gl'
import { Layer, Source } from 'react-map-gl'

const RSU_SOURCE_ID = 'rsu-source'
export const RSU_POINT_LAYER_ID = 'rsu-points'

const rsuPointLayer: CircleLayer = {
  id: RSU_POINT_LAYER_ID,
  type: 'circle',
  paint: {
    'circle-radius': 7.5,
    'circle-color': ['get', 'display_color'],
  },
}

interface RsuMapLayerProps {
  data: GeoJSON.FeatureCollection<GeoJSON.Point>
}

export const RsuMapLayer = React.memo(({ data }: RsuMapLayerProps) => (
  <Source id={RSU_SOURCE_ID} type="geojson" data={data}>
    <Layer {...rsuPointLayer} />
  </Source>
))

RsuMapLayer.displayName = 'RsuMapLayer'

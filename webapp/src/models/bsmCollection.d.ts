type BsmFeatureCollection = {
  type: 'FeatureCollection'
  features: BsmFeature[]
}

type BsmFeature = {
  type: 'Feature'
  properties: J2735BsmCoreData & bsmReceivedAt
  geometry: PointGeometry
}

type bsmReceivedAt = {
  odeReceivedAt: number
}

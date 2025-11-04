type ProcessedSrmFeature = {
  type: 'Feature'
  geometry: {
    type: 'Point'
    coordinates: [number, number]
  }
  properties: ProcessedSrmProperties
}

type ProcessedSrmProperties = {
  schemaVersion: number
  messageType: 'SRM'
  asn1: string
  odeReceivedAt: string
  odeReceivedAtEpochSeconds: number
  timeStamp: string
  originIp: string
}

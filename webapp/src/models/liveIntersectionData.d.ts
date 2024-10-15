type IntersectionDataType = 'bsm' | 'map' | 'spat'

type liveIntersectionData = {
  type: IntersectionDataType
  rcv_ts: number
  update_ts: number
  payload: ProcessedMap | ProcessedSpat | BsmFeature
}

type liveMap = {
  type: 'map'
  rcv_ts: number
  update_ts: number
  payload: ProcessedMap
}

type liveSpat = {
  type: 'spat'
  rcv_ts: number
  update_ts: number
  payload: ProcessedSpat
}

type liveBsm = {
  type: 'bsm'
  rcv_ts: number
  update_ts: number
  payload: BsmFeature
}

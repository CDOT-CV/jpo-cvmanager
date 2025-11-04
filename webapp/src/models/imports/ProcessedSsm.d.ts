type ProcessedSsm = {
  schemaVersion: number
  messageType: 'SSM'
  asn1: string
  odeReceivedAt: string
  odeReceivedAtEpochSeconds: number
  originIp: string
  timeStamp: string
  timeStampEpochSeconds: number
  sequenceNumber?: number
  statusSequenceNumber?: number
  region?: number
  intersectionId?: number
  statusList?: ProcessedSignalStatus[]
  validationMessages: ProcessedValidationMessage[]
}

type ProcessedSignalStatus = {
  vehicleID?: string
  requestID?: number
  requesterSequenceNumber?: number
  requesterRole?: ProcessedBasicVehicleRole
  requesterSubrole?: ProcessedRequestSubRole
  requestImportanceLevel?: ProcessedRequestImportanceLevel
  requesterIso3833VehicleType?: number
  requesterHpmsType?: ProcessedVehicleType
  inboundOnLaneID?: number
  inboundOnApproachID?: number // Will not be used in CV Manager
  inboundOnLaneConnectionID?: number
  outboundOnLaneID?: number
  outboundOnApproachID?: number // Will not be used in CV Manager
  outboundOnLaneConnectionID?: number
  estimatedTimeOfArrival?: string
  estimatedTimeOfArrivalDurationSeconds?: number // Duration in seconds
  status: ProcessedPrioritizationResponseStatus
}

type SsmInfo = {
  requestInfo: SsmRequesterInfo
  timeStampEpochSeconds: number
  status: ProcessedPrioritizationResponseStatus
  sequenceNumber?: number
  statusSequenceNumber?: number
  requestID?: number
  inboundLaneID?: number
  inboundLaneConnectionID?: number
  outboundLaneID?: number
  outboundLaneConnectionID?: number
  estimatedTimeOfArrival?: string
  estimatedTimeOfArrivalDurationSeconds?: number // Duration in seconds
}

type SsmRequesterInfo = {
  vehicleID?: string
  role?: ProcessedBasicVehicleRole
  subrole?: ProcessedRequestSubRole
  importanceLevel?: ProcessedRequestImportanceLevel
  iso3833VehicleType?: number
  hpmsType?: ProcessedVehicleType
}

type SsmVehicleInfo = {}

// New enum type for SSM
type ProcessedPrioritizationResponseStatus =
  | 'unknown'
  | 'requested'
  | 'processing'
  | 'watchOtherTraffic'
  | 'granted'
  | 'rejected'
  | 'maxPresence'
  | 'reserviceLocked'

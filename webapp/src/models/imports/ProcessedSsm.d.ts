type ProcessedSsm = {
  schemaVersion: number
  messageType: 'SSM'
  asn1: string
  odeReceivedAt: string
  odeReceivedAtEpochSeconds: number
  originIp: string
  timeStamp: string
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
  inboundOnApproachID?: number
  inboundOnLaneConnectionID?: number
  outboundOnLaneID?: number
  outboundOnApproachID?: number
  outboundOnLaneConnectionID?: number
  estimatedTimeOfArrival?: string
  estimatedTimeOfArrivalDurationSeconds?: number // Duration in seconds
  status?: ProcessedPrioritizationResponseStatus
}

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

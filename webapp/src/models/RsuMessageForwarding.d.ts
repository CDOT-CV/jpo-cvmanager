export type ForwardingMessageType = 'BSM' | 'SPAT' | 'MAP' | 'SSM' | 'SRM' | 'TIM'

export type MessageForwardingRequest = {
  rsu_ips: string[]
  message_type: ForwardingMessageType
  destination_ip: string
}

/** include_security controls inclusion of IEEE 1609.2 security headers in forwarded messages. */
export type CreateMessageForwardingRequest = MessageForwardingRequest & { include_security: boolean }

export type MessageForwardingResult = {
  execution: {
    taskId: string
    target: string
    status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT' | 'CANCELLED' | 'QUEUE_EXPIRED' | 'REJECTED'
    message: string
    step: string | null
    elapsedMs: number
    steps: { name: string; status: string }[]
  }
  writeState: 'NOT_SUBMITTED' | 'REJECTED' | 'ACKNOWLEDGED_UNVERIFIED' | 'VERIFIED' | 'VERIFICATION_FAILED' | 'UNKNOWN'
  changed: boolean | null
  databaseSync: 'UPDATED' | 'NOT_UPDATED' | 'IN_PROGRESS' | 'FAILED'
}

export type MessageForwardingResponse = Record<string, MessageForwardingResult>


export type MessageForwardingConfiguration = {
  table: 'RECEIVE' | 'TRANSMIT' | 'DSRC'
  index: number
  messageType: string
  destinationIp: string
  destinationPort: number
  startDatetime: string
  endDatetime: string
  active: boolean
  includeSecurity: boolean
}

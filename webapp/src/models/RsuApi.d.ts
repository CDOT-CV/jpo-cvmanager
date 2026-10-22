export type RsuProperties = {
  rsu_id: number
  milepost: number
  geography: string
  model_name: string
  ipv4_address: string
  primary_route: string
  serial_number: string
  manufacturer_name: string
  snmp_protocol_code?: string
  owner_organization_id?: number
  tim_deposit: boolean
}

export type RsuInfoList = {
  rsuList: Array<RsuInfo>
}

export type RsuInfo = {
  id: number
  type: 'Feature'
  geometry: {
    type: 'Point'
    coordinates: Array<number>
  }
  properties: RsuProperties
}

export type MessageType = 'SPAT' | 'MAP' | 'BSM' | 'SRM' | 'SSM' | 'TIM' | 'PSM'

export type RsuCounts = {
  [ip: string]: {
    road: string
    messageTypeCounts: {
      [messageType: string]: number
    }
  }
}

// No response used, this method does not appear to be used
export type GetRsuUserAuthResp = object

// No response used, this method does not appear to be used
export type GetRsuCommandResp = object

export type RsuMapInfo = {
  geojson: GeoJSON.FeatureCollection<GeoJSON.Geometry>
  date: string
}
export type RsuMapInfoIpList = string[]

export type IssScmsStatus = {
  [ip: string]: {
    health: '0' | '1'
    expiration: string
  }
}

export type RsuCommandPostBody = {
  command: 'reboot' | 'upgrade-rsu' | 'upgrade-check'
  rsu_ip: string[]
  args: object
}

export type RsuUpgradePostBody = {
  rsu_ips: string[]
  args: object
}

export type RsuUpgradeCheckPostBody = {
  rsu_ip: string
  args: object
}

export type ApiMsgResp = { message: string }
export type ApiMsgRespWithCodes<T> = {
  body: T
  status: number
  message: string
}

export type SnmpFwdWalkConfig = {
  'Message Type': string
  IP: string
  Port: number
  Protocol: string
  RSSI: number
  Frequency: number
  'Start DateTime': string
  'End DateTime': string
  Forwarding: string
  'Config Active': string
}

export type RsuMsgFwdConfigSingle = {
  table?: 'RECEIVE' | 'TRANSMIT' | 'DSRC'
  index?: number
  'Message Type': string
  IP: string
  Port: number
  'Start DateTime': string
  'End DateTime': string
  'Full WSMP'?: string
  'Config Active': string
}

export type RsuDsrcFwdConfigs = {
  [index: number]: RsuMsgFwdConfigSingle
}

export type RsuRxTxMsgFwdConfigs = {
  rsuReceivedMsgTable: RsuDsrcFwdConfigs
  rsuXmitMsgFwdingTable: RsuDsrcFwdConfigs
}

export type RsuMsgFwdConfigs = {
  RsuFwdSnmpwalk: RsuDsrcFwdConfigs | RsuRxTxMsgFwdConfigs
}

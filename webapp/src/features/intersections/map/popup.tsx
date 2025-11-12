import React from 'react'
import { Popup } from 'react-map-gl'

import { Box, Typography } from '@mui/material'
import { CustomTable } from './custom-table'
import { format } from 'date-fns'
import { getSsmInfoList } from './utilities/message-utils'

const getSrmImportanceLevel = (level: ProcessedRequestImportanceLevel): string => {
  if (level?.includes('requestImportanceLevel')) {
    return level.replace('requestImportanceLevel', '')
  } else if (level === 'requestImportanceLevelUnKnown') {
    return 'Unknown'
  } else {
    return level
  }
}

export const getSelectedLayerPopupContent = (feature: any) => {
  // Feature object has top level structure, but each sub-object is JSON serialized to a string
  switch (feature?.layer?.id) {
    case 'bsm': {
      const bsm = feature.properties
      return (
        <Box>
          <Typography sx={{ paddingLeft: 1 }}>BSM</Typography>
          <CustomTable
            headers={['Field', 'Value']}
            data={[
              ['Id', bsm.id],
              ['Message Count', bsm.msgCnt],
              ['Time', bsm.secMark / 1000],
              ['Speed', bsm.speed],
              ['Heading', bsm.heading],
            ]}
          />
        </Box>
      )
    }
    case 'srm': {
      const srm = feature.properties as ProcessedSrmPropertiesWithStatus

      const rows: any[] = [
        ['Id', srm.vehicleID],
        ['Time', format(srm.timeStampEpochMillis, 'yyyy-MM-dd HH:mm:ss.SSS')],
        ['Importance Level', srm.importanceLevel],
        ['Role', srm.role],
      ]
      const ssms = (JSON.parse((srm.ssms as unknown as string) ?? '[]') as ProcessedSsm[]).flatMap(getSsmInfoList)
      const ssmResponseDict: { [key: number]: SsmInfo } = {}
      ssms.forEach((ssm) => {
        if (ssm.requestID in ssmResponseDict) {
          if (ssm.sequenceNumber ?? 0 > (ssmResponseDict[ssm.requestID].sequenceNumber ?? 0)) {
            ssmResponseDict[ssm.requestID] = ssm
          }
        } else if (ssm.requestID) {
          ssmResponseDict[ssm.requestID] = ssm
        }
      })
      JSON.parse((srm.requests as unknown as string) ?? '[]').forEach((request: ProcessedSignalRequest) => {
        rows.push([`Request ID`, request.requestID])
        rows.push([`  Request Type`, request.priorityRequestType])
        if (request.estimatedTimeOfArrival)
          rows.push([`  Estimated Arrival`, format(request.estimatedTimeOfArrival, 'yyyy-MM-dd HH:mm:ss.SSS')])
        if (request.inboundLaneID || request.outboundLaneID) {
          rows.push(['  Inbound Lane', request.inboundLaneID])
          rows.push(['  Outbound Lane', request.outboundLaneID])
        }
        if (request.inboundApproachID || request.outboundApproachID) {
          rows.push(['  Inbound Approach', request.inboundApproachID])
          rows.push(['  Outbound Approach', request.outboundApproachID])
        }
        if (request.inboundLaneConnectionID || request.outboundLaneConnectionID) {
          rows.push(['  Inbound Lane Connection', request.inboundLaneConnectionID])
          rows.push(['  Outbound Lane Connection', request.outboundLaneConnectionID])
        }
        const ssm = ssmResponseDict[request.requestID]
        if (ssm) {
          rows.push(['  SSM Status', ssm.status])
        }
      })

      return (
        <Box>
          <Typography sx={{ paddingLeft: 1 }}>SRM</Typography>
          <CustomTable headers={['Field', 'Value']} data={rows} />
        </Box>
      )
    }
    case 'map-message': {
      const map = feature.properties
      const rows: any[] = []
      JSON.parse(map?.connectsTo ?? '[]')?.forEach((connectsTo) => {
        rows.push(['Connected Lane', connectsTo.connectingLane.lane])
        rows.push(['Signal Group', connectsTo.signalGroup])
        rows.push(['Connection ID', connectsTo.connectionID])
      })
      const ssmResponses = JSON.parse(map?.signalStatuses ?? '[]') as SsmInfo[]
      const ssmResponseDict: { [key: number]: SsmInfo } = {}
      ssmResponses.forEach((ssm) => {
        if (ssm.requestID in ssmResponseDict) {
          if (ssm.sequenceNumber ?? 0 > (ssmResponseDict[ssm.requestID].sequenceNumber ?? 0)) {
            ssmResponseDict[ssm.requestID] = ssm
          }
        } else {
          ssmResponseDict[ssm.requestID] = ssm
        }
      })
      JSON.parse(map?.signalRequests ?? '[]').forEach((srm: SrmInfo) => {
        rows.push([`SRM ID`, srm.requestID])
        rows.push(['  Status', srm.priorityRequestType])
        if (srm.estimatedTimeOfArrival)
          rows.push([`  Estimated Arrival`, format(srm.estimatedTimeOfArrival, 'yyyy-MM-dd HH:mm:ss.SSS')])
        rows.push(['  Sequence Number', srm.sequenceNumber])
        if (srm.inboundLaneID || srm.outboundLaneID) {
          rows.push(['  Inbound Lane', srm.inboundLaneID])
          rows.push(['  Outbound Lane', srm.outboundLaneID])
        }
        if (srm.inboundLaneConnectionID || srm.outboundLaneConnectionID) {
          rows.push(['  Inbound Lane Connection', srm.inboundLaneConnectionID])
          rows.push(['  Outbound Lane Connection', srm.outboundLaneConnectionID])
        }
        const ssm = ssmResponseDict[srm.requestID]
        if (ssm) {
          rows.push(['  SSM Status', ssm.status])
        }
      })
      return (
        <Box>
          <Typography sx={{ paddingLeft: 1 }}>MAP Lane</Typography>
          <CustomTable headers={['Field', 'Value']} data={[['Lane Id', map.laneId], ...rows]} />
        </Box>
      )
    }
    case 'ssm-connection-status':
    case 'connecting-lanes': {
      const map = feature.properties
      const rows: any[] = [
        ['State', feature.properties.signalState],
        ['Ingress Lane', feature.properties.ingressLaneId],
        ['Egress Lane', feature.properties.egressLaneId],
        ['Signal Group', feature.properties.signalGroupId],
      ]
      let unrespondedSrms = JSON.parse(map?.signalRequests ?? '[]') as SrmInfo[]
      JSON.parse(map?.signalStatuses ?? '[]').forEach((ssm: SsmInfo) => {
        unrespondedSrms = unrespondedSrms.filter((srm) => srm.requestID !== ssm.requestID)
        rows.push([`SSM ID`, ssm.requestID])
        rows.push([`  Status`, ssm.status])
        if (ssm.inboundLaneID || ssm.outboundLaneID) {
          rows.push(['  Inbound Lane', ssm.inboundLaneID])
          rows.push(['  Outbound Lane', ssm.outboundLaneID])
        }
        if (ssm.inboundLaneConnectionID || ssm.outboundLaneConnectionID) {
          rows.push(['  Inbound Lane Connection', ssm.inboundLaneConnectionID])
          rows.push(['  Outbound Lane Connection', ssm.outboundLaneConnectionID])
        }
        if (ssm.requestInfo) {
          rows.push(['  SRM Veh. ID', ssm.requestInfo.vehicleID])
          rows.push(['  SRM Veh. Role', ssm.requestInfo.role])
          rows.push(['  SRM Req. Level', getSrmImportanceLevel(ssm.requestInfo.importanceLevel)])
        }
      })
      unrespondedSrms.forEach((srm: SrmInfo) => {
        rows.push(['SRM (unresponded)', srm.requestID])
        rows.push(['  Sequence Number', srm.sequenceNumber])
        rows.push(['  Vehicle ID', srm.vehicleInfo?.vehicleID])
        rows.push(['  Importance Level', getSrmImportanceLevel(srm.vehicleInfo?.importanceLevel)])
        rows.push(['  Importance Level', srm.vehicleInfo?.role])
      })
      return (
        <Box>
          <Typography sx={{ paddingLeft: 1 }}>Connecting Lane</Typography>
          <CustomTable headers={['Field', 'Value']} data={rows} />
        </Box>
      )
    }

    case 'signal-states':
      return (
        <Box>
          <Typography>Signal State</Typography>
          <CustomTable
            headers={['Field', 'Value']}
            data={[
              ['Signal State', feature.properties.signalState],
              ['Signal Group', feature.properties.signalGroup],
            ]}
          />
        </Box>
      )
    default: {
      return <Typography sx={{ paddingLeft: 1 }}>{JSON.stringify(feature)}</Typography>
    }
  }
  return <Typography sx={{ paddingLeft: 1 }}>No Data</Typography>
}

export const CustomPopup = (props) => {
  return (
    <Popup
      longitude={props.selectedFeature.clickedLocation.lng}
      latitude={props.selectedFeature.clickedLocation.lat}
      anchor="bottom"
      onClose={props.onClose}
      onOpen={() => {}}
      maxWidth={'500px'}
      closeOnClick={false}
    >
      {getSelectedLayerPopupContent(props.selectedFeature.feature)}
    </Popup>
  )
}

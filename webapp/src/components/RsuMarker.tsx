import { getStatusMarkerColor, RsuHealthStatus } from '../utils/rsu-status'

export type RsuMarkerProps = {
  displayType: string
  onlineStatus?: string
  scmsStatus?: boolean | null
}

function RsuMarker(props: RsuMarkerProps) {
  let status: RsuHealthStatus = 'unknown'
  if (props.displayType === 'online') {
    if (props.onlineStatus === 'online' || props.onlineStatus === 'unstable' || props.onlineStatus === 'offline') {
      status = props.onlineStatus
    }
  } else if (props.displayType === 'scms') {
    status = props.scmsStatus === true ? 'healthy' : props.scmsStatus === false ? 'unhealthy' : 'unknown'
  }

  const circleStyle = {
    padding: 5,
    display: 'inline-block',
    borderRadius: '50%',
    width: 5,
    height: 5,
    backgroundColor: getStatusMarkerColor(status),
  }

  return <div style={circleStyle}></div>
}

export default RsuMarker

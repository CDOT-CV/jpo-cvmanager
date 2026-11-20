import { ForwardingDeviceOutcome } from '../models/RsuMessageForwarding'
import RsuOperationResults from './RsuOperationResults'

export type ForwardingResultsProps = {
  operation: 'create' | 'delete'
  messageType: string
  destination: string
  results: ForwardingDeviceOutcome[]
  onDismiss: () => void
}

const ForwardingResults = ({ operation, messageType, destination, results, onDismiss }: ForwardingResultsProps) => (
  <RsuOperationResults
    title={`${operation === 'create' ? 'Add' : 'Delete'} ${messageType.toUpperCase()} → ${destination}`}
    label="Forwarding results"
    results={results.map((row) => ({ ip: row.ip, success: row.success, detail: resultDetail(row, operation) }))}
    onDismiss={onDismiss}
  />
)

function resultDetail(row: ForwardingDeviceOutcome, operation: 'create' | 'delete'): string {
  if (row.notAttempted) return 'Not attempted after authorization failure'
  if (row.success) {
    if (row.result?.changed === false) return operation === 'create' ? 'Already configured' : 'Already absent'
    return operation === 'create' ? 'Added' : 'Deleted'
  }
  const result = row.result
  if (result?.writeState === 'UNKNOWN' || result?.writeState === 'ACKNOWLEDGED_UNVERIFIED'
      || result?.execution.status === 'RUNNING') return 'Unconfirmed; check device before retrying'
  if (result?.changed === true && result.writeState === 'VERIFIED') return 'Change verified; cache not updated'
  if (result?.changed === false && result.databaseSync !== 'UPDATED') return 'No change needed; cache not updated'
  if (row.status === 401) return 'Sign in again'
  if (row.status === 403) return 'Not authorized'
  if (row.status === 400) return 'Invalid request'
  if (row.status === 503 || result?.execution.status === 'REJECTED') return 'Service busy'
  if (row.status === 504 || result?.execution.status === 'TIMED_OUT'
      || result?.execution.status === 'QUEUE_EXPIRED') return 'Timed out'
  if (result?.execution.status === 'CANCELLED') return 'Cancelled; check device before retrying'
  return result ? 'Failed' : 'No response; check device before retrying'
}

export default ForwardingResults

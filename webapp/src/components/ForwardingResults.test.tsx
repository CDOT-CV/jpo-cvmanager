import { fireEvent, render, screen } from '@testing-library/react'
import ForwardingResults from './ForwardingResults'
import { ForwardingDeviceOutcome, MessageForwardingResult } from '../models/RsuMessageForwarding'

const result: MessageForwardingResult = {
  execution: { taskId: 'task', target: 'udp:192.0.2.10:161', status: 'SUCCEEDED',
    message: 'Completed.', step: null, elapsedMs: 1, steps: [] },
  changed: true, writeState: 'VERIFIED', databaseSync: 'UPDATED',
}

it.each([
  [{ success: true, result }, 'Added'],
  [{ success: true, result: { ...result, changed: false } }, 'Already configured'],
  [{ success: false, result: { ...result, writeState: 'UNKNOWN' } }, 'Unconfirmed; check device before retrying'],
  [{ success: false, result: { ...result, databaseSync: 'FAILED' } }, 'Change verified; cache not updated'],
  [{ success: false, result: { ...result, changed: false, databaseSync: 'FAILED' } }, 'No change needed; cache not updated'],
  [{ success: false, status: 403 }, 'Not authorized'],
  [{ success: false, status: 504 }, 'Timed out'],
  [{ success: false }, 'No response; check device before retrying'],
  [{ success: false, skipped: true }, 'Skipped: requires NTCIP 1218'],
] as [Omit<ForwardingDeviceOutcome, 'ip'>, string][])('explains status %s on hover', async (outcome, detail) => {
  render(<ForwardingResults operation="create" messageType="MAP" destination="198.51.100.3"
    results={[{ ip: '192.0.2.10', ...outcome }]} onDismiss={() => {}} />)
  const status = screen.getByLabelText(`192.0.2.10: ${detail}`)
  expect(status).toHaveAttribute('tabindex', '0')
  expect(Boolean(status.querySelector('[data-testid="CheckIcon"]'))).toBe(outcome.success)
  fireEvent.mouseOver(status)
  expect(await screen.findByRole('tooltip')).toHaveTextContent(detail)
})

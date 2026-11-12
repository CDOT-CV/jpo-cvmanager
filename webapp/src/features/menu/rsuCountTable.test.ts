import { buildRsuCountTableRows, formatCount } from './rsuCountTable'

it('includes every configured type even when some are missing', () => {
  expect(
    buildRsuCountTableRows(
      ['BSM', 'MAP', 'SPAT'],
      [{ message_type: 'BSM', rsu_ip: '10.0.0.16', ode_input_count: 10, ode_output_count: 9 }]
    )
  ).toEqual([
    { messageType: 'BSM', odeInputCount: 10, odeOutputCount: 9 },
    { messageType: 'MAP', odeInputCount: 0, odeOutputCount: 0 },
    { messageType: 'SPAT', odeInputCount: 0, odeOutputCount: 0 },
  ])
})

it('formats counts with grouping separators', () => {
  expect(formatCount(327817)).toBe('327,817')
  expect(formatCount(0)).toBe('0')
})

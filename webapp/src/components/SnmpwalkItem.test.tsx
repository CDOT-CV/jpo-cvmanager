import { fireEvent, render, screen } from '@testing-library/react'
import SnmpwalkItem from './SnmpwalkItem'
import { replaceChaoticIds } from '../utils/test-utils'

it('should take a snapshot', () => {
  const { container } = render(
    <SnmpwalkItem
      content={{ 'Message Type': '', IP: '', Port: 0, 'Start DateTime': '', 'End DateTime': '', 'Config Active': '' }}
      index={''}
      handleDelete={() => ({})}
    />
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it.each([true, false])('marks invalid rows and allows deletion only with readable guards (%s)', (deletable) => {
  const remove = jest.fn()
  render(<SnmpwalkItem index="1" handleDelete={remove} content={{
    'Message Type': 'MAP', IP: '198.51.100.3', Port: 80,
    'Start DateTime': 'Unknown', 'End DateTime': 'Unknown', 'Config Active': 'Unknown',
    validationError: 'Incomplete forwarding row', deletable,
  }} />)
  expect(screen.getByRole('alert')).toHaveTextContent('Invalid configuration: Incomplete forwarding row')
  const button = screen.getByRole('button', { name: 'Delete' })
  fireEvent.click(button)
  if (deletable) {
    expect(button).not.toBeDisabled()
    expect(remove).toHaveBeenCalledTimes(1)
  } else {
    expect(button).toBeDisabled()
    expect(remove).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('manual repair is required')
  }
})

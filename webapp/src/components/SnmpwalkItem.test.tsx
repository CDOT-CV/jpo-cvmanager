import { render } from '@testing-library/react'
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

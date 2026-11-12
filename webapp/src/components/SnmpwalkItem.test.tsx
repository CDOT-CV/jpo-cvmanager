import { render, screen } from '@testing-library/react'
import SnmpwalkItem from './SnmpwalkItem'
import { replaceChaoticIds } from '../utils/test-utils'

it('should take a snapshot', () => {
  const { container } = render(<SnmpwalkItem content={{}} index={''} handleDelete={() => ({})} />)

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('shows the Delete button to the owner organization only', () => {
  const { rerender } = render(<SnmpwalkItem content={{}} index={''} handleDelete={() => ({})} />)
  expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument()

  rerender(<SnmpwalkItem content={{}} index={''} handleDelete={() => ({})} isOwnerOrg={false} />)
  expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
})

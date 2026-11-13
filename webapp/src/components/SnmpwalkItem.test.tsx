import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import SnmpwalkItem, { SnmpwalkItemProps } from './SnmpwalkItem'
import { setupStore } from '../store'
import { replaceChaoticIds } from '../utils/test-utils'

const itemElement = (props: Partial<SnmpwalkItemProps> = {}, superUser = false) => (
  <Provider store={setupStore({ user: { value: { authLoginData: { data: { super_user: superUser } } } } })}>
    <SnmpwalkItem content={{}} index={''} handleDelete={() => ({})} {...props} />
  </Provider>
)

it('should take a snapshot', () => {
  const { container } = render(itemElement())

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('shows the Delete button to the owner organization only', () => {
  const { rerender } = render(itemElement())
  expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument()

  rerender(itemElement({ isOwnerOrg: false }))
  expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
})

it('shows the Delete button to a super user even when another organization owns the RSU', () => {
  render(itemElement({ isOwnerOrg: false }, true))
  expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
})

import { render } from '@testing-library/react'
import SubscriptionForm from './SubscriptionForm'
import { replaceChaoticIds } from '../utils/test-utils'

it('should take a snapshot', () => {
  const { container } = render(
    <BrowserRouter>
      <SubscriptionForm subscriptions={[]} onSave={async () => {}} />
    </BrowserRouter>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

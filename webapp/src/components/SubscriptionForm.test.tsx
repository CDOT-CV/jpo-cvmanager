import { vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import SubscriptionForm, {
  defaultFrequency,
  groupByOrganization,
  subscriptionKey,
  isSubscribed,
  setSubscribed,
} from './SubscriptionForm'
import { EmailSubscription } from '../models/email-subscriptions'
import { replaceChaoticIds } from '../utils/test-utils'
import { BrowserRouter } from 'react-router-dom'

it('should take a snapshot', () => {
  const { container } = render(
    <BrowserRouter>
      <SubscriptionForm subscriptions={[]} onSave={async () => {}} />
    </BrowserRouter>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

const subscription = (overrides: Partial<EmailSubscription>): EmailSubscription => ({
  category: 'Daily Message Counts',
  organization: 'Org A',
  description: 'Receive automated daily message count emails',
  required_role: 'user',
  immediate: false,
  hourly: false,
  daily: false,
  weekly: false,
  monthly: false,
  supports_immediate: false,
  supports_hourly: false,
  supports_daily: true,
  supports_weekly: false,
  supports_monthly: false,
  ...overrides,
})

const twoOrgSubscriptions = [
  subscription({ organization: 'Org A', daily: true }),
  subscription({
    organization: 'Org A',
    category: 'Firmware Upgrade Failures',
    required_role: 'operator',
    supports_daily: false,
    supports_immediate: true,
  }),
  subscription({ organization: 'Org B' }),
]

const renderForm = (subs: EmailSubscription[], onSave = vi.fn().mockResolvedValue(undefined)) => {
  render(
    <BrowserRouter>
      <SubscriptionForm subscriptions={subs} onSave={onSave} />
    </BrowserRouter>
  )
  return onSave
}

describe('SubscriptionForm helpers', () => {
  it('groups subscriptions by organization in API order', () => {
    expect(groupByOrganization(twoOrgSubscriptions).map(([org, subs]) => [org, subs.length])).toEqual([
      ['Org A', 2],
      ['Org B', 1],
    ])
  })

  it('groups system-wide subscriptions under a null organization', () => {
    expect(
      groupByOrganization([
        subscription({ organization: null, category: 'Support Requests' }),
        ...twoOrgSubscriptions,
      ]).map(([org, subs]) => [org, subs.length])
    ).toEqual([
      [null, 1],
      ['Org A', 2],
      ['Org B', 1],
    ])
  })

  it('keys system-wide subscriptions separately from organization ones', () => {
    expect(subscriptionKey(subscription({ organization: null }))).not.toEqual(
      subscriptionKey(subscription({ organization: 'Org A' }))
    )
  })

  it('subscribing selects the first supported frequency', () => {
    expect(defaultFrequency(subscription({ supports_hourly: true, supports_daily: true }))).toEqual('hourly')
    expect(setSubscribed(subscription({}), true)).toMatchObject({
      daily: true,
      immediate: false,
    })
  })

  it('subscribing keeps existing frequencies and unsubscribing clears them', () => {
    const weekly = subscription({ supports_weekly: true, weekly: true })
    expect(setSubscribed(weekly, true)).toBe(weekly)
    expect(isSubscribed(setSubscribed(weekly, false))).toBe(false)
  })
})

describe('SubscriptionForm per-organization layout', () => {
  it('renders a section with a status for each organization', () => {
    renderForm(twoOrgSubscriptions)

    const orgA = screen.getByRole('region', {
      name: 'Org A email subscriptions',
    })
    const orgB = screen.getByRole('region', {
      name: 'Org B email subscriptions',
    })
    expect(within(orgA).getByText('Subscribed to 1 of 2')).toBeInTheDocument()
    expect(within(orgB).getByText('Not subscribed')).toBeInTheDocument()
  })

  it('organization toggle only changes that organization', async () => {
    const onSave = renderForm(twoOrgSubscriptions)

    fireEvent.click(screen.getByRole('checkbox', { name: 'Subscribe to all Org B emails' }))
    expect(
      within(screen.getByRole('region', { name: 'Org B email subscriptions' })).getByText('Subscribed to 1 of 1')
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('checkbox', { name: 'Subscribe to all Org A emails' }))
    expect(
      within(screen.getByRole('region', { name: 'Org A email subscriptions' })).getByText('Not subscribed')
    ).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Save Preferences' }))
    await waitFor(() => expect(onSave).toHaveBeenCalled())
    const saved: EmailSubscription[] = onSave.mock.calls[0][0]
    expect(saved.filter((sub) => sub.organization === 'Org A').every((sub) => !isSubscribed(sub))).toBe(true)
    expect(saved.find((sub) => sub.organization === 'Org B')).toMatchObject({
      daily: true,
    })
  })

  it('type toggle subscribes one organization without affecting the other', async () => {
    const onSave = renderForm([subscription({ organization: 'Org A' }), subscription({ organization: 'Org B' })])

    fireEvent.click(
      screen.getByRole('checkbox', {
        name: 'Subscribe to Daily Message Counts for Org A',
      })
    )
    fireEvent.click(screen.getByRole('button', { name: 'Save Preferences' }))

    await waitFor(() => expect(onSave).toHaveBeenCalled())
    const saved: EmailSubscription[] = onSave.mock.calls[0][0]
    expect(saved.find((sub) => sub.organization === 'Org A')).toMatchObject({
      daily: true,
    })
    expect(saved.find((sub) => sub.organization === 'Org B')).toMatchObject({
      daily: false,
    })
  })
})

describe('SubscriptionForm system-wide subscriptions', () => {
  const systemWide = subscription({
    organization: null,
    category: 'Critical Error Messages',
    required_role: 'operator',
    supports_daily: false,
    supports_immediate: true,
  })

  it('renders system-wide email types once in their own section', () => {
    renderForm([systemWide, ...twoOrgSubscriptions])

    const section = screen.getByRole('region', {
      name: 'System-wide email subscriptions',
    })
    expect(within(section).getByText('Critical Error Messages')).toBeInTheDocument()
    expect(screen.getAllByText('Critical Error Messages')).toHaveLength(1)
  })

  it('subscribing to a system-wide type saves it without an organization', async () => {
    const onSave = renderForm([systemWide, ...twoOrgSubscriptions])

    fireEvent.click(
      screen.getByRole('checkbox', {
        name: 'Subscribe to Critical Error Messages',
      })
    )
    fireEvent.click(screen.getByRole('button', { name: 'Save Preferences' }))

    await waitFor(() => expect(onSave).toHaveBeenCalled())
    const saved: EmailSubscription[] = onSave.mock.calls[0][0]
    expect(saved.find((sub) => sub.category === 'Critical Error Messages')).toMatchObject({
      organization: null,
      immediate: true,
    })
    expect(saved.filter((sub) => sub.organization === 'Org B')).toHaveLength(1)
  })
})

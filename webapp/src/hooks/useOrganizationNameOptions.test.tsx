import { render, screen, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setupStore } from '../store'
import { useOrganizationNameOptions } from './useOrganizationNameOptions'

const OrganizationNameOptions = ({ ids }: { ids: number[] }) => {
  const options = useOrganizationNameOptions(ids)
  return (
    <ul>
      {options.map(({ id, name }) => (
        <li key={id}>{name}</li>
      ))}
    </ul>
  )
}

const makeUserState = (superUser: boolean, organizations: UserAuthOrganization[]) => ({
  user: {
    loading: false,
    value: {
      authLoginData: {
        data: {
          name: 'Test User',
          first_name: 'Test',
          last_name: 'User',
          email: 'test@example.com',
          super_user: superUser,
          organizations,
        },
        token: 'test-token',
        expires_at: 0,
      },
      organization: null,
      loginFailure: false,
      loginMessage: '',
      routeNotFound: false,
    },
  },
})

describe('useOrganizationNameOptions', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads the full organization directory for super users to resolve unassigned IDs', async () => {
    const fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ id: 42, name: 'All Organizations', email: '' }]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    )
    vi.stubGlobal('fetch', fetch)

    render(
      <Provider store={setupStore(makeUserState(true, []))}>
        <OrganizationNameOptions ids={[42]} />
      </Provider>
    )

    expect(await screen.findByText('All Organizations')).toBeInTheDocument()
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1))
  })

  it('uses JWT organization names without fetching the full directory for other users', async () => {
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)

    render(
      <Provider
        store={setupStore(makeUserState(false, [{ organization: 7, name: 'Assigned Organization', role: 'ADMIN' }]))}
      >
        <OrganizationNameOptions ids={[7, 42]} />
      </Provider>
    )

    expect(screen.getByText('Assigned Organization')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()
  })
})

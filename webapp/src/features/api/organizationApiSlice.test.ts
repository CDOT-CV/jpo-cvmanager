import { setupStore } from '../../store'
import { organizationApiSlice } from './organizationApiSlice'
import { afterEach, describe, expect, it, vi } from 'vitest'

const makeUserState = () => ({
  user: {
    loading: false,
    value: {
      authLoginData: {
        data: {
          name: 'Test User',
          first_name: 'Test',
          last_name: 'User',
          email: 'test@example.com',
          super_user: true,
          organizations: [],
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

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

describe('organizationApiSlice cache invalidation', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('refetches the organization directory after a rename and a deletion', async () => {
    let organizations = [{ id: 1, name: 'Original Organization', email: 'org@example.com' }]
    const fetch = vi.fn(async (request: Request) => {
      if (request.method === 'PATCH') {
        const patch = (await request.clone().json()) as OrganizationPatch
        organizations = organizations.map((organization) =>
          organization.id === patch.id ? { ...organization, name: patch.name ?? organization.name } : organization
        )
        return jsonResponse(organizations[0])
      }

      if (request.method === 'DELETE') {
        organizations = []
        return new Response(null, { status: 204 })
      }

      return jsonResponse(organizations)
    })
    vi.stubGlobal('fetch', fetch)
    const store = setupStore(makeUserState())
    const organizationsQuery = store.dispatch(organizationApiSlice.endpoints.getOrganizations.initiate())
    const selectOrganizations = organizationApiSlice.endpoints.getOrganizations.select()

    await organizationsQuery.unwrap()
    await store.dispatch(organizationApiSlice.endpoints.patchOrganization.initiate({ id: 1, name: 'Renamed' })).unwrap()

    await vi.waitFor(() => {
      expect(fetch).toHaveBeenCalledTimes(3)
      expect(selectOrganizations(store.getState()).data).toEqual([{ id: 1, name: 'Renamed', email: 'org@example.com' }])
    })

    await store.dispatch(organizationApiSlice.endpoints.deleteOrganization.initiate(1)).unwrap()

    await vi.waitFor(() => {
      expect(fetch).toHaveBeenCalledTimes(5)
      expect(selectOrganizations(store.getState()).data).toEqual([])
    })

    organizationsQuery.unsubscribe()
  })
})

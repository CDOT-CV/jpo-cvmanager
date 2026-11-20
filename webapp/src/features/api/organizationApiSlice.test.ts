import { setupStore } from '../../store'
import EnvironmentVars from '../../EnvironmentVars'
import { organizationApiSlice } from './organizationApiSlice'

const mockUserState = {
  user: {
    value: {
      authLoginData: { token: 'test-token' },
      organization: { organization: 1, role: 'ADMIN' },
    },
  },
}

describe('organizationApiSlice createOrganization', () => {
  beforeEach(() => {
    fetchMock.resetMocks()
  })

  it('posts the organization to the Java API and returns its ID', async () => {
    const store = setupStore(mockUserState)
    const organization = { name: 'New Organization', email: 'new@example.com' }
    fetchMock.mockResponseOnce('123', { status: 201 })

    const result = await store.dispatch(organizationApiSlice.endpoints.createOrganization.initiate(organization))

    expect(result).toMatchObject({ data: 123 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const request = fetchMock.mock.calls[0][0] as Request
    expect(request.url).toBe(`${EnvironmentVars.CVIZ_API_SERVER_URL}/organizations`)
    expect(request.method).toBe('POST')
    expect(request.headers.get('Authorization')).toBe('Bearer test-token')
    expect(await request.json()).toEqual(organization)
  })
})

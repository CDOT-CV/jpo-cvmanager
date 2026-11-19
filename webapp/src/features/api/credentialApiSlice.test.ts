import { configureStore } from '@reduxjs/toolkit'
import userReducer from '../../generalSlices/userSlice'
import EnvironmentVars from '../../EnvironmentVars'
import { credentialApiSlice } from './credentialApiSlice'
import { rsuApiSlice, RSU_API_RSU_ALLOWED_SELECTIONS_TAG } from './rsuApiSlice'

const BASE_URL = `${EnvironmentVars.CVIZ_API_SERVER_URL}/credentials`

const makeStore = () =>
  configureStore({
    reducer: {
      user: userReducer,
      [credentialApiSlice.reducerPath]: credentialApiSlice.reducer,
    },
    preloadedState: {
      user: {
        loading: false,
        value: {
          authLoginData: {
            data: {
              name: 'Test User',
              first_name: 'Test',
              last_name: 'User',
              email: 'test@example.com',
              super_user: false,
              organizations: [],
            },
            token: 'mock-token',
            expires_at: 0,
          },
          organization: { organization: 1, name: 'OrgA', role: 'ADMIN' },
          loginFailure: false,
          loginMessage: '',
          routeNotFound: false,
        },
      },
    } as any,
    middleware: (gDM) => gDM().concat(credentialApiSlice.middleware),
  })

const mockJsonResponse = (body: unknown, status = 200) =>
  Promise.resolve(
    new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    })
  )

const lastRequest = () => (global.fetch as jest.Mock).mock.calls[0][0] as Request

describe('credentialApiSlice', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    global.fetch = jest.fn()
  })

  test.each(['rsu', 'snmp'] as const)('listCredentials (%s)', async (type) => {
    const credentials = [{ nickname: 'cred', username: 'user', ownerOrganization: 'OrgA', rsuIps: ['10.0.0.1'] }]
    ;(global.fetch as jest.Mock).mockImplementationOnce(() => mockJsonResponse(credentials))
    const store = makeStore()

    const action = store.dispatch(credentialApiSlice.endpoints.listCredentials.initiate({ type, organization: 'Org A' }))
    const result = await action.unwrap()

    const req = lastRequest()
    expect(req.url).toBe(`${BASE_URL}/${type}/list?organization=Org+A`)
    expect(req.method).toBe('GET')
    expect(req.headers.get('Authorization')).toBe('Bearer mock-token')
    expect(result).toEqual(credentials)
    action.unsubscribe()
  })

  test('createCredential posts the credential and does not keep the echoed password', async () => {
    ;(global.fetch as jest.Mock).mockImplementationOnce(() =>
      mockJsonResponse({ id: 1, nickname: 'cred', username: 'user', password: 'secret', ownerOrganizationId: 1 })
    )
    const store = makeStore()

    const result = await store
      .dispatch(
        credentialApiSlice.endpoints.createCredential.initiate({
          type: 'snmp',
          nickname: 'cred',
          username: 'user',
          password: 'secret',
          organization: 'OrgA',
        })
      )
      .unwrap()

    const req = lastRequest()
    expect(req.url).toBe(`${BASE_URL}/snmp/create`)
    expect(req.method).toBe('POST')
    expect(await req.json()).toEqual({
      nickname: 'cred',
      username: 'user',
      password: 'secret',
      organization: 'OrgA',
    })
    expect(result).toBeUndefined()
    expect(JSON.stringify(store.getState()[credentialApiSlice.reducerPath])).not.toContain('"password":"secret"')
  })

  test('updateCredential omits a blank password and an unchanged username', async () => {
    ;(global.fetch as jest.Mock).mockImplementationOnce(() => mockJsonResponse({}))
    const store = makeStore()

    await store
      .dispatch(credentialApiSlice.endpoints.updateCredential.initiate({ type: 'rsu', nickname: 'cred', password: '' }))
      .unwrap()

    const req = lastRequest()
    expect(req.url).toBe(`${BASE_URL}/rsu/update`)
    expect(req.method).toBe('POST')
    expect(await req.json()).toEqual({ nickname: 'cred' })
  })

  test('updateCredential sends the fields that changed', async () => {
    ;(global.fetch as jest.Mock).mockImplementationOnce(() => mockJsonResponse({}))
    const store = makeStore()

    await store
      .dispatch(
        credentialApiSlice.endpoints.updateCredential.initiate({
          type: 'rsu',
          nickname: 'cred',
          username: 'newUser',
          password: 'newSecret',
        })
      )
      .unwrap()

    expect(await lastRequest().json()).toEqual({ nickname: 'cred', username: 'newUser', password: 'newSecret' })
  })

  test('deleteCredential posts the nickname', async () => {
    ;(global.fetch as jest.Mock).mockImplementationOnce(() => Promise.resolve(new Response(null, { status: 200 })))
    const store = makeStore()

    await store
      .dispatch(credentialApiSlice.endpoints.deleteCredential.initiate({ type: 'snmp', nickname: 'cred' }))
      .unwrap()

    const req = lastRequest()
    expect(req.url).toBe(`${BASE_URL}/snmp/delete`)
    expect(req.method).toBe('POST')
    expect(await req.json()).toEqual({ nickname: 'cred' })
  })

  test('mutations refresh the RSU allowed selections only after succeeding', async () => {
    const invalidateSpy = jest.spyOn(rsuApiSlice.util, 'invalidateTags')
    const store = makeStore()

    ;(global.fetch as jest.Mock).mockImplementationOnce(() => mockJsonResponse({ detail: 'in use' }, 409))
    await expect(
      store.dispatch(credentialApiSlice.endpoints.deleteCredential.initiate({ type: 'rsu', nickname: 'cred' })).unwrap()
    ).rejects.toBeDefined()
    expect(invalidateSpy).not.toHaveBeenCalled()

    ;(global.fetch as jest.Mock).mockImplementationOnce(() => Promise.resolve(new Response(null, { status: 200 })))
    await store
      .dispatch(credentialApiSlice.endpoints.deleteCredential.initiate({ type: 'rsu', nickname: 'cred' }))
      .unwrap()
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(invalidateSpy).toHaveBeenCalledWith([RSU_API_RSU_ALLOWED_SELECTIONS_TAG])

    invalidateSpy.mockRestore()
  })
})

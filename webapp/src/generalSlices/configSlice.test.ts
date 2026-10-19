import reducer, {
  getCachedSnmpFwdConfigsFromDatabase,
  getRsuMsgConfigsFromRsu,
  submitSnmpSet,
  deleteSnmpSet,
  rebootRsu,
  checkFirmwareUpgrade,
  startFirmwareUpgrade,

  // reducers
  setDestIp,
  setMsgType,
  toggleConfigPointSelect,
} from './configSlice'
import RsuApi from '../apis/rsu-api'
import RsuFirmwareApi from '../apis/intersections/rsu-firmware-api'
import RsuMessageForwardingApi from '../apis/intersections/rsu-message-forwarding-api'
import { MessageForwardingResult } from '../models/RsuMessageForwarding'
import { RootState } from '../store'

describe('config reducer', () => {
  it('should handle initial state', () => {
    expect(reducer(undefined, { type: 'unknown' })).toEqual({
      loading: false,
      value: {
        msgFwdConfig: {},
        errorState: '',
        changeSuccess: false,
        rebootChangeSuccess: false,
        firmwareUpgradeAvailable: false,
        firmwareUpgradeName: '',
        firmwareUpgradeMsg: '',
        firmwareUpgradeErr: false,
        destIp: '',
        snmpMsgType: 'bsm',
        includeSecurityHeader: false,
        addConfigPoint: false,
        configCoordinates: [],
        configList: [],
        msgFwdConfigType: 'database',
      },
    })
  })
})

describe('async thunks', () => {
  const initialState = {
    loading: false,
    value: {
      msgFwdConfig: null,
      errorState: null,
      changeSuccess: false,
      rebootChangeSuccess: false,
      firmwareUpgradeAvailable: false,
      firmwareUpgradeName: '',
      firmwareUpgradeMsg: '',
      firmwareUpgradeErr: false,
      destIp: '',
      snmpMsgType: 'bsm',
      includeSecurityHeader: false,
      addConfigPoint: false,
      configCoordinates: null,
      configList: null,
    },
  } as RootState['config']

  beforeAll(() => {
    jest.mock('../apis/rsu-api')
  })

  afterAll(() => {
    jest.unmock('../apis/rsu-api')
  })

  describe('refreshSnmpFwdConfig', () => {
    it('returns and calls the api correctly', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuMessageForwardingApi.cached = jest.fn().mockReturnValue({ status: 200, body: { RsuFwdSnmpwalk: 'test' } })

      const rsu_ip = '1.2.3.4'

      const action = getCachedSnmpFwdConfigsFromDatabase(rsu_ip)

      const resp = await action(dispatch, getState, undefined)
      expect(RsuMessageForwardingApi.cached).toHaveBeenCalledWith('token', 'Org 1', rsu_ip)
      expect(resp.payload).toEqual({ msgFwdConfig: 'test', errorState: '' })
    })

    it('Updates the state correctly pending', async () => {
      const loading = true
      const msgFwdConfig = {}
      const rebootChangeSuccess = false
      const changeSuccess = false
      const errorState = ''
      const destIp = ''
      const snmpMsgType = 'bsm'
      const state = reducer(initialState, {
        type: 'config/refreshSnmpFwdConfig/pending',
      })
      expect(state).toEqual({
        loading,
        value: {
          ...initialState.value,
          msgFwdConfig,
          errorState,
          rebootChangeSuccess,
          changeSuccess,
          destIp,
          snmpMsgType,
        },
      })
    })

    it('Updates the state correctly fulfilled', async () => {
      const loading = false
      const msgFwdConfig = 'test'
      const errorState = 'error'
      const state = reducer(initialState, {
        type: 'config/refreshSnmpFwdConfig/fulfilled',
        payload: { msgFwdConfig, errorState },
      })
      expect(state).toEqual({
        loading,
        value: { ...initialState.value, msgFwdConfig, errorState, msgFwdConfigType: 'database' },
      })
    })

    it('Updates the state correctly rejected', async () => {
      const loading = false
      const state = reducer(initialState, {
        type: 'config/refreshSnmpFwdConfig/rejected',
      })
      expect(state).toEqual({ loading, value: { ...initialState.value, errorState: 'Failed to retrieve cached RSU message forwarding configuration', msgFwdConfigType: 'database' } })
    })
  })

  describe('getRsuMsgFwdFetch', () => {
    it('returns and calls the api correctly', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuMessageForwardingApi.live = jest.fn().mockReturnValue({ status: 200, body: { RsuFwdSnmpwalk: 'test' } })

      const rsu_ip = '1.2.3.4'

      const action = getRsuMsgConfigsFromRsu(rsu_ip)

      const resp = await action(dispatch, getState, undefined)
      expect(RsuMessageForwardingApi.live).toHaveBeenCalledWith('token', 'Org 1', rsu_ip)
      expect(resp.payload).toEqual({ msgFwdConfig: 'test', errorState: '' })
    })

    it('returns error when API fails', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuMessageForwardingApi.live = jest.fn().mockReturnValue(null)

      const rsu_ip = '1.2.3.4'

      const action = getRsuMsgConfigsFromRsu(rsu_ip)

      const resp = await action(dispatch, getState, undefined)
      expect(resp.payload).toEqual('Failed to fetch RSU message forwarding configuration')
    })

    it('returns error when API throws exception', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuMessageForwardingApi.live = jest.fn().mockImplementation(() => {
        throw new Error('Test Exception')
      })

      const rsu_ip = '1.2.3.4'

      const action = getRsuMsgConfigsFromRsu(rsu_ip)

      const resp = await action(dispatch, getState, undefined)
      expect(resp.payload).toEqual('Test Exception')
    })

    it('returns error when API throws a string', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuMessageForwardingApi.live = jest.fn().mockImplementation(() => {
        throw 'String Exception'
      })

      const rsu_ip = '1.2.3.4'

      const action = getRsuMsgConfigsFromRsu(rsu_ip)

      const resp = await action(dispatch, getState, undefined)
      expect(resp.payload).toEqual('String Exception')
    })

    it('returns error when API throws an unknown error', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuMessageForwardingApi.live = jest.fn().mockImplementation(() => {
        throw 123
      })

      const rsu_ip = '1.2.3.4'

      const action = getRsuMsgConfigsFromRsu(rsu_ip)

      const resp = await action(dispatch, getState, undefined)
      expect(resp.payload).toEqual('An unknown error occurred while fetching RSU message forwarding configuration')
    })

    it('Updates the state correctly pending', async () => {
      const loading = true
      const msgFwdConfig = {}
      const rebootChangeSuccess = false
      const changeSuccess = false
      const errorState = ''
      const destIp = ''
      const snmpMsgType = 'bsm'
      const state = reducer(initialState, {
        type: 'config/getRsuMsgFwdFetch/pending',
      })
      expect(state).toEqual({
        loading,
        value: {
          ...initialState.value,
          msgFwdConfig,
          errorState,
          rebootChangeSuccess,
          changeSuccess,
          destIp,
          snmpMsgType,
        },
      })
    })

    it('Updates the state correctly fulfilled', async () => {
      const loading = false
      const msgFwdConfig = 'test'
      const errorState = 'error'
      const state = reducer(initialState, {
        type: 'config/getRsuMsgFwdFetch/fulfilled',
        payload: { msgFwdConfig, errorState },
      })
      expect(state).toEqual({
        loading,
        value: { ...initialState.value, msgFwdConfig, errorState, msgFwdConfigType: 'rsu' },
      })
    })

    it('Updates the state correctly rejected', async () => {
      const loading = false
      const state = reducer(initialState, {
        type: 'config/getRsuMsgFwdFetch/rejected',
        payload: 'error',
      })
      expect(state).toEqual({
        loading,
        value: { ...initialState.value, errorState: 'error' },
      })
    })
  })

  describe('message forwarding mutations', () => {
    const ips = ['192.0.2.10', '192.0.2.11']
    const result = (overrides: Partial<MessageForwardingResult> = {}): MessageForwardingResult => ({
      execution: { taskId: 'task', target: 'udp:192.0.2.10:161', status: 'SUCCEEDED', message: 'Change verified.', step: null, elapsedMs: 10, steps: [] },
      writeState: 'VERIFIED', changed: true, databaseSync: 'UPDATED', ...overrides,
    })
    const getState = (security = true) => jest.fn().mockReturnValue({
      user: { value: { authLoginData: { token: 'token' }, organization: { name: 'Org 1' } } },
      config: { value: { destIp: '198.51.100.3', snmpMsgType: 'map', includeSecurityHeader: security } },
    })
    const run = (operation: 'create' | 'delete', selected = ips) =>
      (operation === 'create' ? submitSnmpSet(selected) : deleteSnmpSet({ ipList: selected, destIp: '198.51.100.3', snmpMsgType: 'map' }))(
        jest.fn(), getState(), undefined
      )

    afterEach(() => jest.restoreAllMocks())

    describe.each(['create', 'delete'] as const)('%s', (operation) => {
      it('uses the Intersection API with the selected organization and normalized message type', async () => {
        const api = jest.spyOn(RsuMessageForwardingApi, operation).mockResolvedValue({
          status: 200, message: '', body: Object.fromEntries(ips.map((ip) => [ip, result()])),
        })
        const legacy = jest.spyOn(RsuApi, 'postRsuData')
        expect((await run(operation)).payload).toEqual({ changeSuccess: true, errorState: '' })
        expect(api).toHaveBeenCalledWith('token', 'Org 1', {
          rsu_ips: ips, message_type: 'MAP', destination_ip: '198.51.100.3',
          ...(operation === 'create' ? { include_security: true } : {}),
        })
        expect(legacy).not.toHaveBeenCalled()
      })

      it('reports partial failures even when HTTP status is 200', async () => {
        jest.spyOn(RsuMessageForwardingApi, operation).mockResolvedValue({ status: 200, message: '', body: {
          [ips[0]]: result(),
          [ips[1]]: result({ execution: { ...result().execution, status: 'TIMED_OUT', message: 'Device did not respond.' }, writeState: 'NOT_SUBMITTED', changed: null, databaseSync: 'NOT_UPDATED' }),
        } })
        const outcome = await run(operation)
        expect(outcome.payload).toEqual({ changeSuccess: false, errorState: expect.stringContaining('1 of 2 RSUs completed successfully') })
        expect(outcome.payload).toEqual(expect.objectContaining({ errorState: expect.stringContaining(`${ips[1]}: Device did not respond.`) }))
      })

      it('warns about uncertain writes and database failures rather than claiming success', async () => {
        const api = jest.spyOn(RsuMessageForwardingApi, operation)
        for (const [failure, message] of [
          [result({ writeState: 'UNKNOWN', changed: null, databaseSync: 'NOT_UPDATED' }), 'check RSU state before retrying'],
          [result({ databaseSync: 'FAILED' }), 'Forwarding change verified, but the database update failed'],
        ] as const) {
          api.mockResolvedValue({ status: 200, message: '', body: { [ips[0]]: failure } })
          expect((await run(operation, [ips[0]])).payload).toEqual({ changeSuccess: false, errorState: expect.stringContaining(message) })
        }
      })

      it('does not treat absent results or transport errors as success', async () => {
        const api = jest.spyOn(RsuMessageForwardingApi, operation)
        for (const response of [null, { status: 200, body: {}, message: '' }, { status: 403, body: null, message: 'Not permitted for this organization' }]) {
          api.mockResolvedValue(response)
          expect((await run(operation)).payload).toEqual({ changeSuccess: false, errorState: expect.any(String) })
        }
        api.mockRejectedValue(new Error('network disconnected'))
        expect((await run(operation)).payload).toEqual({ changeSuccess: false, errorState: expect.stringContaining('check RSU state before retrying') })
      })

      it('supports selections larger than the endpoint limit without repeating targets', async () => {
        const api = jest.spyOn(RsuMessageForwardingApi, operation).mockImplementation(async (_token, _org, body) => ({
          status: 200, message: '', body: Object.fromEntries(body.rsu_ips.map((ip) => [ip, result()])),
        }))
        const selected = Array.from({ length: 201 }, (_, index) => `192.0.2.${index + 1}`)
        expect((await run(operation, [...selected, selected[0]])).payload).toEqual({ changeSuccess: true, errorState: '' })
        expect(api.mock.calls.map((call) => call[2].rsu_ips.length)).toEqual([100, 100, 1])
        expect(api.mock.calls.flatMap((call) => call[2].rsu_ips)).toEqual(selected)
      })

      it('does not send an empty selection', async () => {
        const api = jest.spyOn(RsuMessageForwardingApi, operation)
        expect((await run(operation, [])).payload).toEqual({ changeSuccess: false, errorState: 'No RSUs selected' })
        expect(api).not.toHaveBeenCalled()
      })
    })

    it('treats a confirmed delete no-op as success', async () => {
      jest.spyOn(RsuMessageForwardingApi, 'delete').mockResolvedValue({ status: 200, message: '', body: {
        [ips[0]]: result({ writeState: 'NOT_SUBMITTED', changed: false }),
      } })
      expect((await run('delete', [ips[0]])).payload).toEqual({ changeSuccess: true, errorState: '' })
    })

    it('sends a disabled security header as false', async () => {
      const api = jest.spyOn(RsuMessageForwardingApi, 'create').mockResolvedValue(null)
      await submitSnmpSet(ips)(jest.fn(), getState(false), undefined)
      expect(api).toHaveBeenCalledWith('token', 'Org 1', expect.objectContaining({ include_security: false }))
    })

    describe.each(['submitSnmpSet', 'deleteSnmpSet'])('%s reducer', (name) => {
      it('clears stale outcomes while pending and stores the returned outcome', () => {
        const pending = reducer({ ...initialState, value: { ...initialState.value, changeSuccess: true, errorState: 'old error' } }, { type: `config/${name}/pending` })
        expect(pending.loading).toBe(true)
        expect(pending.value.changeSuccess).toBe(false)
        expect(pending.value.errorState).toBe('')
        const state = reducer(pending, { type: `config/${name}/fulfilled`, payload: { changeSuccess: false, errorState: 'offline' } })
        expect(state.loading).toBe(false)
        expect(state.value.changeSuccess).toBe(false)
        expect(state.value.errorState).toBe('offline')
      })

      it('keeps a rejection readable and clears loading', () => {
        const state = reducer(initialState, { type: `config/${name}/rejected`, error: { message: 'Request rejected' } })
        expect(state.loading).toBe(false)
        expect(state.value.changeSuccess).toBe(false)
        expect(state.value.errorState).toBe('Request rejected')
      })
    })
  })

  describe('rebootRsu', () => {
    it('returns and calls the api correctly', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuApi.postRsuData = jest.fn().mockReturnValue({ status: 200 })

      const arg = ['1.2.3.4', '2.3.4.5']

      const action = rebootRsu(arg)

      const resp = await action(dispatch, getState, undefined)
      expect(RsuApi.postRsuData).toHaveBeenCalledWith(
        'token',
        'Org 1',
        {
          command: 'reboot',
          rsu_ip: arg,
          args: {},
        },
        ''
      )
      expect(resp.payload).toEqual(undefined)
    })

    it('Updates the state correctly pending', async () => {
      const loading = true
      const rebootChangeSuccess = false
      const state = reducer(initialState, {
        type: 'config/rebootRsu/pending',
      })
      expect(state).toEqual({ loading, value: { ...initialState.value, rebootChangeSuccess } })
    })

    it('Updates the state correctly fulfilled', async () => {
      const loading = false
      const rebootChangeSuccess = true
      const state = reducer(initialState, {
        type: 'config/rebootRsu/fulfilled',
      })
      expect(state).toEqual({ loading, value: { ...initialState.value, rebootChangeSuccess } })
    })

    it('Updates the state correctly rejected', async () => {
      const loading = false
      const rebootChangeSuccess = false
      const state = reducer(initialState, {
        type: 'config/rebootRsu/rejected',
      })
      expect(state).toEqual({ loading, value: { ...initialState.value, rebootChangeSuccess } })
    })
  })

  describe('checkFirmwareUpgrade', () => {
    it('returns and calls the api correctly', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuFirmwareApi.postRsuUpgradeData = jest.fn().mockReturnValue({
        status: 200,
        body: {
          upgrade_available: true,
          upgrade_id: 42,
          upgrade_name: 'RSU Firmware v2.0',
          upgrade_version: '2.0',
        },
      })

      const arg = ['1.2.3.4']

      const action = checkFirmwareUpgrade(arg)

      const resp = await action(dispatch, getState, undefined)
      expect(RsuFirmwareApi.postRsuUpgradeData).toHaveBeenCalledWith(
        'token',
        {
          rsu_ip: arg[0],
          args: {},
        },
        '/check'
      )
      expect(resp.payload).toEqual({ firmwareUpgradeAvailable: true, firmwareUpgradeName: 'RSU Firmware v2.0' })
    })

    it('rejects when multiple RSUs are provided', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuFirmwareApi.postRsuUpgradeData = jest.fn()

      const action = checkFirmwareUpgrade(['1.2.3.4', '5.6.7.8'])
      const resp = await action(dispatch, getState, undefined)

      expect(resp.type).toBe('config/checkFirmwareUpgrade/rejected')
      expect(resp.payload).toBe('Firmware upgrade availability check requires exactly one RSU')
      expect(RsuFirmwareApi.postRsuUpgradeData).not.toHaveBeenCalled()
    })

    it('Updates the state correctly pending', async () => {
      const loading = true
      const firmwareUpgradeAvailable = false
      const firmwareUpgradeName = ''
      const firmwareUpgradeErr = false
      const state = reducer(initialState, {
        type: 'config/checkFirmwareUpgrade/pending',
      })
      expect(state).toEqual({
        loading,
        value: { ...initialState.value, firmwareUpgradeAvailable, firmwareUpgradeName, firmwareUpgradeErr },
      })
    })

    it('Updates the state correctly fulfilled', async () => {
      const loading = false
      const firmwareUpgradeAvailable = false
      const firmwareUpgradeName = ''
      const firmwareUpgradeMsg = 'Firmware is up to date!'
      const state = reducer(initialState, {
        type: 'config/checkFirmwareUpgrade/fulfilled',
        payload: { firmwareUpgradeAvailable, firmwareUpgradeName },
      })
      expect(state).toEqual({
        loading,
        value: { ...initialState.value, firmwareUpgradeAvailable, firmwareUpgradeName, firmwareUpgradeMsg },
      })
    })

    it('Updates the state correctly rejected', async () => {
      const loading = false
      const firmwareUpgradeAvailable = false
      const firmwareUpgradeName = ''
      const firmwareUpgradeMsg = 'An error occurred while checking for an upgrade'
      const firmwareUpgradeErr = true
      const state = reducer(initialState, {
        type: 'config/checkFirmwareUpgrade/rejected',
      })
      expect(state).toEqual({
        loading,
        value: {
          ...initialState.value,
          firmwareUpgradeAvailable,
          firmwareUpgradeName,
          firmwareUpgradeMsg,
          firmwareUpgradeErr,
        },
      })
    })

    it('handles 404 EntityNotFoundException from backend (RSU not found)', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuFirmwareApi.postRsuUpgradeData = jest.fn().mockReturnValue({
        __isErrorResponse: true,
        status: 404,
        body: {
          detail: 'Provided RSU IP does not have complete RSU data: 1.2.3.4',
        },
        message: 'Provided RSU IP does not have complete RSU data: 1.2.3.4',
      })

      const arg = ['1.2.3.4']
      const action = checkFirmwareUpgrade(arg)
      const resp = await action(dispatch, getState, undefined)

      expect(resp.type).toBe('config/checkFirmwareUpgrade/rejected')
      expect(resp.payload).toBe('Provided RSU IP does not have complete RSU data: 1.2.3.4')
    })

    it('handles 409 FirmwareUpgradeUnavailableException from backend (already up to date)', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuFirmwareApi.postRsuUpgradeData = jest.fn().mockReturnValue({
        __isErrorResponse: true,
        status: 409,
        body: {
          detail: "Requested RSU '1.2.3.4' is already up to date with the latest firmware",
        },
        message: "Requested RSU '1.2.3.4' is already up to date with the latest firmware",
      })

      const arg = ['1.2.3.4']
      const action = checkFirmwareUpgrade(arg)
      const resp = await action(dispatch, getState, undefined)

      expect(resp.type).toBe('config/checkFirmwareUpgrade/rejected')
      expect(resp.payload).toBe("Requested RSU '1.2.3.4' is already up to date with the latest firmware")
    })
  })

  describe('startFirmwareUpgrade', () => {
    it('returns and calls the api correctly', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuFirmwareApi.postRsuUpgradeData = jest.fn().mockReturnValue({
        status: 200,
        body: {
          '1.2.3.4': {
            code: 200,
            data: {
              message: 'started',
            },
          },
        },
      })

      const arg = ['1.2.3.4']

      const action = startFirmwareUpgrade(arg)

      const resp = await action(dispatch, getState, undefined)
      expect(RsuFirmwareApi.postRsuUpgradeData).toHaveBeenCalledWith(
        'token',
        {
          rsu_ips: arg,
          args: {},
        },
        ''
      )
      expect(resp.payload).toEqual({ message: 'Firmware upgrade started successfully.', statusCode: 200 })
    })

    it('returns a concise single-rsu failure message', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuFirmwareApi.postRsuUpgradeData = jest.fn().mockReturnValue({
        status: 200,
        body: {
          '1.2.3.4': {
            code: 500,
            data: 'device is unreachable',
          },
        },
      })

      const arg = ['1.2.3.4']
      const action = startFirmwareUpgrade(arg)
      const resp = await action(dispatch, getState, undefined)

      expect(resp.payload).toEqual({ message: 'Firmware upgrade failed to start.', statusCode: 500 })
    })

    it('returns an informational message when a single RSU is already up to date', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuFirmwareApi.postRsuUpgradeData = jest.fn().mockReturnValue({
        status: 200,
        body: {
          '1.2.3.4': {
            code: 409,
            data: "Requested RSU '1.2.3.4' is already up to date with the latest firmware",
          },
        },
      })

      const arg = ['1.2.3.4']
      const action = startFirmwareUpgrade(arg)
      const resp = await action(dispatch, getState, undefined)

      expect(resp.payload).toEqual({ message: 'Selected RSU is already up to date.', statusCode: 200 })
    })

    it('excludes up-to-date RSUs from multi-rsu failed list', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })

      const arg = ['10.0.0.78', '10.0.0.79', '10.0.0.80']
      RsuFirmwareApi.postRsuUpgradeData = jest.fn().mockReturnValue({
        status: 200,
        body: {
          '10.0.0.78': {
            code: 409,
            data: "Requested RSU '10.0.0.78' is already up to date with the latest firmware",
          },
          '10.0.0.79': {
            code: 200,
            data: { message: 'started' },
          },
          '10.0.0.80': {
            code: 500,
            data: 'device unreachable',
          },
        },
      })

      const action = startFirmwareUpgrade(arg)
      const resp = await action(dispatch, getState, undefined)

      expect(resp.payload).toEqual({
        message: 'Firmware upgrade started for 1 RSUs. 1 RSUs already up to date. Failed: 10.0.0.80',
        statusCode: 500,
      })
    })

    it('Updates the state correctly pending', async () => {
      const loading = true
      const firmwareUpgradeErr = false
      const state = reducer(initialState, {
        type: 'config/startFirmwareUpgrade/pending',
      })
      expect(state).toEqual({
        loading,
        value: { ...initialState.value, firmwareUpgradeErr },
      })
    })

    it('Updates the state correctly fulfilled', async () => {
      const loading = false
      const firmwareUpgradeAvailable = false
      const firmwareUpgradeName = ''
      const firmwareUpgradeMsg = 'Firmware is up to date!'
      const firmwareUpgradeErr = false
      const statusCode = 201
      const state = reducer(initialState, {
        type: 'config/startFirmwareUpgrade/fulfilled',
        payload: { message: firmwareUpgradeMsg, statusCode },
      })
      expect(state).toEqual({
        loading,
        value: {
          ...initialState.value,
          firmwareUpgradeAvailable,
          firmwareUpgradeName,
          firmwareUpgradeMsg,
          firmwareUpgradeErr,
        },
      })
    })

    it('Updates the state correctly rejected', async () => {
      const loading = false
      const firmwareUpgradeAvailable = false
      const firmwareUpgradeName = ''
      const firmwareUpgradeMsg = 'An error occurred while starting the firmware upgrade'
      const firmwareUpgradeErr = true
      const state = reducer(initialState, {
        type: 'config/startFirmwareUpgrade/rejected',
      })
      expect(state).toEqual({
        loading,
        value: {
          ...initialState.value,
          firmwareUpgradeAvailable,
          firmwareUpgradeName,
          firmwareUpgradeMsg,
          firmwareUpgradeErr,
        },
      })
    })

    it('handles 404 per-RSU result from backend (RSU not found)', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuFirmwareApi.postRsuUpgradeData = jest.fn().mockReturnValue({
        status: 200,
        body: {
          '1.2.3.4': {
            code: 404,
            data: 'Provided RSU IP does not have complete RSU data: 1.2.3.4',
          },
        },
      })

      const arg = ['1.2.3.4']
      const action = startFirmwareUpgrade(arg)
      const resp = await action(dispatch, getState, undefined)

      expect(resp.type).toBe('config/startFirmwareUpgrade/fulfilled')
      expect(resp.payload).toEqual({ message: 'Firmware upgrade failed to start.', statusCode: 404 })
    })

    it('handles 501 per-RSU result when firmware manager is not supported', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuFirmwareApi.postRsuUpgradeData = jest.fn().mockReturnValue({
        status: 200,
        body: {
          '1.2.3.4': {
            code: 501,
            data: 'The firmware manager is not supported for this CV Manager deployment',
          },
        },
      })

      const arg = ['1.2.3.4']
      const action = startFirmwareUpgrade(arg)
      const resp = await action(dispatch, getState, undefined)

      expect(resp.type).toBe('config/startFirmwareUpgrade/fulfilled')
      expect(resp.payload).toEqual({ message: 'Firmware upgrade failed to start.', statusCode: 501 })
    })

    it('handles multi-rsu response with mixed success and error codes', async () => {
      const dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
            organization: { name: 'Org 1' },
          },
        },
      })
      RsuFirmwareApi.postRsuUpgradeData = jest.fn().mockReturnValue({
        status: 200,
        body: {
          '1.2.3.4': {
            code: 404,
            data: 'Provided RSU IP does not have complete RSU data',
          },
          '2.3.4.5': {
            code: 200,
            data: { message: 'started' },
          },
          '3.4.5.6': {
            code: 409,
            data: "Requested RSU '3.4.5.6' is already up to date with the latest firmware",
          },
        },
      })

      const arg = ['1.2.3.4', '2.3.4.5', '3.4.5.6']
      const action = startFirmwareUpgrade(arg)
      const resp = await action(dispatch, getState, undefined)

      expect(resp.type).toBe('config/startFirmwareUpgrade/fulfilled')
      const payload = resp.payload as { statusCode: number; message: string }
      expect(payload.statusCode).toBe(404)
      expect(payload.message).toContain('Firmware upgrade started for 1 RSUs')
      expect(payload.message).toContain('1 RSUs already up to date')
      expect(payload.message).toContain('Failed: 1.2.3.4')
    })
  })
})

describe('reducers', () => {
  const initialState = {
    loading: null,
    value: {
      msgFwdConfig: null,
      errorState: null,
      changeSuccess: false,
      rebootChangeSuccess: false,
      firmwareUpgradeAvailable: false,
      firmwareUpgradeName: '',
      firmwareUpgradeMsg: '',
      firmwareUpgradeErr: false,
      destIp: '',
      snmpMsgType: 'bsm',
      includeSecurityHeader: false,
      addConfigPoint: false,
      configCoordinates: null,
      configList: null,
    },
  } as RootState['config']

  it('reducer displays 404 EntityNotFoundException error from check thunk', async () => {
    const errorMsg = 'Provided RSU IP does not have complete RSU data: 1.2.3.4'
    const state = reducer(initialState, {
      type: 'config/checkFirmwareUpgrade/rejected',
      payload: errorMsg,
    })
    expect(state.value.firmwareUpgradeErr).toBe(true)
    expect(state.value.firmwareUpgradeMsg).toBe(errorMsg)
  })

  it('reducer displays 409 FirmwareUpgradeUnavailableException error from check thunk', async () => {
    const errorMsg = "Requested RSU '1.2.3.4' is already up to date with the latest firmware"
    const state = reducer(initialState, {
      type: 'config/checkFirmwareUpgrade/rejected',
      payload: errorMsg,
    })
    expect(state.value.firmwareUpgradeErr).toBe(true)
    expect(state.value.firmwareUpgradeMsg).toBe(errorMsg)
  })

  it('reducer displays 404 per-RSU error from a fulfilled start thunk', async () => {
    const errorMsg = 'Firmware upgrade failed to start.'
    const state = reducer(initialState, {
      type: 'config/startFirmwareUpgrade/fulfilled',
      payload: { message: errorMsg, statusCode: 404 },
    })
    expect(state.value.firmwareUpgradeErr).toBe(true)
    expect(state.value.firmwareUpgradeMsg).toBe(errorMsg)
  })

  it('reducer displays 501 per-RSU error from a fulfilled start thunk', async () => {
    const errorMsg = 'Firmware upgrade failed to start.'
    const state = reducer(initialState, {
      type: 'config/startFirmwareUpgrade/fulfilled',
      payload: { message: errorMsg, statusCode: 501 },
    })
    expect(state.value.firmwareUpgradeErr).toBe(true)
    expect(state.value.firmwareUpgradeMsg).toBe(errorMsg)
  })

  it('setDestIp reducer updates state correctly', async () => {
    const destIp = 'updated'
    expect(reducer(initialState, setDestIp(destIp))).toEqual({
      ...initialState,
      value: { ...initialState.value, destIp },
    })
  })

  it('setMsgType reducer updates state correctly', async () => {
    const snmpMsgType = 'updated'
    expect(reducer(initialState, setMsgType(snmpMsgType))).toEqual({
      ...initialState,
      value: { ...initialState.value, snmpMsgType },
    })
  })

  it('toggleConfigPointSelect reducer updates state correctly', async () => {
    const addConfigPoint = initialState.value.addConfigPoint
    expect(reducer(initialState, toggleConfigPointSelect())).toEqual({
      ...initialState,
      value: { ...initialState.value, addConfigPoint: !addConfigPoint },
    })
  })
})

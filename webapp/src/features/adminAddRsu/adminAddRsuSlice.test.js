import reducer from './adminAddRsuSlice'
import {
  // async thunks
  getRsuCreationData,
  createRsu,
  submitForm,

  // functions
  updateApiJson,
  checkForm,
  updateJson,

  // reducers
  setSuccessMsg,
  updateSelectedRoute,
  updateSelectedModel,
  updateSelectedSshGroup,
  updateSelectedSnmpGroup,
  updateSelectedOrganizations,
  resetForm,

  // selectors
  selectApiData,
  selectPrimaryRoutes,
  selectRsuModels,
  selectSshCredentialGroups,
  selectSnmpCredentialGroups,
  selectOrganizations,
  selectSuccessMsg,
  selectErrorState,
  selectErrorMsg,
  selectSelectedRoute,
  selectOtherRouteDisabled,
  selectSelectedModel,
  selectSelectedSshGroup,
  selectSelectedSnmpGroup,
  selectSelectedOrganizations,
  selectSubmitAttempt,
  selectLoading,
} from './adminAddRsuSlice'
import apiHelper from '../../apis/api-helper'
import EnvironmentVars from '../../EnvironmentVars'

describe('admin add RSU reducer', () => {
  it('should handle initial state', () => {
    expect(reducer(undefined, { type: 'unknown' })).toEqual({
      loading: false,
      value: {
        successMsg: '',
        apiData: {},
        errorState: false,
        errorMsg: '',
        primaryRoutes: [],
        selectedRoute: 'Select Route',
        otherRouteDisabled: true,
        rsuModels: [],
        selectedModel: 'Select RSU Model',
        sshCredentialGroups: [],
        selectedSshGroup: 'Select SSH Group',
        snmpCredentialGroups: [],
        selectedSnmpGroup: 'Select SNMP Group',
        organizations: [],
        selectedOrganizations: [],
        submitAttempt: false,
      },
    })
  })
})

describe('async thunks', () => {
  const initialState = {
    loading: null,
    value: {
      successMsg: 'successMsg',
      apiData: null,
      errorState: null,
      errorMsg: null,
      primaryRoutes: null,
      selectedRoute: null,
      otherRouteDisabled: null,
      rsuModels: null,
      selectedModel: null,
      sshCredentialGroups: null,
      selectedSshGroup: null,
      snmpCredentialGroups: null,
      selectedSnmpGroup: null,
      organizations: null,
      selectedOrganizations: null,
      submitAttempt: null,
    },
  }

  beforeAll(() => {
    jest.mock('../../apis/api-helper')
  })

  afterAll(() => {
    jest.unmock('../../apis/api-helper')
  })

  describe('getRsuCreationData', () => {
    it('returns and calls the api correctly', async () => {
      let dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
          },
        },
      })
      const action = getRsuCreationData()

      const apiJson = { data: 'data' }
      apiHelper._getData = jest.fn().mockReturnValue('_getData_response')
      let resp = await action(dispatch, getState, undefined)
      expect(resp.payload).toEqual(undefined)
      expect(apiHelper._getData).toHaveBeenCalledWith({
        url: EnvironmentVars.adminAddRsu,
        token: 'token',
        additional_headers: { 'Content-Type': 'application/json' },
      })
    })

    it('Updates the state correctly pending', async () => {
      const loading = true
      const errorState = false
      const state = reducer(initialState, {
        type: 'adminAddRsu/getRsuCreationData/pending',
      })
      expect(state).toEqual({
        ...initialState,
        loading,
        value: { ...initialState.value, errorState },
      })
    })

    it('Updates the state correctly fulfilled', async () => {
      const loading = false
      const errorState = false
      const apiData = 'apiData'
      const state = reducer(initialState, {
        type: 'adminAddRsu/getRsuCreationData/fulfilled',
        payload: apiData,
      })

      expect(state).toEqual({
        ...initialState,
        loading,
        value: { ...initialState.value, errorState, apiData },
      })
    })

    it('Updates the state correctly rejected', async () => {
      const loading = false
      const errorState = true
      const state = reducer(initialState, {
        type: 'adminAddRsu/getRsuCreationData/rejected',
      })
      expect(state).toEqual({ ...initialState, loading, value: { ...initialState.value, errorState } })
    })
  })

  describe('createRsu', () => {
    it('returns and calls the api correctly', async () => {
      let dispatch = jest.fn()
      const getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
          },
        },
      })
      const json = { data: 'data' }

      let reset = jest.fn()
      let action = createRsu({ json, reset })
      global.setTimeout = jest.fn((cb) => cb())
      try {
        apiHelper._postData = jest.fn().mockReturnValue({ status: 200, message: 'message' })
        let resp = await action(dispatch, getState, undefined)
        expect(resp.payload).toEqual({ success: true, message: '' })
        expect(apiHelper._postData).toHaveBeenCalledWith({
          url: EnvironmentVars.adminAddRsu,
          token: 'token',
          body: JSON.stringify(json),
        })
        expect(setTimeout).toHaveBeenCalledTimes(1)
        expect(dispatch).toHaveBeenCalledTimes(3 + 2)
        expect(reset).toHaveBeenCalledTimes(1)
      } catch (e) {
        global.setTimeout.mockClear()
        throw e
      }

      // Error Code Other
      dispatch = jest.fn()
      reset = jest.fn()
      action = createRsu({ json, reset })
      global.setTimeout = jest.fn((cb) => cb())
      try {
        apiHelper._postData = jest.fn().mockReturnValue({ status: 500, message: 'message' })
        let resp = await action(dispatch, getState, undefined)
        expect(resp.payload).toEqual({ success: false, message: 'message' })
        expect(apiHelper._postData).toHaveBeenCalledWith({
          url: EnvironmentVars.adminAddRsu,
          token: 'token',
          body: JSON.stringify(json),
        })
        expect(setTimeout).not.toHaveBeenCalled()
        expect(dispatch).toHaveBeenCalledTimes(0 + 2)
        expect(reset).not.toHaveBeenCalled()
      } catch (e) {
        global.setTimeout.mockClear()
        throw e
      }
    })

    it('Updates the state correctly pending', async () => {
      const loading = true
      const errorState = false
      const state = reducer(initialState, {
        type: 'adminAddRsu/createRsu/pending',
      })
      expect(state).toEqual({
        ...initialState,
        loading,
        value: { ...initialState.value, errorState },
      })
    })

    it('Updates the state correctly fulfilled', async () => {
      const loading = false
      let successMsg = 'RSU Creation is successful.'
      let errorMsg = ''
      let errorState = false

      let state = reducer(initialState, {
        type: 'adminAddRsu/createRsu/fulfilled',
        payload: { message: 'message', success: true },
      })

      expect(state).toEqual({
        ...initialState,
        loading,
        value: { ...initialState.value, successMsg, errorMsg, errorState },
      })

      // Error Case
      successMsg = ''
      errorMsg = 'message'
      errorState = true

      state = reducer(initialState, {
        type: 'adminAddRsu/createRsu/fulfilled',
        payload: { message: 'message', success: false },
      })

      expect(state).toEqual({
        ...initialState,
        loading,
        value: { ...initialState.value, successMsg, errorMsg, errorState },
      })
    })

    it('Updates the state correctly rejected', async () => {
      const loading = false
      const errorState = true
      const errorMsg = 'unknown error'
      const state = reducer(initialState, {
        type: 'adminAddRsu/createRsu/rejected',
      })
      expect(state).toEqual({ ...initialState, loading, value: { ...initialState.value, errorState, errorMsg } })
    })
  })

  describe('submitForm', () => {
    it('returns and calls the api correctly', async () => {
      let dispatch = jest.fn()
      let getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
          },
        },
        adminAddRsu: {
          value: {
            selectedRoute: 'I-25',
            selectedModel: 'model1',
            selectedSshGroup: 'group1',
            selectedSnmpGroup: 'group1snmp',
            selectedOrganizations: ['org1'],
          },
        },
      })
      const data = { data: 'data' }

      let reset = jest.fn()
      let action = submitForm({ data, reset })
      let resp = await action(dispatch, getState, undefined)
      expect(resp.payload).toEqual(false)
      expect(dispatch).toHaveBeenCalledTimes(1 + 2)

      // invalid checkForm

      dispatch = jest.fn()
      getState = jest.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
          },
        },
        adminAddRsu: {
          value: {
            selectedRoute: 'Select Route',
            selectedModel: 'Select RSU Model',
            selectedSshGroup: 'Select SSH Group',
            selectedSnmpGroup: 'Select SNMP Group',
            selectedOrganizations: [],
          },
        },
      })
      action = submitForm({ data, reset })
      resp = await action(dispatch, getState, undefined)
      expect(resp.payload).toEqual(true)
      expect(setTimeout).not.toHaveBeenCalled()
      expect(dispatch).toHaveBeenCalledTimes(0 + 2)
    })

    it('Updates the state correctly fulfilled', async () => {
      const submitAttempt = 'submitAttempt'

      const state = reducer(initialState, {
        type: 'adminAddRsu/submitForm/fulfilled',
        payload: submitAttempt,
      })

      expect(state).toEqual({
        ...initialState,
        value: { ...initialState.value, submitAttempt },
      })
    })
  })
})

describe('functions', () => {
  it('updateApiJson', async () => {
    // write test for updateApiJson
    const apiJson = {
      primary_routes: ['I-25', 'I-70'],
      rsu_models: ['model1', 'model2'],
      ssh_credential_groups: ['group1', 'group2'],
      snmp_credential_groups: ['group1snmp', 'group2snmp'],
      organizations: ['org1', 'org2'],
    }

    const expected = {
      primary_routes: [
        { id: 0, name: 'I-25' },
        { id: 1, name: 'I-70' },
      ],
      rsu_models: [
        { id: 0, name: 'model1' },
        { id: 1, name: 'model2' },
      ],
      ssh_credential_groups: [
        { id: 0, name: 'group1' },
        { id: 1, name: 'group2' },
      ],
      snmp_credential_groups: [
        { id: 0, name: 'group1snmp' },
        { id: 1, name: 'group2snmp' },
      ],
      organizations: [
        { id: 0, name: 'org1' },
        { id: 1, name: 'org2' },
      ],
    }
    expect(updateApiJson(apiJson)).toEqual(expected)
  })

  it('checkForm selectedRoute', async () => {
    expect(
      checkForm({
        value: {
          selectedRoute: 'Select Route',
        },
      })
    ).toEqual(false)
  })

  it('checkForm selectedModel', async () => {
    expect(
      checkForm({
        value: {
          selectedModel: 'Select RSU Model',
        },
      })
    ).toEqual(false)
  })

  it('checkForm selectedSshGroup', async () => {
    expect(
      checkForm({
        value: {
          selectedSshGroup: 'Select SSH Group',
        },
      })
    ).toEqual(false)
  })

  it('checkForm selectedSnmpGroup', async () => {
    expect(
      checkForm({
        value: {
          selectedSnmpGroup: 'Select SNMP Group',
        },
      })
    ).toEqual(false)
  })

  it('checkForm selectedOrganizations', async () => {
    expect(
      checkForm({
        value: {
          selectedOrganizations: [],
        },
      })
    ).toEqual(false)
  })

  it('checkForm all invalid', async () => {
    expect(
      checkForm({
        value: {
          selectedRoute: 'Select Route',
          selectedModel: 'Select RSU Model',
          selectedSshGroup: 'Select SSH Group',
          selectedSnmpGroup: 'Select SNMP Group',
          selectedOrganizations: [],
        },
      })
    ).toEqual(false)
  })

  it('checkForm all valid', async () => {
    expect(
      checkForm({
        value: {
          selectedRoute: 'I-25',
          selectedModel: 'model1',
          selectedSshGroup: 'group1',
          selectedSnmpGroup: 'group1snmp',
          selectedOrganizations: ['org1'],
        },
      })
    ).toEqual(true)
  })

  it('updateJson', async () => {
    const data = {
      latitude: 39.7392,
      longitude: -104.9903,
      milepost: 0.0,
    }

    const state = {
      value: {
        selectedRoute: 'I-25',
        selectedModel: 'model1',
        selectedSshGroup: 'group1',
        selectedSnmpGroup: 'group1snmp',
        selectedOrganizations: [{ name: 'org1' }],
      },
    }

    const expected = {
      primary_route: 'I-25',
      milepost: 0.0,
      model: 'model1',
      geo_position: {
        latitude: 39.7392,
        longitude: -104.9903,
      },
      ssh_credential_group: 'group1',
      snmp_credential_group: 'group1snmp',
      organizations: ['org1'],
    }

    expect(updateJson(data, state)).toEqual(expected)
  })

  it('updateJson Other Route', async () => {
    const data = {
      latitude: 39.7392,
      longitude: -104.9903,
      milepost: 0.0,
    }

    const state = {
      value: {
        selectedRoute: 'Other',
        selectedModel: 'model1',
        selectedSshGroup: 'group1',
        selectedSnmpGroup: 'group1snmp',
        selectedOrganizations: [{ name: 'org1' }],
      },
    }

    const expected = {
      milepost: 0.0,
      model: 'model1',
      geo_position: {
        latitude: 39.7392,
        longitude: -104.9903,
      },
      ssh_credential_group: 'group1',
      snmp_credential_group: 'group1snmp',
      organizations: ['org1'],
    }

    expect(updateJson(data, state)).toEqual(expected)
  })
})

describe('reducers', () => {
  const initialState = {
    loading: null,
    value: {
      selectedRsu: null,
    },
  }

  it('setSuccessMsg reducer updates state correctly', async () => {
    const successMsg = 'successMsg'
    expect(reducer(initialState, setSuccessMsg(successMsg))).toEqual({
      ...initialState,
      value: { ...initialState.value, successMsg },
    })
  })

  it('updateSelectedRoute reducer updates state correctly', async () => {
    let selectedRoute = 'selectedRoute'
    let otherRouteDisabled = false
    expect(reducer(initialState, updateSelectedRoute(selectedRoute))).toEqual({
      ...initialState,
      value: { ...initialState.value, selectedRoute, otherRouteDisabled },
    })

    selectedRoute = 'Other'
    otherRouteDisabled = true
    expect(reducer(initialState, updateSelectedRoute(selectedRoute))).toEqual({
      ...initialState,
      value: { ...initialState.value, selectedRoute, otherRouteDisabled },
    })
  })

  it('updateSelectedModel reducer updates state correctly', async () => {
    const selectedModel = 'selectedModel'
    expect(reducer(initialState, updateSelectedModel(selectedModel))).toEqual({
      ...initialState,
      value: { ...initialState.value, selectedModel },
    })
  })

  it('updateSelectedSshGroup reducer updates state correctly', async () => {
    const selectedSshGroup = 'selectedSshGroup'
    expect(reducer(initialState, updateSelectedSshGroup(selectedSshGroup))).toEqual({
      ...initialState,
      value: { ...initialState.value, selectedSshGroup },
    })
  })

  it('updateSelectedSnmpGroup reducer updates state correctly', async () => {
    const selectedSnmpGroup = 'selectedSnmpGroup'
    expect(reducer(initialState, updateSelectedSnmpGroup(selectedSnmpGroup))).toEqual({
      ...initialState,
      value: { ...initialState.value, selectedSnmpGroup },
    })
  })

  it('updateSelectedOrganizations reducer updates state correctly', async () => {
    const selectedOrganizations = 'selectedOrganizations'
    expect(reducer(initialState, updateSelectedOrganizations(selectedOrganizations))).toEqual({
      ...initialState,
      value: { ...initialState.value, selectedOrganizations },
    })
  })

  it('resetForm reducer updates state correctly', async () => {
    const selectedRoute = 'Select Route'
    const otherRouteDisabled = false
    const selectedModel = 'Select RSU Model'
    const selectedSshGroup = 'Select SSH Group'
    const selectedSnmpGroup = 'Select SNMP Group'
    const selectedOrganizations = []
    expect(reducer(initialState, resetForm(selectedOrganizations))).toEqual({
      ...initialState,
      value: {
        ...initialState.value,
        selectedRoute,
        otherRouteDisabled,
        selectedModel,
        selectedSshGroup,
        selectedSnmpGroup,
        selectedOrganizations,
      },
    })
  })
})

describe('selectors', () => {
  const initialState = {
    loading: 'loading',
    value: {
      successMsg: 'successMsg',
      apiData: {
        primary_routes: ['I-25', 'I-70'],
        rsu_models: ['model1', 'model2'],
        ssh_credential_groups: ['group1', 'group2'],
        snmp_credential_groups: ['group1snmp', 'group2snmp'],
        organizations: ['org1', 'org2'],
      },
      errorState: 'errorState',
      errorMsg: 'errorMsg',
      primaryRoutes: 'primaryRoutes',
      selectedRoute: 'selectedRoute',
      otherRouteDisabled: 'otherRouteDisabled',
      rsuModels: 'rsuModels',
      selectedModel: 'selectedModel',
      sshCredentialGroups: 'sshCredentialGroups',
      selectedSshGroup: 'selectedSshGroup',
      snmpCredentialGroups: 'snmpCredentialGroups',
      selectedSnmpGroup: 'selectedSnmpGroup',
      organizations: 'organizations',
      selectedOrganizations: 'selectedOrganizations',
      submitAttempt: 'submitAttempt',
    },
  }
  const state = { adminAddRsu: initialState }

  it('selectors return the correct value', async () => {
    expect(selectLoading(state)).toEqual('loading')

    expect(selectSuccessMsg(state)).toEqual('successMsg')
    expect(selectApiData(state)).toEqual(initialState.value.apiData)
    expect(selectPrimaryRoutes(state)).toEqual(initialState.value.apiData.primary_routes)
    expect(selectRsuModels(state)).toEqual(initialState.value.apiData.rsu_models)
    expect(selectSshCredentialGroups(state)).toEqual(initialState.value.apiData.ssh_credential_groups)
    expect(selectSnmpCredentialGroups(state)).toEqual(initialState.value.apiData.snmp_credential_groups)
    expect(selectOrganizations(state)).toEqual(initialState.value.apiData.organizations)
    expect(selectErrorState(state)).toEqual('errorState')
    expect(selectErrorMsg(state)).toEqual('errorMsg')
    expect(selectSelectedRoute(state)).toEqual('selectedRoute')
    expect(selectOtherRouteDisabled(state)).toEqual('otherRouteDisabled')
    expect(selectSelectedModel(state)).toEqual('selectedModel')
    expect(selectSelectedSshGroup(state)).toEqual('selectedSshGroup')
    expect(selectSelectedSnmpGroup(state)).toEqual('selectedSnmpGroup')
    expect(selectSelectedOrganizations(state)).toEqual('selectedOrganizations')
    expect(selectSubmitAttempt(state)).toEqual('submitAttempt')
  })

  it('selectors return the correct value defaults', async () => {
    initialState.value.apiData = undefined
    expect(selectApiData(state)).toEqual(undefined)
    expect(selectPrimaryRoutes(state)).toEqual([])
    expect(selectRsuModels(state)).toEqual([])
    expect(selectSshCredentialGroups(state)).toEqual([])
    expect(selectSnmpCredentialGroups(state)).toEqual([])
    expect(selectOrganizations(state)).toEqual([])
  })
})

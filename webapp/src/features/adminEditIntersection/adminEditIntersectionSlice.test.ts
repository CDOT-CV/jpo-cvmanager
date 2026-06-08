import reducer from './adminEditIntersectionSlice'
import {
  // functions
  validateFormContents,
  mapFormToRequestJson,

  // reducers
  clear,
  setSelectedOrganizations,
  setSelectedRsus,
  setSubmitAttempt,
  updateStates,

  // selectors
  selectApiData,
  selectOrganizations,
  selectSelectedOrganizations,
  selectRsus,
  selectSelectedRsus,
  selectSubmitAttempt,
} from './adminEditIntersectionSlice'
import { RootState } from '../../store'

describe('admin edit Intersection reducer', () => {
  it('should handle initial state', () => {
    expect(reducer(undefined, { type: 'unknown' })).toEqual({
      value: {
        apiData: undefined,
        organizations: [],
        selectedOrganizations: [],
        rsus: [],
        selectedRsus: [],
        submitAttempt: false,
      },
    })
  })
})

describe('functions', () => {
  it('checkForm selectedOrganizations', async () => {
    expect(
      validateFormContents({
        value: {
          selectedOrganizations: [],
          selectedRsus: [],
        },
      } as any)
    ).toEqual(false)
  })

  it('checkForm all invalid', async () => {
    expect(
      validateFormContents({
        value: {
          selectedOrganizations: [],
          selectedRsus: [],
        },
      } as any)
    ).toEqual(false)
  })

  it('checkForm all valid', async () => {
    expect(
      validateFormContents({
        value: {
          selectedOrganizations: [1],
          selectedRsus: ['rsu1'],
        },
      } as any)
    ).toEqual(true)
  })

  it('updateJson', async () => {
    const data = {
      intersection_name: 'a',
    } as any
    const state = {
      value: {
        selectedOrganizations: [{ id: 1 }],
        selectedRsus: [{ name: 'rsu1' }],
      },
    } as any

    const expected = {
      intersection_name: 'a',
      organizations: [1],
      rsus: ['rsu1'],
    }

    expect(mapFormToRequestJson(data, state)).toEqual(expected)
  })

  it('updateJson selectedRoute Other', async () => {
    const data = {
      intersection_name: 'a',
    } as any
    const state = {
      value: {
        apiData: {
          allowed_selections: {
            organizations: [1, 2, 4],
            rsus: ['rsu1', 'rsu2', 'rsu4'],
          },
          intersection_data: {
            organizations: [2, 4],
            rsus: ['rsu2', 'rsu4'],
          },
        },
        selectedOrganizations: [{ id: 1 }, { id: 2 }, { id: 3 }],
        selectedRsus: [{ name: 'rsu1' }, { name: 'rsu2' }, { name: 'rsu3' }],
      },
    } as any

    const expected = {
      intersection_name: 'a',
      organizations: [1, 2, 3],
      rsus: ['rsu1', 'rsu2', 'rsu3'],
    }

    expect(mapFormToRequestJson(data, state)).toEqual(expected)
  })
})

describe('reducers', () => {
  const initialState: RootState['adminEditIntersection'] = {
    value: {
      apiData: undefined,
      organizations: [] as { id: number }[],
      selectedOrganizations: [] as { id: number }[],
      rsus: [] as { name: string }[],
      selectedRsus: [] as { name: string }[],
      submitAttempt: false,
    },
  }

  it('clear reducer updates state correctly', async () => {
    const selectedOrganizations = [{ id: 1 }]

    expect(reducer({ ...initialState, value: { ...initialState.value, selectedOrganizations } }, clear())).toEqual({
      ...initialState,
      value: {
        ...initialState.value,
      },
    })
  })

  it('setSelectedOrganizations reducer updates state correctly', async () => {
    const selectedOrganizations = 'selectedOrganizations'
    expect(reducer(initialState, setSelectedOrganizations(selectedOrganizations))).toEqual({
      ...initialState,
      value: { ...initialState.value, selectedOrganizations },
    })
  })

  it('setSelectedRsus reducer updates state correctly', async () => {
    const selectedRsus = 'selectedRsus'
    expect(reducer(initialState, setSelectedRsus(selectedRsus))).toEqual({
      ...initialState,
      value: { ...initialState.value, selectedRsus },
    })
  })

  it('setSubmitAttempt reducer updates state correctly', async () => {
    expect(reducer(initialState, setSubmitAttempt(true))).toEqual({
      ...initialState,
      value: { ...initialState.value, submitAttempt: true },
    })
  })

  it('updateStates', async () => {
    const apiData = {
      allowed_selections: {
        organizations: [1, 2],
        rsus: ['rsu1', 'rsu2'],
      },
      intersection_data: {
        organizations: [1, 2],
        rsus: ['rsu1', 'rsu2'],
      },
    } as any

    const values = {
      organizations: [{ id: 1 }, { id: 2 }],
      rsus: [{ name: 'rsu1' }, { name: 'rsu2' }],
      selectedOrganizations: [{ id: 1 }, { id: 2 }],
      selectedRsus: [{ name: 'rsu1' }, { name: 'rsu2' }],
    }
    expect(reducer(initialState, updateStates(apiData))).toEqual({
      ...initialState,
      value: { ...initialState.value, ...values, apiData },
    })
  })
})

describe('selectors', () => {
  const initialState = {
    value: {
      apiData: 'apiData',
      organizations: [{ id: 1 }],
      selectedOrganizations: [{ id: 1 }],
      rsus: 'rsus',
      selectedRsus: 'selectedRsus',
      submitAttempt: 'submitAttempt',
    },
  }
  const org = { organization: 1, name: 'Org 1', email: 'org1@example.com', role: 'ADMIN' }
  const initialUserState = { value: { authLoginData: { data: { organizations: [org] } } } }
  const state = { user: initialUserState, adminEditIntersection: initialState } as any

  it('selectors return the correct value', async () => {
    expect(selectApiData(state)).toEqual('apiData')
    expect(selectOrganizations(state)).toEqual([{ id: 1, name: 'Org 1' }])
    expect(selectSelectedOrganizations(state)).toEqual([{ id: 1, name: 'Org 1' }])
    expect(selectRsus(state)).toEqual('rsus')
    expect(selectSelectedRsus(state)).toEqual('selectedRsus')
    expect(selectSubmitAttempt(state)).toEqual('submitAttempt')
  })
})

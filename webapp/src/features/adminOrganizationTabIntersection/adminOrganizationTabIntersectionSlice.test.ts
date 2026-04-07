import reducer from './adminOrganizationTabIntersectionSlice'
import {
  intersectionDeleteSingle,
  intersectionDeleteMultiple,
  refresh,
  selectLoading,
} from './adminOrganizationTabIntersectionSlice'

describe('admin organization tab Intersection reducer', () => {
  it('should handle initial state', () => {
    expect(reducer(undefined, { type: 'unknown' })).toEqual({
      loading: false,
      value: {},
    })
  })
})

describe('async thunks', () => {
  const makeGetState = () =>
    jest.fn().mockReturnValue({
      user: {
        value: {
          authLoginData: { token: 'token' },
        },
      },
    })

  const makeMockDispatch = (unwrapValue: any, editOrgResult = { payload: { success: true } }) => {
    const dispatch = jest.fn()
    dispatch.mockImplementation((action: any) => {
      if (typeof action === 'function') {
        return action(dispatch, makeGetState(), undefined)
      }
      // RTK Query initiate() calls return a promise-like with unwrap()
      if (action?.type?.includes('initiate')) {
        return { unwrap: jest.fn().mockResolvedValue(unwrapValue) }
      }
      // editOrg and other thunk dispatches
      return editOrgResult
    })
    return dispatch
  }

  describe('intersectionDeleteSingle', () => {
    it('dispatches editOrg when intersection has multiple orgs', async () => {
      const intersectionData = {
        intersection_data: { organizations: ['org1', 'org2'] },
        allowed_selections: {},
      }

      const dispatch = makeMockDispatch(intersectionData)
      const getState = makeGetState()

      const intersection = { intersection_id: '1' } as any
      const selectedOrg = 'selectedOrg'
      const selectedOrgEmail = 'name@email.com'
      const updateTableData = jest.fn()

      const action = intersectionDeleteSingle({ intersection, selectedOrg, selectedOrgEmail, updateTableData })
      const result = await action(dispatch, getState, undefined)

      expect(result.payload).toEqual({ success: true, message: 'Intersection deleted successfully' })
    })

    it('alerts when intersection belongs to only one org', async () => {
      const intersectionData = {
        intersection_data: { organizations: ['org1'] },
        allowed_selections: {},
      }

      const dispatch = makeMockDispatch(intersectionData)
      const getState = makeGetState()

      const jsdomAlert = window.alert
      window.alert = jest.fn()

      try {
        const intersection = { intersection_id: '1' } as any
        const selectedOrg = 'selectedOrg'
        const selectedOrgEmail = 'name@email.com'
        const updateTableData = jest.fn()

        const action = intersectionDeleteSingle({ intersection, selectedOrg, selectedOrgEmail, updateTableData })
        await action(dispatch, getState, undefined)

        expect(window.alert).toHaveBeenCalledWith(
          'Cannot remove Intersection 1 from selectedOrg because it must belong to at least one organization.'
        )
      } finally {
        window.alert = jsdomAlert
      }
    })
  })

  describe('intersectionDeleteMultiple', () => {
    it('dispatches editOrg when all intersections have multiple orgs', async () => {
      const intersectionData = {
        intersection_data: { organizations: ['org1', 'org2', 'org3'] },
        allowed_selections: {},
      }

      const dispatch = makeMockDispatch(intersectionData)
      const getState = makeGetState()

      const rows = [{ intersection_id: '1' }, { intersection_id: '2' }, { intersection_id: '3' }] as any
      const selectedOrg = 'selectedOrg'
      const selectedOrgEmail = 'name@email.com'
      const updateTableData = jest.fn()

      const action = intersectionDeleteMultiple({ rows, selectedOrg, selectedOrgEmail, updateTableData })
      const result = await action(dispatch, getState, undefined)

      expect(result.payload).toEqual({ success: true, message: 'Intersection(s) deleted successfully' })
    })

    it('alerts when some intersections belong to only one org', async () => {
      const validData = {
        intersection_data: { organizations: ['org1', 'org2'] },
        allowed_selections: {},
      }
      const invalidData = {
        intersection_data: { organizations: ['org1'] },
        allowed_selections: {},
      }

      // Need per-call unwrap behavior
      const dispatch = jest.fn()
      const getState = makeGetState()
      let unwrapCallCount = 0
      const unwrapValues = [validData, invalidData, invalidData]

      dispatch.mockImplementation((action: any) => {
        if (typeof action === 'function') {
          return action(dispatch, getState, undefined)
        }
        if (action?.type?.includes('initiate')) {
          const val = unwrapValues[unwrapCallCount++]
          return { unwrap: jest.fn().mockResolvedValue(val) }
        }
        return { payload: { success: true } }
      })

      const jsdomAlert = window.alert
      window.alert = jest.fn()

      try {
        const rows = [{ intersection_id: '1' }, { intersection_id: '2' }, { intersection_id: '3' }] as any
        const selectedOrg = 'selectedOrg'
        const selectedOrgEmail = 'name@email.com'
        const updateTableData = jest.fn()

        const action = intersectionDeleteMultiple({ rows, selectedOrg, selectedOrgEmail, updateTableData })
        await action(dispatch, getState, undefined)

        expect(window.alert).toHaveBeenCalledWith(
          'Cannot remove Intersection(s) 2, 3 from selectedOrg because they must belong to at least one organization.'
        )
      } finally {
        window.alert = jsdomAlert
      }
    })
  })

  describe('refresh', () => {
    it('calls updateTableData and dispatches invalidateTags', async () => {
      const dispatch = jest.fn()
      const getState = makeGetState()
      const selectedOrg = 'selectedOrg'
      const updateTableData = jest.fn()

      dispatch.mockImplementation((action: any) => {
        if (typeof action === 'function') {
          return action(dispatch, getState, undefined)
        }
        return action
      })

      const action = refresh({ selectedOrg, updateTableData })
      await action(dispatch, getState, undefined)

      expect(updateTableData).toHaveBeenCalledTimes(1)
      expect(updateTableData).toHaveBeenCalledWith(selectedOrg)
    })
  })
})

describe('selectors', () => {
  const initialState = {
    loading: 'loading',
    value: {},
  }
  const state = { adminOrganizationTabIntersection: initialState } as any

  it('selectors return the correct value', async () => {
    expect(selectLoading(state)).toEqual('loading')
  })
})

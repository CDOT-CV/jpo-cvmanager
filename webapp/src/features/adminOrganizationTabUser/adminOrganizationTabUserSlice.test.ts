import { vi } from 'vitest'

vi.mock('../../apis/api-helper')
import reducer from './adminOrganizationTabUserSlice'
import {
  // async thunks
  userDeleteSingle,
  userDeleteMultiple,
  userAddMultiple,
  userBulkEdit,
  refresh,

  // reducers
  setSelectedUserList,
  setSelectedUserRole,

  // selectors
  selectLoading,
  selectSelectedUserList,
} from './adminOrganizationTabUserSlice'
import { RootState } from '../../store'

// Mock the organizationApiSlice
const { mockInitiate, mockInvalidateTags } = vi.hoisted(() => ({ mockInitiate: vi.fn(), mockInvalidateTags: vi.fn() }))

vi.mock('../api/organizationApiSlice', () => ({
  organizationApiSlice: {
    endpoints: {
      getUserOrganizations: {
        initiate: mockInitiate,
      },
    },
    util: {
      invalidateTags: mockInvalidateTags,
    },
  },
}))

describe('admin organization tab User reducer', () => {
  it('should handle initial state', () => {
    expect(reducer(undefined, { type: 'unknown' })).toEqual({
      loading: false,
      value: {
        selectedUserList: [],
      },
    })
  })
})

describe('async thunks', () => {

  describe('userDeleteSingle', () => {
    it('returns and calls the api correctly', async () => {
      let dispatch = vi.fn()
      const getState = vi.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
          },
        },
        organizationApi: {},
      })
      const user = { email: 'test@gmail.com', role: 'role1' }
      const selectedOrg = 'selectedOrg'
      const selectedOrgEmail = 'name@email.com'
      const updateTableData = vi.fn()

      dispatch.mockResolvedValue({
        data: ['org1', 'org2'],
        payload: { success: true },
      })

      let action = userDeleteSingle({ user, selectedOrg, selectedOrgEmail, updateTableData })

      const jsdomAlert = window.alert
      try {
        window.alert = vi.fn()
        await action(dispatch, getState, undefined)
        expect(dispatch).toHaveBeenCalledTimes(2 + 4)
        expect(window.alert).not.toHaveBeenCalled()

        // Only 1 organization
        dispatch = vi.fn()

        action = userDeleteSingle({ user, selectedOrg, selectedOrgEmail, updateTableData })

        dispatch.mockResolvedValue({
          data: ['org1'],
          payload: { success: true },
        })
        window.alert = vi.fn()
        await action(dispatch, getState, undefined)
        expect(dispatch).toHaveBeenCalledTimes(2 + 3)
        expect(window.alert).toHaveBeenCalledWith(
          'Cannot remove User test@gmail.com from selectedOrg because they must belong to at least one organization.'
        )
      } catch (e) {
        window.alert = jsdomAlert
        throw e
      }
    })
  })
  describe('userDeleteMultiple', () => {
    it('returns and calls the api correctly when all users have multiple organizations', async () => {
      const dispatch = vi.fn()
      const getState = vi.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
          },
        },
        organizationApi: {},
      })
      const users = [
        { email: 'test@gmail.com', role: 'role1' },
        { email: 'test2@gmail.com', role: 'role2' },
        { email: 'test3@gmail.com', role: 'role3' },
      ]
      const selectedOrg = 'selectedOrg'
      const selectedOrgEmail = 'name@email.com'
      const updateTableData = vi.fn()

      dispatch.mockResolvedValueOnce(undefined) // First call
      dispatch.mockResolvedValueOnce({ data: ['org1', 'org2', 'org3'], unsubscribe: vi.fn() })
      dispatch.mockResolvedValueOnce({ data: ['org1', 'org2'], unsubscribe: vi.fn() })
      dispatch.mockResolvedValueOnce({ data: ['org1', 'org2'], unsubscribe: vi.fn() })
      dispatch.mockResolvedValueOnce({ payload: { success: true } })

      const action = userDeleteMultiple({ users, selectedOrg, selectedOrgEmail, updateTableData })

      const jsdomAlert = window.alert
      try {
        window.alert = vi.fn()
        const result = await action(dispatch, getState, undefined)

        expect(dispatch).toHaveBeenCalledTimes(2 + 6)
        expect(window.alert).not.toHaveBeenCalled()
        expect(result.payload).toEqual({ success: true, message: 'User(s) deleted successfully' })
      } finally {
        window.alert = jsdomAlert
      }
    })

    it('shows alert when some users have only one organization', async () => {
      const dispatch = vi.fn()
      const getState = vi.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
          },
        },
      })
      const users = [
        { email: 'test@gmail.com', role: 'role1' },
        { email: 'test2@gmail.com', role: 'role2' },
        { email: 'test3@gmail.com', role: 'role3' },
      ]
      const selectedOrg = 'selectedOrg'
      const selectedOrgEmail = 'name@email.com'
      const updateTableData = vi.fn()

      dispatch.mockResolvedValueOnce(undefined) // First call
      dispatch.mockResolvedValueOnce({ data: ['org1', 'org2'], unsubscribe: vi.fn() })
      dispatch.mockResolvedValueOnce({ data: ['org1'], unsubscribe: vi.fn() })
      dispatch.mockResolvedValueOnce({ data: ['org1'], unsubscribe: vi.fn() })

      const action = userDeleteMultiple({ users, selectedOrg, selectedOrgEmail, updateTableData })

      const jsdomAlert = window.alert
      try {
        window.alert = vi.fn()
        await action(dispatch, getState, undefined)

        expect(window.alert).toHaveBeenCalledWith(
          'Cannot remove User(s) test2@gmail.com, test3@gmail.com from selectedOrg because they must belong to at least one organization.'
        )
      } finally {
        window.alert = jsdomAlert
      }
    })

    it('handles mixed valid and invalid users', async () => {
      const dispatch = vi.fn()
      const getState = vi.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
          },
        },
      })
      const users = [
        { email: 'test@gmail.com', role: 'role1' },
        { email: 'test2@gmail.com', role: 'role2' },
      ]
      const selectedOrg = 'selectedOrg'
      const selectedOrgEmail = 'name@email.com'
      const updateTableData = vi.fn()

      dispatch.mockResolvedValueOnce(undefined) // First call
      dispatch.mockResolvedValueOnce({ data: ['org1', 'org2'], unsubscribe: vi.fn() })
      dispatch.mockResolvedValueOnce({ data: ['org1'], unsubscribe: vi.fn() })

      const action = userDeleteMultiple({ users, selectedOrg, selectedOrgEmail, updateTableData })

      const jsdomAlert = window.alert
      try {
        window.alert = vi.fn()
        await action(dispatch, getState, undefined)

        expect(window.alert).toHaveBeenCalledWith(
          'Cannot remove User(s) test2@gmail.com from selectedOrg because they must belong to at least one organization.'
        )
      } finally {
        window.alert = jsdomAlert
      }
    })
  })

  describe('userAddMultiple', () => {
    it('returns and calls the api correctly', async () => {
      const dispatch = vi.fn()
      const getState = vi.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
          },
        },
        organizationApi: {},
      })
      const userList = [
        { email: 'test@gmail.com', role: 'role1' },
        { email: 'test2@gmail.com', role: 'role2' },
      ]
      const selectedOrg = 'selectedOrg'
      const selectedOrgEmail = 'name@email.com'
      const updateTableData = vi.fn()

      dispatch.mockImplementation((action: any) => {
        if (typeof action === 'function') {
          return action(dispatch, getState, undefined)
        }
        return Promise.resolve({ payload: { success: true } })
      })

      const action = userAddMultiple({ userList, selectedOrg, selectedOrgEmail, updateTableData })

      await action(dispatch, getState, undefined)

      // Should dispatch editOrg and refresh
      expect(dispatch).toHaveBeenCalledTimes(2 + 7)
    })
  })

  describe('userBulkEdit', () => {
    it('returns and calls the api correctly', async () => {
      const dispatch = vi.fn()
      const getState = vi.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
          },
        },
      })
      const json = {
        obj1: { newData: { email: 'test@gmail.com', role: 'role1' } },
        obj2: { newData: { email: 'test2@gmail.com', role: 'role2' } },
      }
      const selectedOrg = 'selectedOrg'
      const selectedUser = 'selectedUser'
      const selectedOrgEmail = 'name@email.com'
      const updateTableData = vi.fn()

      const action = userBulkEdit({ json, selectedOrg, selectedUser, selectedOrgEmail, updateTableData })

      await action(dispatch, getState, undefined)
      expect(dispatch).toHaveBeenCalledTimes(2 + 2)
    })
  })

  describe('refresh', () => {
    it('returns and calls the api correctly', async () => {
      const dispatch = vi.fn()
      const getState = vi.fn().mockReturnValue({
        user: {
          value: {
            authLoginData: { token: 'token' },
          },
        },
      })
      const selectedOrg = 'selectedOrg'
      const updateTableData = vi.fn()

      const action = refresh({ selectedOrg, updateTableData })

      await action(dispatch, getState, undefined)
      expect(updateTableData).toHaveBeenCalledTimes(1)
      expect(updateTableData).toHaveBeenCalledWith(selectedOrg)
      expect(dispatch).toHaveBeenCalledTimes(2 + 1)
    })
  })
})

describe('reducers', () => {
  const initialState: RootState['adminOrganizationTabUser'] = {
    loading: null,
    value: {
      selectedUserList: null,
    },
  }

  it('setSelectedUserList reducer updates state correctly', async () => {
    const selectedUserList = [{ organizations: [{ organization: 'org', role: 'role' }], email: 'email' }] as any
    expect(reducer(initialState, setSelectedUserList(selectedUserList))).toEqual({
      ...initialState,
      value: {
        ...initialState.value,
        selectedUserList: [{ organizations: [{ name: 'org', role: 'role' }], email: 'email' }],
      },
    })
  })

  it('setSelectedUserRole reducer updates state correctly', async () => {
    const email = 'test@gmail.com'
    const role = 'role1'
    const selectedUserList = [{ email, role: 'role2' }] as any
    expect(
      reducer(
        { ...initialState, value: { ...initialState.value, selectedUserList } },
        setSelectedUserRole({ email, role })
      )
    ).toEqual({
      ...initialState,
      value: { ...initialState.value, selectedUserList: [{ email, role: 'role1' }] },
    })
  })
})

describe('selectors', () => {
  const initialState = {
    loading: 'loading',
    value: {
      selectedUserList: 'selectedUserList',
    },
  }
  const state = { adminOrganizationTabUser: initialState } as any

  it('selectors return the correct value', async () => {
    expect(selectLoading(state)).toEqual('loading')
    expect(selectSelectedUserList(state)).toEqual('selectedUserList')
  })
})

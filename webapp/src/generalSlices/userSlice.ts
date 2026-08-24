import { PayloadAction, createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import AuthApi from '../apis/auth-api'
import { UserManager, LocalStorageManager, SecureStorageManager } from '../managers'
import { RootState } from '../store'
import apiHelper from '../apis/api-helper'
import EnvironmentVars from '../EnvironmentVars'

const authDataLocalStorage = LocalStorageManager.getAuthData()
const authLoginData = UserManager.isLoginActive(authDataLocalStorage) ? authDataLocalStorage : null

export const keycloakLogin = createAsyncThunk('user/login', async (token: string, { rejectWithValue }) => {
  try {
    if (token) {
      const authLoginData = await AuthApi.logIn(token)
      if (authLoginData?.data?.organizations?.length == 0) {
        console.error('User does not belong to any organizations')
        return rejectWithValue(
          'Login Unsuccessful: User does not belong to any organizations. Contact support for assistance.'
        )
      }
      return authLoginData
    } else {
      console.error('Invalid token passed to user/login')
      return rejectWithValue('Login Unsuccessful: No KeyCloak Token Please Refresh')
    }
  } catch (exception_var) {
    console.error('Exception logging in user', exception_var)
    return rejectWithValue(`Login Unsuccessful: ${(exception_var as Error).message}`)
  }
})

export const addOrModifyOrgAssociationByOrgName = createAsyncThunk(
  'user/addOrModifyOrgAssociationByOrgName',
  async ({ name, role }: { name: string; role: UserRole }, { getState }) => {
    const currentState = getState() as RootState
    const token = selectToken(currentState)
    const authLoginData = selectAuthLoginData(currentState)

    if (!token || !authLoginData) {
      return null
    }
    const existingIndex = authLoginData.data.organizations.findIndex((org) => org.name === name)
    if (existingIndex > -1) {
      return { name, role, organization: authLoginData.data.organizations[existingIndex].organization }
    }

    // Organization is new/unknown - fetch the organization ID from the backend
    const data = await apiHelper._getDataWithCodes({
      url: EnvironmentVars.adminOrg,
      token,
      query_params: { org_name: name },
    })
    switch (data?.status) {
      case 200:
        // Organization exists
        const organization = (data.body as any)?.organization_id
        if (organization) {
          return { name, role, organization }
        } else {
          console.error(
            `Unable to retrieve organization ID for ${name}. Status: ${data?.status}, Message: ${data?.message}`
          )
          return null
        }
      default:
        // organization does not exist or error occurred
        console.error(
          `Organization ${name} does not exist or error occurred. Status: ${data?.status}, Message: ${data?.message}`
        )
        return null
    }
  }
)

export const userSlice = createSlice({
  name: 'user',
  initialState: {
    loading: true,
    value: {
      authLoginData: authLoginData,
      organization: authLoginData?.data?.organizations?.[0],
      loginFailure: false,
      loginMessage: '',
      routeNotFound: false,
    },
  },
  reducers: {
    logout: (state) => {
      state.value.authLoginData = null
      state.value.organization = null
      LocalStorageManager.removeAuthData()
      SecureStorageManager.removeUserRole()
    },
    changeOrganization: (state, action: PayloadAction<number>) => {
      const organization =
        UserManager.getOrganization(state.value.authLoginData, action.payload) ?? state.value.organization
      state.value.organization = organization
      if (organization) SecureStorageManager.setUserRole({ name: organization.name, role: organization.role })
    },
    changeOrganizationName: (state, action: PayloadAction<string>) => {
      const organization =
        UserManager.getOrganizationByName(state.value.authLoginData, action.payload) ?? state.value.organization
      state.value.organization = organization
      if (organization) SecureStorageManager.setUserRole({ name: organization.name, role: organization.role })
    },
    deleteOrgAssociationByOrgName: (state, action: PayloadAction<string>) => {
      if (!state.value.authLoginData) {
        return
      }
      const index = state.value.authLoginData.data.organizations.findIndex((org) => org.name === action.payload)
      if (index > -1) {
        const updatedOrgList = state.value.authLoginData.data.organizations
        updatedOrgList.splice(index, 1)
        state.value.authLoginData.data.organizations = [...updatedOrgList]
      }
    },
    setLoading: (state, action) => {
      state.loading = action.payload
    },
    setLoginFailure: (state, action) => {
      state.value.loginFailure = action.payload
    },
    setLoginMessage: (state, action) => {
      state.value.loginMessage = action.payload
    },
    setRouteNotFound: (state, action) => {
      state.value.routeNotFound = action.payload
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(keycloakLogin.pending, (state) => {
        state.value.loginMessage = ''
        state.loading = true
      })
      .addCase(keycloakLogin.fulfilled, (state, action) => {
        state.loading = false
        state.value.loginMessage = ''
        state.value.loginFailure = false
        state.value.authLoginData = action.payload
        state.value.organization = action.payload?.data?.organizations?.[0]
        LocalStorageManager.setAuthData(action.payload)
        SecureStorageManager.setUserRole(action.payload?.data?.organizations?.[0])
      })
      .addCase(keycloakLogin.rejected, (state, action: PayloadAction<unknown>) => {
        state.loading = false
        state.value.loginFailure = true
        state.value.loginMessage = action.payload as string
        LocalStorageManager.removeAuthData()
        SecureStorageManager.removeUserRole()
      })
      .addCase(addOrModifyOrgAssociationByOrgName.fulfilled, (state, action) => {
        if (action.payload) {
          const { name, role, organization } = action.payload
          if (!state.value.authLoginData) {
            return
          }
          const existingOrgIndex = state.value.authLoginData.data.organizations.findIndex((org) => org.name === name)
          if (existingOrgIndex > -1) {
            // Update the role of the existing organization association
            state.value.authLoginData.data.organizations[existingOrgIndex].role = role
          } else {
            // Add a new organization association
            state.value.authLoginData.data.organizations.push({ name, role, organization })
          }
        }
      })
  },
})

export const {
  logout,
  changeOrganization,
  changeOrganizationName,
  deleteOrgAssociationByOrgName,
  setLoading,
  setLoginFailure,
  setRouteNotFound,
} = userSlice.actions

export const selectAuthLoginData = (state: RootState) => state.user.value.authLoginData
export const selectToken = (state: RootState) => state.user.value.authLoginData?.token
export const selectRole = (state: RootState) => state.user.value.organization?.role
export const selectIsSuperUser = (state: RootState) => state.user.value.authLoginData?.data?.super_user
export const selectOrganizationName = (state: RootState) => state.user.value.organization?.name
export const selectOrganizationId = (state: RootState) => state.user.value.organization?.organization
export const selectName = (state: RootState) => state.user.value.authLoginData?.data?.name
export const selectEmail = (state: RootState) => state.user.value.authLoginData?.data?.email
export const selectSuperUser = (state: RootState) => state.user.value.authLoginData?.data?.super_user
export const selectTokenExpiration = (state: RootState) => state.user.value.authLoginData?.expires_at
export const selectLoginFailure = (state: RootState) => state.user.value.loginFailure
export const selectLoginMessage = (state: RootState) => state.user.value.loginMessage
export const selectRouteNotFound = (state: RootState) => state.user.value.routeNotFound
export const selectLoading = (state: RootState) => state.user.loading
export const selectLoadingGlobal = (state: RootState) => {
  let loading = false
  for (const [, value] of Object.entries(state)) {
    const valueObj = value as object
    if ('loading' in valueObj) {
      const valLoading = valueObj as { loading: boolean }
      if (valLoading.loading) {
        loading = true
        break
      }
    }
  }
  return loading
}

export const selectIsUserOrAbove = (state: RootState) => {
  if (selectIsSuperUser(state)) {
    return true
  }
  const role = selectRole(state)?.toUpperCase()
  return role === 'USER' || role === 'OPERATOR' || role === 'ADMIN'
}
export const selectIsOperatorOrAbove = (state: RootState) => {
  if (selectIsSuperUser(state)) {
    return true
  }
  const role = selectRole(state)?.toUpperCase()
  return role === 'OPERATOR' || role === 'ADMIN'
}
export const selectIsAdminOrAbove = (state: RootState) => {
  if (selectIsSuperUser(state)) {
    return true
  }
  const role = selectRole(state)?.toUpperCase()
  return role === 'ADMIN'
}

export default userSlice.reducer

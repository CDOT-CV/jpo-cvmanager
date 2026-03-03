import { UserManager, LocalStorageManager } from './managers'

test('UserManager correctly checks if login is active', () => {
  let authLoginData: AuthLoginData = undefined
  expect(UserManager.isLoginActive(authLoginData)).toBe(false)

  // get time 5 minutes ago
  authLoginData = { expires_at: Date.now() - 1000 * 60 * 5, data: undefined, token: undefined }
  expect(UserManager.isLoginActive(authLoginData)).toBe(false)

  // get time 5 minutes from now
  authLoginData = { expires_at: Date.now() + 1000 * 60 * 5, data: undefined, token: undefined }
  expect(UserManager.isLoginActive(authLoginData)).toBe(true)
})

// write a test for the UserManager.getOrganization function
test('UserManager correctly gets the organization', () => {
  const authLoginData: AuthLoginData = {
    data: {
      name: undefined,
      email: undefined,
      super_user: undefined,
      first_name: undefined,
      last_name: undefined,
      organizations: [
        {
          name: 'test1',
          role: 'role',
        },
        {
          name: 'test2',
          role: 'role',
        },
      ],
    },
    token: undefined,
    expires_at: undefined,
  }

  const organization = UserManager.getOrganization(authLoginData, 'test2')

  expect(organization).toEqual(authLoginData.data.organizations[1])
})

test('LocalStorageManager correctly sets and gets auth data', () => {
  const authData: AuthLoginData = { token: 'test', data: undefined, expires_at: undefined }
  LocalStorageManager.setAuthData(authData)
  expect(LocalStorageManager.getAuthData()).toEqual(authData)
})

test('LocalStorageManager correctly removes auth data', () => {
  const authData: AuthLoginData = { token: 'test', data: undefined, expires_at: undefined }
  LocalStorageManager.setAuthData(authData)
  LocalStorageManager.removeAuthData()
  expect(LocalStorageManager.getAuthData()).toBe(null)
})

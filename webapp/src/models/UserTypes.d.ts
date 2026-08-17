type AuthLoginData = {
  data: UserAuthResponse
  token: string
  expires_at: number
}

type UserRole = 'ADMIN' | 'OPERATOR' | 'USER'

type UserOrganization = {
  role: UserRole
  organization: number
}

type UserOrganizationWithName = UserOrganization & {
  name: string
}

type UserAuthResponse = {
  email: string
  first_name: string
  last_name: string
  name: string
  super_user: boolean
  organizations: UserOrganizationWithName[]
}

type AdminUser = {
  email: string
  first_name: string
  last_name: string
  super_user: boolean
  organizations: UserOrganization[]
}

type AdminUserForOrg = AdminUser & {
  role: UserRole
}

type AdminUserCreationBody = {
  email: string
  first_name: string
  last_name: string
  super_user: boolean
  organizations: UserOrganization[]
}

type AdminUserWithId = AdminUser & {
  id: number
}

type AdminUserWithRole = AdminUser & {
  role: UserRole
}

type AdminUserAllowedSelections = {
  roles: UserRole[]
  organizations: number[]
}

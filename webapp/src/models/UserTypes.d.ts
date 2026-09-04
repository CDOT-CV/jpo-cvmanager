type AuthLoginData = {
  data: UserAuthResponse
  token: string
  expires_at: number
}

type UserRole = 'ADMIN' | 'OPERATOR' | 'USER'

type UserOrganization = {
  role: UserRole
  organization: string
}

type UserAuthOrganization = {
  role: UserRole
  organization: number
  name: string
}

type UserAuthResponse = {
  email: string
  first_name: string
  last_name: string
  name: string
  super_user: boolean
  organizations: UserAuthOrganization[]
}

type AdminUser = {
  email: string
  first_name: string
  last_name: string
  super_user: boolean
  organizations: UserOrganization[]
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
  organizations: string[]
}
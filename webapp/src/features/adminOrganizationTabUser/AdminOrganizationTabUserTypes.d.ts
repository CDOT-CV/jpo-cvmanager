export type AdminOrgTabUser = {
  email: string
  role: string
}

export type AdminOrgTabUserAddMultiple = {
  userList: AdminOrgTabUser[]
  selectedOrg: string
  selectedOrgEmail: string
  updateTableData: (org: string) => void
}

export type AdminOrgUserDeleteMultiple = {
  users: AdminOrgTabUser[]
  selectedOrg: string
  selectedOrgEmail: string
  updateTableData: (org: string) => void
}

export type AdminOrgTabUserBulkEdit = {
  json: { [key: string]: { newData: AdminOrgTabUser } }
  selectedOrg: string
  selectedOrgEmail: string
  selectedUser: string
  updateTableData: (org: string) => void
}

import { AdminOrgIntersection } from '../adminOrganizationTab/adminOrganizationTabSlice'

export type AdminOrgIntersectionDeleteSingle = {
  intersection: AdminOrgIntersection
  selectedOrg: string
  selectedOrgEmail: string
  updateTableData: (org: string) => void
}

export type AdminOrgIntersectionDeleteMultiple = {
  rows: AdminOrgIntersection[]
  selectedOrg: string
  selectedOrgEmail: string
  updateTableData: (org: string) => void
}

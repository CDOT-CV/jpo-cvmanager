type OrganizationDto = {
  name: string
  email: string
}

type OrganizationPatch = {
  orig_name: string
  name?: string
  email?: string
  users_to_add?: { email: string; role: string }[]
  users_to_modify?: { email: string; role: string }[]
  users_to_remove?: string[]
  rsus_to_add?: string[]
  rsus_to_remove?: string[]
  intersections_to_add?: number[]
  intersections_to_remove?: number[]
  tim_deposit?: boolean
  snmp_monitoring?: boolean
}

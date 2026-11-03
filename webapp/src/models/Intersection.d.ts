export type AdminIntersection = {
  orig_intersection_id: string
  intersection_id: string
  ref_pt: {
    latitude: string
    longitude: string
  }
  bbox?: {
    latitude1: string
    longitude1: string
    latitude2: string
    longitude2: string
  }
  intersection_name?: string
  origin_ip?: string
  organizations: number[]
  rsus: string[]
}

export type AdminIntersectionCreationInfo = {
  organizations: string[]
  rsus: string[]
}

// No changes required currently - just an admin intersection object
export type AdminIntersectionCreationBody = AdminIntersection

export type AdminIntersectionPatch = {
  intersection_id?: string
  ref_pt?: {
    latitude: string
    longitude: string
  }
  bbox?: {
    latitude1: string
    longitude1: string
    latitude2: string
    longitude2: string
  }
  intersection_name?: string
  origin_ip?: string
  organizations_to_add?: number[]
  organizations_to_remove?: number[]
  rsus_to_add?: string[]
  rsus_to_remove?: string[]
}

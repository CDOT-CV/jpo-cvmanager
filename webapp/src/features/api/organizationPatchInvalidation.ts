import type { AnyAction, Dispatch, Middleware } from '@reduxjs/toolkit'
import {
  ADMIN_INTERSECTION_AVAILABLE_LIST_ID,
  ADMIN_INTERSECTION_LIST_ID,
  ADMIN_INTERSECTION_TAG,
  adminIntersectionApiSlice,
} from './adminIntersectionApiSlice'
import { organizationApiSlice } from './organizationApiSlice'
import { RSU_API_RSU_LIST_ID, RSU_API_RSU_TAG, rsuApiSlice } from './rsuApiSlice'
import { USER_API_USER_LIST_ID, USER_API_USER_TAG, userApiSlice } from './userApiSlice'

/** True when the optional list exists and has at least one entry. */
export const hasItems = (items?: readonly unknown[]): boolean => (items?.length ?? 0) > 0

/**
 * Builds the adminIntersectionApi cache tags to invalidate for a patch.
 * Returns an empty array when no intersections were added or removed.
 *
 * This handles the case where intersections are added to or removed from an organization.
 */
export const getIntersectionInvalidationTags = (patch: OrganizationPatch) => {
  if (!hasItems(patch.intersections_to_add) && !hasItems(patch.intersections_to_remove)) {
    return []
  }
  return [
    // List of intersections not in org
    { type: ADMIN_INTERSECTION_TAG, id: ADMIN_INTERSECTION_LIST_ID },
    { type: ADMIN_INTERSECTION_TAG, id: ADMIN_INTERSECTION_AVAILABLE_LIST_ID },
    // Intersection info (includes organizations list)
    ...(patch.intersections_to_add ?? []).map((id) => ({ type: ADMIN_INTERSECTION_TAG, id })),
    ...(patch.intersections_to_remove ?? []).map((id) => ({ type: ADMIN_INTERSECTION_TAG, id })),
  ]
}

/**
 * Builds the userApi cache tags to invalidate for a patch (the user list plus each added,
 * modified, or removed user's email). Returns an empty array when no users changed.
 *
 * This handles the case where users are added to, modified in, or removed from an organization.
 */
export const getUserInvalidationTags = (patch: OrganizationPatch) => {
  if (!hasItems(patch.users_to_add) && !hasItems(patch.users_to_modify) && !hasItems(patch.users_to_remove)) {
    return []
  }
  return [
    { type: USER_API_USER_TAG, id: USER_API_USER_LIST_ID },
    // User info (includes organizations and roles list)
    ...(patch.users_to_add ?? []).map((u) => ({ type: USER_API_USER_TAG, id: u.email })),
    ...(patch.users_to_modify ?? []).map((u) => ({ type: USER_API_USER_TAG, id: u.email })),
    ...(patch.users_to_remove ?? []).map((email) => ({ type: USER_API_USER_TAG, id: email })),
  ]
}

/**
 * Builds the rsuApi cache tags to invalidate for a patch (the RSU list plus each added or
 * Returns an empty array when no RSUs changed.
 *
 * This handles the case where RSUs are added to or removed from an organization.
 */
export const getRsuInvalidationTags = (patch: OrganizationPatch) => {
  if (!hasItems(patch.rsus_to_add) && !hasItems(patch.rsus_to_remove)) {
    return []
  }
  return [
    { type: RSU_API_RSU_TAG, id: RSU_API_RSU_LIST_ID },
    // Rsu info (includes organizations list)
    ...(patch.rsus_to_add ?? []).map((ip) => ({ type: RSU_API_RSU_TAG, id: ip })),
    ...(patch.rsus_to_remove ?? []).map((ip) => ({ type: RSU_API_RSU_TAG, id: ip })),
  ]
}

/**
 * Invalidates the intersection, user, and RSU API caches affected by a patched organization so
 * dropdowns and organization lists refresh. Only slices with changes in the patch are invalidated.
 */
export const invalidateCachesForOrganizationPatch = (dispatch: Dispatch<AnyAction>, patch: OrganizationPatch) => {
  const intersectionTags = getIntersectionInvalidationTags(patch)
  if (intersectionTags.length > 0) {
    dispatch(adminIntersectionApiSlice.util.invalidateTags(intersectionTags))
  }

  const userTags = getUserInvalidationTags(patch)
  if (userTags.length > 0) {
    dispatch(userApiSlice.util.invalidateTags(userTags))
  }

  const rsuTags = getRsuInvalidationTags(patch)
  if (rsuTags.length > 0) {
    dispatch(rsuApiSlice.util.invalidateTags(rsuTags))
  }
}

/**
 * Invalidates every cached intersection, user, and RSU entry after an organization is deleted.
 * The deleted organization's members are unknown to the client, so all entries of each tag type
 * (lists, available lists, and per-item organization info) are invalidated, not specific ids.
 */
export const invalidateCachesForOrganizationDelete = (dispatch: Dispatch<AnyAction>) => {
  dispatch(adminIntersectionApiSlice.util.invalidateTags([ADMIN_INTERSECTION_TAG]))
  dispatch(userApiSlice.util.invalidateTags([USER_API_USER_TAG]))
  dispatch(rsuApiSlice.util.invalidateTags([RSU_API_RSU_TAG]))
}

/**
 * Redux middleware that, after a fulfilled patchOrganization mutation, invalidates the related
 * API caches. Always forwards the action and returns the downstream result.
 */
export const organizationPatchInvalidationMiddleware: Middleware = (api) => (next) => (action) => {
  const result = next(action)
  if (organizationApiSlice.endpoints.patchOrganization.matchFulfilled(action as AnyAction)) {
    invalidateCachesForOrganizationPatch(api.dispatch, (action as any).meta.arg.originalArgs)
  }
  return result
}

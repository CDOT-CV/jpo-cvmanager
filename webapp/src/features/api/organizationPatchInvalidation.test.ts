import { describe, expect, it, vi } from 'vitest'
import { adminIntersectionApiSlice } from './adminIntersectionApiSlice'
import {
  getIntersectionInvalidationTags,
  getRsuInvalidationTags,
  getUserInvalidationTags,
  invalidateCachesForOrganizationPatch,
  organizationPatchInvalidationMiddleware,
} from './organizationPatchInvalidation'
import { rsuApiSlice } from './rsuApiSlice'
import { userApiSlice } from './userApiSlice'

const patch = (overrides: Partial<OrganizationPatch> = {}): OrganizationPatch => ({ id: 1, ...overrides })

describe('getIntersectionInvalidationTags', () => {
  it('returns no tags when nothing intersection related changed', () => {
    expect(getIntersectionInvalidationTags(patch())).toEqual([])
  })

  it('returns no tags for empty arrays', () => {
    expect(getIntersectionInvalidationTags(patch({ intersections_to_add: [], intersections_to_remove: [] }))).toEqual(
      []
    )
  })

  it('returns list tags plus each added intersection', () => {
    const tags = getIntersectionInvalidationTags(patch({ intersections_to_add: [10, 11] }))
    expect(tags).toHaveLength(4)
    expect(tags.slice(2)).toEqual([
      expect.objectContaining({ id: 10 }),
      expect.objectContaining({ id: 11 }),
    ])
  })

  it('returns list tags plus each removed intersection', () => {
    const tags = getIntersectionInvalidationTags(patch({ intersections_to_remove: [12] }))
    expect(tags).toHaveLength(3)
    expect(tags[2]).toEqual(expect.objectContaining({ id: 12 }))
  })

  it('includes both added and removed intersections', () => {
    const tags = getIntersectionInvalidationTags(patch({ intersections_to_add: [1], intersections_to_remove: [2] }))
    expect(tags.map((t) => t.id)).toEqual(expect.arrayContaining([1, 2]))
    expect(tags).toHaveLength(4)
  })
})

describe('getUserInvalidationTags', () => {
  it('returns no tags when nothing user related changed', () => {
    expect(getUserInvalidationTags(patch({ rsus_to_add: ['1.1.1.1'] }))).toEqual([])
  })

  it('returns no tags for empty arrays', () => {
    expect(
      getUserInvalidationTags(patch({ users_to_add: [], users_to_modify: [], users_to_remove: [] }))
    ).toEqual([])
  })

  it('tags added users by email', () => {
    const tags = getUserInvalidationTags(patch({ users_to_add: [{ email: 'a@x.com', role: 'USER' }] }))
    expect(tags.map((t) => t.id)).toEqual(expect.arrayContaining(['a@x.com']))
    expect(tags).toHaveLength(2)
  })

  it('tags modified users by email', () => {
    const tags = getUserInvalidationTags(patch({ users_to_modify: [{ email: 'm@x.com', role: 'ADMIN' }] }))
    expect(tags.map((t) => t.id)).toContain('m@x.com')
    expect(tags).toHaveLength(2)
  })

  it('tags removed users by email', () => {
    const tags = getUserInvalidationTags(patch({ users_to_remove: ['r@x.com'] }))
    expect(tags.map((t) => t.id)).toContain('r@x.com')
    expect(tags).toHaveLength(2)
  })

  it('combines added, modified, and removed users', () => {
    const tags = getUserInvalidationTags(
      patch({
        users_to_add: [{ email: 'a@x.com', role: 'USER' }],
        users_to_modify: [{ email: 'm@x.com', role: 'USER' }],
        users_to_remove: ['r@x.com'],
      })
    )
    expect(tags.map((t) => t.id)).toEqual(expect.arrayContaining(['a@x.com', 'm@x.com', 'r@x.com']))
    expect(tags).toHaveLength(4)
  })
})

describe('getRsuInvalidationTags', () => {
  it('returns no tags when nothing RSU related changed', () => {
    expect(getRsuInvalidationTags(patch({ users_to_remove: ['r@x.com'] }))).toEqual([])
  })

  it('returns no tags for empty arrays', () => {
    expect(getRsuInvalidationTags(patch({ rsus_to_add: [], rsus_to_remove: [] }))).toEqual([])
  })

  it('tags added RSUs by ip', () => {
    const tags = getRsuInvalidationTags(patch({ rsus_to_add: ['10.0.0.1'] }))
    expect(tags.map((t) => t.id)).toContain('10.0.0.1')
    expect(tags).toHaveLength(2)
  })

  it('tags removed RSUs by ip', () => {
    const tags = getRsuInvalidationTags(patch({ rsus_to_remove: ['10.0.0.2'] }))
    expect(tags.map((t) => t.id)).toContain('10.0.0.2')
    expect(tags).toHaveLength(2)
  })

  it('combines added and removed RSUs', () => {
    const tags = getRsuInvalidationTags(patch({ rsus_to_add: ['10.0.0.1'], rsus_to_remove: ['10.0.0.2'] }))
    expect(tags.map((t) => t.id)).toEqual(expect.arrayContaining(['10.0.0.1', '10.0.0.2']))
    expect(tags).toHaveLength(3)
  })
})

describe('invalidateCachesForOrganizationPatch', () => {
  it('dispatches nothing when the patch has no relevant changes', () => {
    const dispatch = vi.fn()
    invalidateCachesForOrganizationPatch(dispatch, patch({ name: 'New name' }))
    expect(dispatch).not.toHaveBeenCalled()
  })

  it('only invalidates the intersection api for intersection changes', () => {
    const dispatch = vi.fn()
    invalidateCachesForOrganizationPatch(dispatch, patch({ intersections_to_add: [1] }))
    expect(dispatch).toHaveBeenCalledTimes(1)
    expect(dispatch).toHaveBeenCalledWith(
      adminIntersectionApiSlice.util.invalidateTags(getIntersectionInvalidationTags(patch({ intersections_to_add: [1] })))
    )
  })

  it('only invalidates the user api for user changes', () => {
    const dispatch = vi.fn()
    const p = patch({ users_to_remove: ['r@x.com'] })
    invalidateCachesForOrganizationPatch(dispatch, p)
    expect(dispatch).toHaveBeenCalledTimes(1)
    expect(dispatch).toHaveBeenCalledWith(userApiSlice.util.invalidateTags(getUserInvalidationTags(p)))
  })

  it('only invalidates the rsu api for rsu changes', () => {
    const dispatch = vi.fn()
    const p = patch({ rsus_to_add: ['10.0.0.1'] })
    invalidateCachesForOrganizationPatch(dispatch, p)
    expect(dispatch).toHaveBeenCalledTimes(1)
    expect(dispatch).toHaveBeenCalledWith(rsuApiSlice.util.invalidateTags(getRsuInvalidationTags(p)))
  })

  it('invalidates all three apis when all change', () => {
    const dispatch = vi.fn()
    invalidateCachesForOrganizationPatch(
      dispatch,
      patch({ intersections_to_add: [1], users_to_modify: [{ email: 'a@x.com', role: 'USER' }], rsus_to_remove: ['x'] })
    )
    expect(dispatch).toHaveBeenCalledTimes(3)
  })
})

describe('organizationPatchInvalidationMiddleware', () => {
  const run = (action: any) => {
    const dispatch = vi.fn()
    const next = vi.fn().mockReturnValue('next-result')
    const result = organizationPatchInvalidationMiddleware({ dispatch, getState: vi.fn() } as any)(next)(action)
    return { dispatch, next, result }
  }

  const fulfilledPatch = (originalArgs: OrganizationPatch) => ({
    type: 'organizationApi/executeMutation/fulfilled',
    payload: {},
    meta: {
      requestId: 'req',
      requestStatus: 'fulfilled',
      arg: { type: 'mutation', endpointName: 'patchOrganization', originalArgs },
    },
  })

  it('always passes the action along and returns the result', () => {
    const { next, result } = run({ type: 'unrelated' })
    expect(next).toHaveBeenCalledWith({ type: 'unrelated' })
    expect(result).toBe('next-result')
  })

  it('does not dispatch for unrelated actions', () => {
    const { dispatch } = run({ type: 'unrelated' })
    expect(dispatch).not.toHaveBeenCalled()
  })

  it('dispatches invalidations after a fulfilled patchOrganization', () => {
    const { dispatch, result } = run(fulfilledPatch(patch({ rsus_to_add: ['10.0.0.1'] })))
    expect(result).toBe('next-result')
    expect(dispatch).toHaveBeenCalledTimes(1)
  })

  it('does not dispatch for a fulfilled patchOrganization with no relevant changes', () => {
    const { dispatch } = run(fulfilledPatch(patch({ name: 'x' })))
    expect(dispatch).not.toHaveBeenCalled()
  })

  it('does not dispatch for a pending patchOrganization', () => {
    const action = fulfilledPatch(patch({ rsus_to_add: ['10.0.0.1'] }))
    action.type = 'organizationApi/executeMutation/pending'
    action.meta.requestStatus = 'pending'
    const { dispatch } = run(action)
    expect(dispatch).not.toHaveBeenCalled()
  })
})

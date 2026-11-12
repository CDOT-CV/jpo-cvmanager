import { describe, expect, it } from 'vitest'
import reducer, { getIntersections } from './intersectionSlice'

const intersection = (intersectionID: number): IntersectionReferenceData => ({
  intersectionID,
  rsuIP: '127.0.0.1',
  latitude: 39,
  longitude: -105,
})

describe('intersectionSlice organization requests', () => {
  it('ignores an older response after a newer organization request starts', () => {
    const initial = reducer(undefined, { type: 'unknown' })
    const firstPending = reducer(initial, getIntersections.pending('first-request', 'first-org'))
    const secondPending = reducer(firstPending, getIntersections.pending('second-request', 'second-org'))

    const afterStaleResponse = reducer(
      secondPending,
      getIntersections.fulfilled([intersection(1)], 'first-request', 'first-org')
    )
    expect(afterStaleResponse.value.intersections).toEqual(initial.value.intersections)

    const afterCurrentResponse = reducer(
      afterStaleResponse,
      getIntersections.fulfilled([intersection(2)], 'second-request', 'second-org')
    )
    expect(afterCurrentResponse.value.intersections).toEqual([intersection(2)])
    expect(afterCurrentResponse.loading).toBe(false)
  })
})

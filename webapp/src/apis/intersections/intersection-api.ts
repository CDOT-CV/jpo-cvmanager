import { apiHelper } from '../api-helper'

class IntersectionApi {
  async getIntersections({ token }): Promise<IntersectionReferenceData[]> {
    var response = await apiHelper.invokeApi({
      path: '/intersections',
      token: token,
      failureMessage: 'Failed to retrieve intersection list',
      tag: 'intersection',
    })
    return response ?? []
  }
}

export default new IntersectionApi()

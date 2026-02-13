import { useCallback, useState } from 'react'
import { Query, QueryResult, OrderByCollection, Column } from '@material-table/core'


export interface PaginatedQueryParams {
  page?: number
  size?: number
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

export const usePaginatedQuery = (
  useQueryHook: any,
  additionalParams: Record<string, any> = {}
) => {
  const [queryParams, setQueryParams] = useState({
    page: 0,
    size: 25,
    ...additionalParams,
  })

  const { data, isLoading, isFetching, refetch } = useQueryHook(queryParams)

  const fetchData = useCallback(
    async (query: Query<any>): Promise<QueryResult<any>> => {
      // Extract orderBy from orderByCollection (which can have multiple fields)
      const orderByCollection: OrderByCollection[] = query.orderByCollection || []
      const primaryOrder: OrderByCollection | undefined = orderByCollection[0] // Get first ordering (most tables use single column sort)

      // Extract the field name from the Column object
      let orderByFieldName: string | undefined = undefined
      
      if (primaryOrder?.orderBy) {
        const column = primaryOrder.orderBy as Column<any>
        // The field property contains the actual column name (e.g., 'rsuIp', 'model', etc.)
        orderByFieldName = column.field as string
      }

      const params = {
        page: query.page,
        size: query.pageSize,
        orderBy: orderByFieldName, // This is now the field name like 'rsuIp', not an index
        orderDirection: primaryOrder?.orderDirection as 'asc' | 'desc' | undefined,
        ...additionalParams,
      }

      setQueryParams(params)

      // Wait for next tick to ensure RTK Query updates
      await new Promise((resolve) => setTimeout(resolve, 0))

      return {
        data: data?.data || [],
        page: query.page,
        totalCount: data?.totalCount || 0,
      }
    },
    [data, additionalParams]
  )

  const refresh = useCallback(() => {
    refetch()
  }, [refetch])

  return {
    fetchData,
    isLoading: isLoading || isFetching,
    data,
    refresh,
    refetch, // Expose both for flexibility
  }
}
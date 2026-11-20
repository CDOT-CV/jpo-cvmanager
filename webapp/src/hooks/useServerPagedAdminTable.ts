import { useCallback, useRef, useState } from 'react'
import toast from 'react-hot-toast'
import { buildAdminTableQueryParams } from '../components/AdminTable'
import { useAdminTableQuerySync } from './useAdminTableQuerySync'

type UseServerPagedAdminTableArgs = {
  organization: number | undefined
  columns: any[]
  defaultSortField: string
  entityName: string
  useQuery: (params: any, options: { skip: boolean }) => { data?: any }
  useLazyQuery: () => readonly [(params: any) => { unwrap: () => Promise<any> }, ...any[]]
  mapRows?: (rows: any[]) => any[]
}

/**
 * Shared server-side paging wiring for AdminTable: query params, lazy fetching,
 * and keeping the table in sync with the RTK Query cache.
 */
export const useServerPagedAdminTable = ({
  organization,
  columns,
  defaultSortField,
  entityName,
  useQuery,
  useLazyQuery,
  mapRows,
}: UseServerPagedAdminTableArgs) => {
  const tableRef = useRef<any>(null)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [currentParams, setCurrentParams] = useState({
    page: 0,
    size: 20,
    sort: `${defaultSortField},asc`,
    search: '',
    organization: organization || -1,
  })

  const [trigger] = useLazyQuery()
  const { data: subscribedData } = useQuery(currentParams, { skip: !organization })
  const { currentQueryRef, markTableRenderedData, handleRefresh } = useAdminTableQuerySync({
    organization,
    tableRef,
    isRefreshing,
    currentPage: currentParams.page,
    subscribedData,
  })

  const handleQueryChange = useCallback(
    async (query) => {
      setIsRefreshing(true)

      try {
        const params = buildAdminTableQueryParams(query, columns, organization, defaultSortField, 'asc')

        // Reset to the first page when the organization changes
        if (currentQueryRef.current && currentQueryRef.current.organization !== params.organization) {
          params.page = 0
          query.page = 0
        }

        currentQueryRef.current = params
        setCurrentParams(params)

        const result = await trigger(params).unwrap()

        markTableRenderedData(params, result)

        const content = result.content || []
        return {
          data: mapRows ? mapRows(content) : content,
          page: params.page,
          totalCount: result.totalElements || 0,
        }
      } catch (error) {
        console.error(`Failed to fetch ${entityName}:`, error)
        toast.error(`Failed to fetch ${entityName}`)
        return {
          data: [],
          page: query.page,
          totalCount: 0,
        }
      } finally {
        setIsRefreshing(false)
      }
    },
    [columns, trigger, organization, currentQueryRef, markTableRenderedData, defaultSortField, entityName, mapRows]
  )

  return { tableRef, isRefreshing, handleQueryChange, handleRefresh }
}

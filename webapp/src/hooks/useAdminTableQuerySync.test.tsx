import { act, render } from '@testing-library/react'
import { useEffect, type MutableRefObject } from 'react'
import { vi } from 'vitest'
import { useAdminTableQuerySync } from './useAdminTableQuerySync'

type SubscribedData = {
  content?: any[]
  totalElements?: number
}

type HarnessProps = {
  organization?: number
  isRefreshing: boolean
  currentPage: number
  subscribedData?: SubscribedData
  tableRef: MutableRefObject<any>
  onHookReady?: (hook: ReturnType<typeof useAdminTableQuerySync>) => void
}

const Harness = ({ organization, isRefreshing, currentPage, subscribedData, tableRef, onHookReady }: HarnessProps) => {
  const hook = useAdminTableQuerySync({
    organization,
    tableRef,
    isRefreshing,
    currentPage,
    subscribedData,
  })

  useEffect(() => {
    onHookReady?.(hook)
  }, [hook, onHookReady])

  return null
}

describe('useAdminTableQuerySync', () => {
  it('calls table onQueryChange when handleRefresh is invoked', () => {
    const onQueryChange = vi.fn()
    const tableRef = { current: { onQueryChange } }
    let hookApi: ReturnType<typeof useAdminTableQuerySync> | undefined

    render(
      <Harness isRefreshing={false} currentPage={0} tableRef={tableRef} onHookReady={(hook) => (hookApi = hook)} />
    )

    act(() => {
      hookApi?.handleRefresh()
    })

    expect(onQueryChange).toHaveBeenCalledTimes(1)
  })

  it('does not auto-refresh while isRefreshing is true', () => {
    const onQueryChange = vi.fn()
    const tableRef = { current: { onQueryChange } }
    const subscribedData = { content: [{ id: 1 }], totalElements: 1 }

    const { rerender } = render(
      <Harness
        organization={1}
        isRefreshing={true}
        currentPage={0}
        subscribedData={subscribedData}
        tableRef={tableRef}
      />
    )

    expect(onQueryChange).toHaveBeenCalledTimes(0)

    rerender(
      <Harness
        organization={1}
        isRefreshing={false}
        currentPage={0}
        subscribedData={subscribedData}
        tableRef={tableRef}
      />
    )

    expect(onQueryChange).toHaveBeenCalledTimes(1)
  })

  it('auto-refreshes only when subscribed signature changes', () => {
    const onQueryChange = vi.fn()
    const tableRef = { current: { onQueryChange } }
    const baseData = { content: [{ id: 1 }], totalElements: 1 }

    const { rerender } = render(
      <Harness organization={1} isRefreshing={false} currentPage={0} subscribedData={baseData} tableRef={tableRef} />
    )

    expect(onQueryChange).toHaveBeenCalledTimes(1)

    rerender(
      <Harness
        organization={1}
        isRefreshing={false}
        currentPage={0}
        subscribedData={{ content: [{ id: 1 }], totalElements: 1 }}
        tableRef={tableRef}
      />
    )

    expect(onQueryChange).toHaveBeenCalledTimes(1)

    rerender(
      <Harness
        organization={1}
        isRefreshing={false}
        currentPage={0}
        subscribedData={{ content: [{ id: 1 }, { id: 2 }], totalElements: 2 }}
        tableRef={tableRef}
      />
    )

    expect(onQueryChange).toHaveBeenCalledTimes(2)
  })

  it('does not auto-refresh when current table render was already marked', () => {
    const onQueryChange = vi.fn()
    const tableRef = { current: { onQueryChange } }
    const subscribedData = { content: [{ id: 1 }], totalElements: 1 }
    let hookApi: ReturnType<typeof useAdminTableQuerySync> | undefined

    const { rerender } = render(
      <Harness
        organization={1}
        isRefreshing={false}
        currentPage={0}
        tableRef={tableRef}
        onHookReady={(hook) => (hookApi = hook)}
      />
    )

    act(() => {
      hookApi?.markTableRenderedData(
        {
          page: 0,
          size: 20,
          sort: 'first_name,asc',
          search: '',
          organization: 1,
        },
        subscribedData
      )
    })

    rerender(
      <Harness
        organization={1}
        isRefreshing={false}
        currentPage={0}
        subscribedData={subscribedData}
        tableRef={tableRef}
        onHookReady={(hook) => (hookApi = hook)}
      />
    )

    expect(onQueryChange).toHaveBeenCalledTimes(0)
  })
})

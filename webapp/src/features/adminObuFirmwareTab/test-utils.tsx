import React from 'react'

// A minimal stand-in for AdminTable so tab tests can focus on queries and actions.
// AdminTable owns Material Table rendering and debounce behavior, which are tested
// separately. Column header clicks sort by column index, as Material Table does.
export const MockAdminTable = ({
  actions,
  columns,
  defaultPageSize = 25,
  handleQueryChange,
  tableRef,
  rowKey = 'object_id',
}: any) => {
  const [rows, setRows] = React.useState<any[]>([])
  const [totalCount, setTotalCount] = React.useState(0)
  const queryRef = React.useRef<any>({ page: 0, pageSize: defaultPageSize, search: '' })

  const runQuery = React.useCallback(
    async (changes: Record<string, unknown> = {}) => {
      const query = { ...queryRef.current, ...changes }
      queryRef.current = query
      const result = await handleQueryChange(query)
      setRows(result.data)
      setTotalCount(result.totalCount)
    },
    [handleQueryChange]
  )

  // Material Table catches a failed data load and shows it in the table, so the
  // stand-in does the same rather than leaving the rejection unhandled
  const loadRows = React.useCallback(
    (changes: Record<string, unknown> = {}) =>
      runQuery(changes).catch(() => {
        /* The handler under test reports the failure. */
      }),
    [runQuery]
  )

  React.useEffect(() => {
    tableRef.current = { onQueryChange: runQuery }
    void loadRows()
  }, [runQuery, loadRows, tableRef])

  const rowActions = actions.filter((action: any) => typeof action === 'function')
  const toolbarActions = actions.filter((action: any) => typeof action !== 'function' && action.position === 'toolbar')

  return (
    <div>
      <input
        placeholder="Search"
        value={queryRef.current.search}
        onChange={(event) => void loadRows({ page: 0, search: event.target.value })}
      />
      {toolbarActions.map((action: any, index: number) =>
        action.iconProps?.itemType === 'custom' ? (
          <React.Fragment key={index}>{action.iconProps.render()}</React.Fragment>
        ) : (
          <button key={index} onClick={action.onClick} disabled={action.disabled}>
            {action.iconProps?.title}
          </button>
        )
      )}
      <table>
        <thead>
          <tr>
            {columns.map((column: any, index: number) => (
              <th
                key={column.title}
                onClick={() => {
                  const current = queryRef.current.orderByCollection?.[0]
                  const orderDirection =
                    current?.orderBy === index && current.orderDirection === 'asc' ? 'desc' : 'asc'
                  void loadRows({ page: 0, orderByCollection: [{ orderBy: index, orderDirection }] })
                }}
              >
                {column.title}
              </th>
            ))}
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row: any, index: number) => (
            <tr key={row[rowKey] ?? index}>
              {columns.map((column: any) => (
                <td key={column.title}>{column.render ? column.render(row) : row[column.field]}</td>
              ))}
              <td>
                {rowActions.map((action: any, index: number) => {
                  const rowAction = action(row)
                  return (
                    <button
                      key={index}
                      aria-label={rowAction.tooltip}
                      disabled={rowAction.disabled}
                      onClick={(event) => rowAction.onClick(event, row)}
                    >
                      {rowAction.tooltip}
                    </button>
                  )
                })}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <button
        aria-label="Next Page"
        disabled={(queryRef.current.page + 1) * queryRef.current.pageSize >= totalCount}
        onClick={() => void loadRows({ page: queryRef.current.page + 1 })}
      >
        Next
      </button>
    </div>
  )
}

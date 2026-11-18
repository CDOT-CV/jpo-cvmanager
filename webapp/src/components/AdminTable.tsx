import MaterialTable, {
  Action,
  Column,
  MTableAction,
  MTableCell,
  MTableToolbar,
  Query,
  QueryResult,
} from '@material-table/core'
import { makeStyles } from '@mui/styles'
import React from 'react'

import { alpha, Box, Button, Tooltip, Typography, useTheme } from '@mui/material'
import '../features/adminRsuTab/Admin.css'
import AddCircleOutline from '@mui/icons-material/AddCircleOutline'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import ModeEditOutline from '@mui/icons-material/ModeEditOutline'
import Refresh from '@mui/icons-material/Refresh'

interface AdminTableProps {
  actions: (Action<any> | ((row: any) => Action<any>))[]
  columns: Column<any>[]
  data?: any[] // Optional for client-side pagination
  defaultPageSize?: number
  title: string
  editable?: any
  selection?: boolean
  tableLayout?: 'auto' | 'fixed'
  pageSizeOptions?: any
  search?: boolean
  thirdSortClick?: boolean
  // Server-side pagination props
  handleQueryChange?: (query: Query<any>) => Promise<QueryResult<any>>
  isLoading?: boolean
  tableRef?: React.MutableRefObject<any>
  selectionKeyField?: string
  selectedRows?: any[]
  onSelectionChange?: (selectedRows: any[]) => void
}

const useStyles = makeStyles({
  toolbarWrapper: {
    '& .MuiToolbar-gutters': {
      paddingLeft: '0px',
      paddingRight: '16px',
      display: 'flex',
      justifyContent: 'space-between',
    },
    '& .MuiTextField-root': {
      paddingLeft: 16,
      width: '260px',
    },
    '& .MuiBox-root:empty': {
      display: 'none',
    },
    '& .MuiTypography-h6': {
      paddingLeft: '16px',
    },
  },
})

const getActionIcon = (title: string) => {
  if (title.startsWith('Delete')) {
    return <DeleteOutline color="primary" />
  }

  switch (title) {
    case 'New':
      return <AddCircleOutline />
    case 'Add RSU':
      return <AddCircleOutline />
    case 'Add User':
      return <AddCircleOutline />
    case 'Add Intersection':
      return <AddCircleOutline />
    case 'Refresh':
      return <Refresh />
    case 'Edit':
      return <ModeEditOutline color="primary" />
    default:
      return null
  }
}

export const buildAdminTableQueryParams = (
  query,
  columns,
  organization: number | undefined,
  orderByDefault: string,
  orderDirectionDefault: string = 'asc'
) => {
  // Extract order information from orderByCollection
  let orderBy = orderByDefault
  let orderDirection = orderDirectionDefault
  if (query.orderByCollection && query.orderByCollection.length > 0) {
    const firstOrder = query.orderByCollection[0]
    if (firstOrder.orderBy !== undefined) {
      if (typeof firstOrder.orderBy.field === 'string') {
        orderBy = firstOrder.orderBy.field
      } else if (typeof firstOrder.orderBy === 'number') {
        orderBy = columns[firstOrder.orderBy].field
      }
    }
    orderDirection = firstOrder.orderDirection || 'asc'
  }

  // Build query params including organization
  const params = {
    page: query.page,
    size: query.pageSize,
    sort: `${orderBy},${orderDirection}`,
    search: query.search || '',
    organization: organization || -1, // Add organization parameter
  }
  return params
}

const AdminTable = (props: AdminTableProps) => {
  const theme = useTheme()
  const classes = useStyles()
  const currentPageRows = React.useRef<any[]>([])
  const selectedRows = React.useRef(props.selectedRows ?? [])
  React.useEffect(() => {
    selectedRows.current = props.selectedRows ?? []
  }, [props.selectedRows])

  // Function to check if a row is missing organizations
  const isMissingOrganizations = (rowData: any) => {
    try {
      return Array.isArray(rowData?.organizations) && rowData?.organizations?.length === 0
    } catch (e) {
      console.error('Error checking if row is missing organizations:', e)
      return false
    }
  }

  // Determine if server-side pagination is enabled
  const isServerSidePagination = props.handleQueryChange !== undefined
  const handleServerQueryChange = async (query: Query<any>) => {
    const result = await props.handleQueryChange!(query)
    if (!props.selectionKeyField) {
      return result
    }

    currentPageRows.current = result.data
    const selectedKeys = new Set(selectedRows.current.map((row) => row[props.selectionKeyField!]))
    return {
      ...result,
      data: result.data.map((row) => ({
        ...row,
        tableData: {
          ...row.tableData,
          checked: selectedKeys.has(row[props.selectionKeyField!]),
        },
      })),
    }
  }

  const handleSelectionChange = (pageSelection: any[]) => {
    if (!props.selectionKeyField) {
      props.onSelectionChange?.(pageSelection)
      return
    }

    const keyField = props.selectionKeyField
    const currentPageKeys = new Set(currentPageRows.current.map((row) => row[keyField]))
    const selectionByKey = new Map(
      selectedRows.current
        .filter((row) => !currentPageKeys.has(row[keyField]))
        .map((row) => [row[keyField], row])
    )
    pageSelection.forEach((row) => selectionByKey.set(row[keyField], row))
    const mergedSelection = Array.from(selectionByKey.values())
    selectedRows.current = mergedSelection
    props.onSelectionChange?.(mergedSelection)
  }

  return (
    <Box
      sx={{
        '& .MuiTableSortLabel-root': { textTransform: 'capitalize' },
        '& .MuiTableCell-footer': {
          borderBottom: 'none',
        },
        '& .MuiPaper-root': {
          boxShadow: 'none',
        },
        '& .MuiTableRow-root:empty': {
          display: 'none',
        },
      }}
      className="admin-table"
    >
      <MaterialTable
        actions={props.actions}
        columns={props.columns?.map((column) => ({
          ...column,
        }))}
        // Use data function for server-side, data array for client-side
        data={isServerSidePagination ? handleServerQueryChange : (props.data ?? [])}
        title={props.title}
        editable={props.editable}
        isLoading={props.isLoading}
        tableRef={props.tableRef}
        onSelectionChange={props.onSelectionChange ? handleSelectionChange : undefined}
        options={{
          selection: props.selection === undefined ? true : props.selection,
          ...(props.selectionKeyField ? { idSynonym: props.selectionKeyField } : {}),
          searchFieldAlignment: 'left',
          actionsColumnIndex: -1,
          tableLayout: props.tableLayout === undefined ? 'fixed' : props.tableLayout,
          rowStyle: (rowData) => ({
            overflowWrap: 'break-word',
            border: `1px solid ${alpha(theme.palette.divider, 0.1)}`,
            backgroundColor: isMissingOrganizations(rowData) ? theme.palette.custom.tableErrorBackground : 'inherit',
          }),
          headerStyle: {
            backgroundColor: theme.palette.background.paper,
          },
          emptyRowsWhenPaging: false, // Prevents tall empty rows
          pageSize: props.defaultPageSize ?? 25,
          pageSizeOptions: props.pageSizeOptions === undefined ? [5, 25, 50, 100] : props.pageSizeOptions,
          paging: true,
          search: props.search ?? true,
          thirdSortClick: props.thirdSortClick ?? true,
          debounceInterval: 500,
        }}
        components={{
          Cell: (cellProps) => {
            const rowData = cellProps.data
            return (
              <Tooltip title={isMissingOrganizations(rowData) ? 'Missing organizations' : ''}>
                <MTableCell
                  sx={{
                    padding: '6px 16px',
                    textTransform: 'none !important',
                    '&.MuiTableCell-body': {
                      color: theme.palette.text.secondary + ' !important',
                    },
                    '& .MuiButtonBase-root': {
                      borderRadius: '4px !important',
                    },
                  }}
                  {...cellProps}
                />
              </Tooltip>
            )
          },
          Action: (actionProps: any) => {
            const { action } = actionProps
            const iconProps = action?.iconProps

            // Allow admin pages to place controls such as filters alongside the
            // standard table actions without duplicating the shared toolbar
            if (iconProps?.itemType === 'custom' && typeof iconProps?.render === 'function') {
              return iconProps.render()
            }

            if (iconProps?.itemType === 'displayIcon') {
              return (
                <Box>
                  <action.icon />
                </Box>
              )
            }

            if (iconProps?.title !== null && iconProps?.title !== undefined) {
              return (
                <Button
                  variant={iconProps?.itemType}
                  color={iconProps?.color}
                  size="small"
                  disabled={action?.disabled}
                  startIcon={getActionIcon(iconProps?.title)}
                  sx={{ padding: '4px 8px', margin: '0px 4px' }}
                  onClick={action?.onClick}
                >
                  <Typography className="capital-case museo-slab" fontSize="12px">
                    {iconProps?.title}
                  </Typography>
                </Button>
              )
            }

            return (
              <Box
                sx={{
                  '&:hover': {
                    backgroundColor: alpha(theme.palette.custom.rowActionIcon, 0.1),
                    borderRadius: '4px',
                  },
                  '& .MuiButtonBase-root': {
                    borderRadius: '4px',
                    color: theme.palette.custom.rowActionIcon,
                  },
                }}
              >
                <MTableAction {...actionProps} />
              </Box>
            )
          },
          Toolbar: (toolbarProps) => {
            const originalData = props.selectionKeyField
              ? props.selectedRows?.map((row) => ({
                  ...row,
                  tableData: { ...row.tableData, checked: true },
                })) ?? toolbarProps.originalData
              : toolbarProps.originalData

            return (
              <div className={classes.toolbarWrapper}>
                <MTableToolbar {...toolbarProps} originalData={originalData} />
              </div>
            )
          },
        }}
      />
    </Box>
  )
}
export default AdminTable

import React from 'react'
import MaterialTable, { Action, Column } from '@material-table/core'
import { ThemeProvider, StyledEngineProvider } from '@mui/material/styles'
import { tableTheme } from '../styles'

import '../features/adminRsuTab/Admin.css'

interface AdminTableProps {
  actions: Action<any>[]
  columns: Column<any>[]
  data: any[]
  title: string
  editable?: any
}

const AdminTable = (props: AdminTableProps) => {
  return (
    <div>
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={tableTheme}>
          <MaterialTable
            actions={props.actions}
            columns={props.columns}
            data={props.data}
            title={props.title}
            editable={props.editable}
            options={{
              selection: true,
              actionsColumnIndex: -1,
              tableLayout: 'fixed',
              rowStyle: {
                overflowWrap: 'break-word',
              },
            }}
          />
        </ThemeProvider>
      </StyledEngineProvider>
    </div>
  )
}
export default AdminTable

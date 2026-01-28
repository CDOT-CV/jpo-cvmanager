import React, { useState, useEffect } from 'react'
import AdminTable from '../../components/AdminTable'
import { Button, Typography, useTheme } from '@mui/material'
import Accordion from '@mui/material/Accordion'
import AccordionSummary from '@mui/material/AccordionSummary'
import AccordionDetails from '@mui/material/AccordionDetails'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { confirmAlert } from 'react-confirm-alert'
import { Options } from '../../components/AdminDeletionOptions'
import {
  selectAvailableRsuList,
  selectSelectedRsuList,

  // actions
  setSelectedRsuList,
  getRsuData,
  rsuDeleteSingle,
  rsuDeleteMultiple,
  rsuAddMultiple,
} from './adminOrganizationTabRsuSlice'
import {
  updateOrgTimDeposit,
  selectTimDeposit,
} from '../adminOrganizationTab/adminOrganizationTabSlice'
import { selectLoadingGlobal } from '../../generalSlices/userSlice'
import { useSelector, useDispatch } from 'react-redux'

import '../adminRsuTab/Admin.css'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../store'
import { Action, Column } from '@material-table/core'
import { AdminOrgRsu } from '../adminOrganizationTab/adminOrganizationTabSlice'
import toast from 'react-hot-toast'
import { AddCircleOutline, DeleteOutline } from '@mui/icons-material'
import { Multiselect } from 'react-widgets/cjs'
import '../css/multiselect.css'

interface AdminOrganizationTabRsuProps {
  selectedOrg: string
  selectedOrgEmail: string
  tableData: AdminOrgRsu[]
  updateTableData: (orgname: string) => void
}

const AdminOrganizationTabRsu = (props: AdminOrganizationTabRsuProps) => {
  const { selectedOrg, selectedOrgEmail, updateTableData } = props
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const theme = useTheme()

  const availableRsuList = useSelector(selectAvailableRsuList)
  const selectedRsuList = useSelector(selectSelectedRsuList)
  const loadingGlobal = useSelector(selectLoadingGlobal)
  const timDeposit = useSelector(selectTimDeposit)
  const [rsuColumns] = useState<Column<any>[]>([
    { title: 'IP Address', field: 'ip', id: 0, width: '31%' },
    { title: 'Primary Route', field: 'primary_route', id: 1, width: '31%' },
    { title: 'Milepost', field: 'milepost', id: 2, width: '31%' },
  ])

  const rsuActions: Action<AdminOrgRsu>[] = [
    {
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      iconProps: {
        itemType: 'rowAction',
      },
      position: 'row',
      onClick: (event, rowData: AdminOrgRsu) => {
        const buttons = [
          { label: 'Yes', onClick: () => rsuOnDelete(rowData) },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options(
          'Delete RSU',
          'Are you sure you want to delete "' + rowData.ip + '" from ' + selectedOrg + ' organization?',
          buttons
        )
        confirmAlert(alertOptions)
      },
    },
    {
      tooltip: 'Remove All Selected From Organization',
      icon: 'delete',
      iconProps: {
        itemType: 'rowAction',
      },
      position: 'toolbarOnSelect',
      onClick: (event, rowData: AdminOrgRsu[]) => {
        const buttons = [
          { label: 'Yes', onClick: () => rsuMultiDelete(rowData) },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options(
          'Delete Selected RSUs',
          'Are you sure you want to delete ' + rowData.length + ' RSUs from ' + selectedOrg + ' organization?',
          buttons
        )
        confirmAlert(alertOptions)
      },
    },
    {
      position: 'toolbar',
      iconProps: {
        itemType: 'displayIcon',
      },
      icon: () => (
        <Multiselect
          dataKey="id"
          textField="ip"
          placeholder="Click to add RSUs"
          data={availableRsuList}
          value={selectedRsuList}
          onChange={(value) => {
            dispatch(setSelectedRsuList(value))
          }}
          style={{
            fontSize: '1rem',
          }}
        />
      ),
      onClick: () => {},
    },
    {
      position: 'toolbar',
      iconProps: {
        title: 'Add RSU',
        color: 'primary',
        itemType: 'contained',
      },
      icon: () => <AddCircleOutline />,
      onClick: () => rsuMultiAdd(selectedRsuList),
    },
  ]

  useEffect(() => {
    dispatch(setSelectedRsuList([]))
    dispatch(getRsuData(selectedOrg))
  }, [selectedOrg, dispatch])

  const rsuOnDelete = async (rsu: AdminOrgRsu) => {
    dispatch(rsuDeleteSingle({ rsu, selectedOrg, selectedOrgEmail, updateTableData })).then((data) => {
      if (!(data.payload as any).success) {
        toast.error((data.payload as any).message)
      } else {
        toast.success((data.payload as any).message)
      }
    })
  }

  const rsuMultiDelete = async (rows: AdminOrgRsu[]) => {
    dispatch(rsuDeleteMultiple({ rows, selectedOrg, selectedOrgEmail, updateTableData })).then((data) => {
      if (!(data.payload as any).success) {
        toast.error((data.payload as any).message)
      } else {
        toast.success((data.payload as any).message)
      }
    })
  }

  const rsuMultiAdd = async (rsuList: AdminOrgRsu[]) => {
    if (rsuList.length === 0) {
      toast.error('Please select RSUs to add')
      return
    }
    dispatch(rsuAddMultiple({ rsuList, selectedOrg, selectedOrgEmail, updateTableData })).then((data) => {
      if (!(data.payload as any).success) {
        toast.error((data.payload as any).message)
      } else {
        toast.success((data.payload as any).message)
      }
    })
  }

  const onTimDepositChange = (newValue: boolean) => {
    const actionLabel = newValue ? 'Enable' : 'Disable'
    const buttons = [
      {
        label: 'Yes',
        onClick: () => {
          dispatch(updateOrgTimDeposit({ orgName: selectedOrg, email: selectedOrgEmail, timDeposit: newValue })).then(
            (data: any) => {
              if (data.payload.success) {
                toast.success(data.payload.message)
              } else {
                toast.error(data.payload.message)
              }
            }
          )
        },
      },
      { label: 'No', onClick: () => {} },
    ]
    const alertOptions = Options(
      `${actionLabel} TIM Deposit`,
      'Are you sure this will change all RSU values under this organization and overwrite any previous settings?',
      buttons
    )
    confirmAlert(alertOptions)
  }

  return (
    <div className="accordion">
      <Accordion className="accordion-content" elevation={0}>
        <AccordionSummary
          expandIcon={<ExpandMoreIcon />}
          aria-controls="panel1a-content"
          id="panel1a-header"
          sx={{ display: 'flex', alignItems: 'center' }}
        >
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            RSUs
          </Typography>
          <div style={{ display: 'flex', gap: '8px' }}>
            <Button
              variant="contained"
              color="primary"
              size="small"
              onClick={(e) => {
                e.stopPropagation()
                onTimDepositChange(true)
              }}
            >
              Enable TIM Deposit for all RSUs
            </Button>
            <Button
              variant="contained"
              color="error"
              size="small"
              onClick={(e) => {
                e.stopPropagation()
                onTimDepositChange(false)
              }}
            >
              Disable TIM Deposit for all RSUs
            </Button>
          </div>
        </AccordionSummary>
        <AccordionDetails sx={{ padding: '8px 0px' }}>
          {loadingGlobal === false && [
            <div key="adminTable">
              <AdminTable title={''} data={props.tableData} columns={rsuColumns} actions={rsuActions} />
            </div>,
          ]}
        </AccordionDetails>
      </Accordion>
    </div>
  )
}

export default AdminOrganizationTabRsu

import { useState, useEffect } from 'react'
import AdminTable from '../../components/AdminTable'
import { Button, Typography, useTheme } from '@mui/material'
import Accordion from '@mui/material/Accordion'
import AccordionSummary from '@mui/material/AccordionSummary'
import AccordionDetails from '@mui/material/AccordionDetails'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { confirmAlert } from 'react-confirm-alert'
import { Options } from '../../components/AdminDeletionOptions'
import {
  selectSelectedRsuList,

  // actions
  setSelectedRsuList,
  rsuDeleteSingle,
  rsuDeleteMultiple,
  rsuAddMultiple,
} from './adminOrganizationTabRsuSlice'
import {
  getOrgData,
  updateOrgTimDeposit,
  updateOrgSnmpMonitoring,
  AdminOrgSingle,
  selectTimDeposit,
  selectSnmpMonitoring,
  selectSelectedOrgName,
} from '../adminOrganizationTab/adminOrganizationTabSlice'
import { selectLoadingGlobal } from '../../generalSlices/userSlice'
import { useSelector, useDispatch } from 'react-redux'

import '../adminRsuTab/Admin.css'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../store'
import { Action, Column } from '@material-table/core'
import { AdminOrgRsu } from '../adminOrganizationTab/adminOrganizationTabSlice'
import toast from 'react-hot-toast'
import AddCircleOutline from '@mui/icons-material/AddCircleOutline'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import { Multiselect } from 'react-widgets/cjs'
import '../css/multiselect.css'
import { useGetAllRsusNotInOrganizationQuery } from '../api/organizationApiSlice'

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
  const organizationName = useSelector(selectSelectedOrgName)

  const { data: availableRsuList } = useGetAllRsusNotInOrganizationQuery(organizationName, {
    skip: !organizationName, // Skip if no organization selected
  })

  const selectedRsuList = useSelector(selectSelectedRsuList)
  const loadingGlobal = useSelector(selectLoadingGlobal)
  const timDepositStatus = useSelector(selectTimDeposit)
  const snmpMonitoringStatus = useSelector(selectSnmpMonitoring)
  const [rsuColumns] = useState<Column<any>[]>([
    { title: 'IP Address', field: 'ip', id: 0, width: '18%' },
    { title: 'Primary Route', field: 'primary_route', id: 1, width: '18%' },
    { title: 'Milepost', field: 'milepost', id: 2, width: '18%' },
    {
      title: 'TIM Deposit',
      field: 'tim_deposit',
      id: 3,
      width: '18%',
      render: (rowData) => (
        <Typography
          variant="body2"
          sx={{
            color: rowData.tim_deposit ? theme.palette.success.light : theme.palette.error.light,
            fontWeight: 'bold',
          }}
        >
          {rowData.tim_deposit ? 'Enabled' : 'Disabled'}
        </Typography>
      ),
    },
    {
      title: 'SNMP Monitoring',
      field: 'snmp_monitoring',
      id: 4,
      width: '18%',
      render: (rowData) => (
        <Typography
          variant="body2"
          sx={{
            color: rowData.snmp_monitoring ? theme.palette.success.light : theme.palette.error.light,
            fontWeight: 'bold',
          }}
        >
          {rowData.snmp_monitoring ? 'Enabled' : 'Disabled'}
        </Typography>
      ),
    },
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

  // The org-level endpoints only update RSUs owned by the organization (enforced by the API)
  const confirmOrgRsuOptionChange = async (title: string, updateOwnedRsus: () => Promise<any>) => {
    const result: any = await dispatch(getOrgData({ orgName: selectedOrg }))
    const orgRsus: AdminOrgRsu[] | undefined = result.payload?.success
      ? (result.payload.data?.org_data as AdminOrgSingle | undefined)?.org_rsus
      : undefined
    if (!orgRsus) {
      toast.error('Failed to verify RSU ownership; no RSUs were updated')
      return
    }

    const ownedRsus = orgRsus.filter((rsu) => rsu.owner_organization === selectedOrg)
    const nonOwnedRsuCount = orgRsus.length - ownedRsus.length

    if (ownedRsus.length === 0) {
      confirmAlert(
        Options(
          title,
          nonOwnedRsuCount === 0
            ? 'This organization has no RSUs to update.'
            : `None of the RSUs in this organization are owned by ${selectedOrg}, so no RSUs will be updated. Only the owner organization can change these settings.`,
          [{ label: 'OK', onClick: () => {} }]
        )
      )
      return
    }

    const message =
      nonOwnedRsuCount === 0
        ? `Are you sure? This will change the value on all ${ownedRsus.length} RSU(s) in this organization and overwrite any previous settings.`
        : `Only the ${ownedRsus.length} RSU(s) owned by ${selectedOrg} will be updated. ` +
          `${nonOwnedRsuCount} RSU(s) owned by other organizations will not be changed. ` +
          'Are you sure you want to overwrite the previous settings on the owned RSUs?'

    confirmAlert(
      Options(title, message, [
        {
          label: 'Yes',
          onClick: () =>
            updateOwnedRsus().then((data: any) => {
              if (data.payload?.success) {
                toast.success(data.payload.message)
              } else {
                toast.error(data.payload?.message ?? 'Failed to update RSUs')
              }
            }),
        },
        { label: 'No', onClick: () => {} },
      ])
    )
  }

  const handleOrgTimDepositChange = (newValue: boolean) =>
    confirmOrgRsuOptionChange(`${newValue ? 'Enable' : 'Disable'} TIM Deposit`, () =>
      dispatch(updateOrgTimDeposit({ orgName: selectedOrg, email: selectedOrgEmail, timDeposit: newValue }))
    )

  const handleOrgSnmpMonitoringChange = (newValue: boolean) =>
    confirmOrgRsuOptionChange(`${newValue ? 'Enable' : 'Disable'} SNMP Monitoring`, () =>
      dispatch(updateOrgSnmpMonitoring({ orgName: selectedOrg, email: selectedOrgEmail, snmpMonitoring: newValue }))
    )

  return (
    <div className="accordion">
      <Accordion className="accordion-content" elevation={0}>
        <AccordionSummary
          expandIcon={<ExpandMoreIcon />}
          aria-controls="panel1a-content"
          id="panel1a-header"
          sx={{ display: 'flex', alignItems: 'center' }}
        >
          <div style={{ display: 'flex', alignItems: 'center', flexGrow: 1, gap: '16px' }}>
            <Typography variant="h6">RSUs</Typography>
            <Typography
              variant="subtitle1"
              sx={{
                color:
                  timDepositStatus === 'Enabled'
                    ? theme.palette.success.main
                    : timDepositStatus === 'Disabled'
                      ? theme.palette.error.main
                      : theme.palette.warning.main,
                fontWeight: 'bold',
                bgcolor:
                  timDepositStatus === 'Enabled'
                    ? 'rgba(46, 125, 50, 0.1)'
                    : timDepositStatus === 'Disabled'
                      ? 'rgba(211, 47, 47, 0.1)'
                      : 'rgba(237, 108, 2, 0.1)',
                px: 1,
                borderRadius: 1,
              }}
            >
              TIM Deposit: {timDepositStatus}
            </Typography>
            <Typography
              variant="subtitle1"
              sx={{
                color:
                  snmpMonitoringStatus === 'Enabled'
                    ? theme.palette.success.main
                    : snmpMonitoringStatus === 'Disabled'
                      ? theme.palette.error.main
                      : snmpMonitoringStatus === 'Mixed'
                        ? theme.palette.warning.main
                        : theme.palette.text.secondary,
                fontWeight: 'bold',
                bgcolor:
                  snmpMonitoringStatus === 'Enabled'
                    ? 'rgba(46, 125, 50, 0.1)'
                    : snmpMonitoringStatus === 'Disabled'
                      ? 'rgba(211, 47, 47, 0.1)'
                      : snmpMonitoringStatus === 'Mixed'
                        ? 'rgba(237, 108, 2, 0.1)'
                        : 'transparent',
                px: 1,
                borderRadius: 1,
              }}
            >
              SNMP Monitoring: {snmpMonitoringStatus}
            </Typography>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', justifyContent: 'flex-end', maxWidth: '50%' }}>
            <Button
              variant="contained"
              color="primary"
              size="small"
              onClick={(e) => {
                e.stopPropagation()
                handleOrgTimDepositChange(true).catch(() => toast.error('Failed to update RSUs'))
              }}
            >
              Enable TIM Deposit
            </Button>
            <Button
              variant="contained"
              color="error"
              size="small"
              onClick={(e) => {
                e.stopPropagation()
                handleOrgTimDepositChange(false).catch(() => toast.error('Failed to update RSUs'))
              }}
            >
              Disable TIM Deposit
            </Button>
            <Button
              variant="contained"
              color="primary"
              size="small"
              onClick={(e) => {
                e.stopPropagation()
                handleOrgSnmpMonitoringChange(true).catch(() => toast.error('Failed to update RSUs'))
              }}
            >
              Enable SNMP Monitoring
            </Button>
            <Button
              variant="contained"
              color="error"
              size="small"
              onClick={(e) => {
                e.stopPropagation()
                handleOrgSnmpMonitoringChange(false).catch(() => toast.error('Failed to update RSUs'))
              }}
            >
              Disable SNMP Monitoring
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

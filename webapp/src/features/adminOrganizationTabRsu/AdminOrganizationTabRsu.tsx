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
  selectTimDeposit,
  selectSnmpMonitoring,
  selectSelectedOrgName,
} from '../adminOrganizationTab/adminOrganizationTabSlice'
import { selectLoadingGlobal } from '../../generalSlices/userSlice'
import { useSelector } from 'react-redux'

import '../adminRsuTab/Admin.css'
import { Action, Column } from '@material-table/core'
import { AdminOrgRsu } from '../adminOrganizationTab/adminOrganizationTabSlice'
import toast from 'react-hot-toast'
import { AddCircleOutline, DeleteOutline } from '@mui/icons-material'
import { Multiselect } from 'react-widgets/cjs'
import '../css/multiselect.css'
import {
  useGetAllRsusNotInOrganizationQuery,
  useLazyGetRsuOrganizationsQuery,
  usePatchOrganizationMutation,
} from '../api/organizationApiSlice'
import { AdminRsu } from '../../models/Rsu'

interface AdminOrganizationTabRsuProps {
  selectedOrg: string
  selectedOrgEmail: string
  tableData: AdminOrgRsu[]
}

const AdminOrganizationTabRsu = (props: AdminOrganizationTabRsuProps) => {
  const { selectedOrg, selectedOrgEmail } = props
  const theme = useTheme()

  const { data: availableRsuList } = useGetAllRsusNotInOrganizationQuery(selectedOrg)

  const [patchOrganization] = usePatchOrganizationMutation()
  const [getRsuOrganizations] = useLazyGetRsuOrganizationsQuery()
  const [selectedRsuList, setSelectedRsuList] = useState<AdminRsu[]>([])
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
      onClick: (event, rowData: AdminOrgRsu | AdminOrgRsu[]) => {
        const buttons = [
          {
            label: 'Yes',
            onClick: () => {
              if (Array.isArray(rowData)) {
                rsuMultiDelete(rowData)
              } else {
                rsuOnDelete(rowData)
              }
            },
          },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options(
          'Delete RSU',
          'Are you sure you want to delete "' +
            (Array.isArray(rowData) ? rowData.map((rsu) => rsu.ip).join(', ') : rowData.ip) +
            '" from ' +
            selectedOrg +
            ' organization?',
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
      onClick: (event, rowData: AdminOrgRsu | AdminOrgRsu[]) => {
        const buttons = [
          {
            label: 'Yes',
            onClick: () => {
              if (Array.isArray(rowData)) {
                rsuMultiDelete(rowData)
              } else {
                rsuOnDelete(rowData)
              }
            },
          },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options(
          'Delete Selected RSUs',
          'Are you sure you want to delete ' +
            (Array.isArray(rowData) ? rowData.length : 1) +
            ' RSU(s) from ' +
            selectedOrg +
            ' organization?',
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
            setSelectedRsuList(value as AdminRsu[])
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
    setSelectedRsuList([])
  }, [selectedOrg])

  const rsuOnDelete = async (rsu: AdminOrgRsu) => {
    const loadingToast = toast.loading(`Deleting RSU ${rsu.ip}...`)
    try {
      const rsuData = await getRsuOrganizations(rsu.ip).unwrap()
      if (rsuData.length > 1) {
        await patchOrganization({
          orig_name: selectedOrg,
          name: selectedOrg,
          email: selectedOrgEmail,
          rsus_to_remove: [rsu.ip],
          rsus_to_add: [],
          users_to_add: [],
          users_to_modify: [],
          users_to_remove: [],
          intersections_to_add: [],
          intersections_to_remove: [],
        }).unwrap()
        toast.success('RSU deleted successfully', { id: loadingToast })
      } else {
        toast.dismiss(loadingToast)
        alert(
          'Cannot remove RSU ' +
            rsu.ip +
            ' from ' +
            selectedOrg +
            ' because it must belong to at least one organization.'
        )
      }
    } catch (error) {
      toast.error('Failed to delete RSU due to error: ' + error, { id: loadingToast })
    }
  }

  const rsuMultiDelete = async (rows: AdminOrgRsu[]) => {
    const loadingToast = toast.loading(`Deleting ${rows.length} RSU(s)...`)
    try {
      const invalidRsus: string[] = []
      const validRsuIps: string[] = []
      for (const row of rows) {
        const rsuData = await getRsuOrganizations(row.ip).unwrap()
        if (rsuData.length > 1) {
          validRsuIps.push(row.ip)
        } else {
          invalidRsus.push(row.ip)
        }
      }
      if (invalidRsus.length > 0) {
        toast.dismiss(loadingToast)
        alert(
          'Cannot remove RSU(s) ' +
            invalidRsus.join(', ') +
            ' from ' +
            selectedOrg +
            ' because they must belong to at least one organization.'
        )
        return
      }
      await patchOrganization({
        orig_name: selectedOrg,
        name: selectedOrg,
        email: selectedOrgEmail,
        rsus_to_remove: validRsuIps,
        rsus_to_add: [],
        users_to_add: [],
        users_to_modify: [],
        users_to_remove: [],
        intersections_to_add: [],
        intersections_to_remove: [],
      }).unwrap()
      toast.success('RSU(s) deleted successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to delete RSU(s) due to error: ' + error, { id: loadingToast })
    }
  }

  const rsuMultiAdd = async (rsuList: AdminRsu[]) => {
    if (rsuList.length === 0) {
      toast.error('Please select RSUs to add')
      return
    }
    const loadingToast = toast.loading(`Adding ${rsuList.length} RSU(s)...`)
    try {
      await patchOrganization({
        orig_name: selectedOrg,
        name: selectedOrg,
        email: selectedOrgEmail,
        rsus_to_add: rsuList.map((r) => r.ip),
        rsus_to_remove: [],
        users_to_add: [],
        users_to_modify: [],
        users_to_remove: [],
        intersections_to_add: [],
        intersections_to_remove: [],
      }).unwrap()
      setSelectedRsuList([])
      toast.success('RSU(s) added successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to add RSU(s) due to error: ' + error, { id: loadingToast })
    }
  }

  const handleOrgTimDepositChange = (newValue: boolean) => {
    const actionLabel = newValue ? 'Enable' : 'Disable'
    const buttons = [
      {
        label: 'Yes',
        onClick: async () => {
          const loadingToast = toast.loading(`${actionLabel}ing TIM Deposit...`)
          try {
            await patchOrganization({
              orig_name: selectedOrg,
              name: selectedOrg,
              email: selectedOrgEmail,
              tim_deposit: newValue,
              rsus_to_add: [],
              rsus_to_remove: [],
              users_to_add: [],
              users_to_modify: [],
              users_to_remove: [],
              intersections_to_add: [],
              intersections_to_remove: [],
            }).unwrap()
            toast.success(`Successfully ${actionLabel.toLowerCase()}d TIM Deposit for all RSUs in ${selectedOrg}`, {
              id: loadingToast,
            })
          } catch (error) {
            toast.error('Failed to update TIM Deposit: ' + error, { id: loadingToast })
          }
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

  const handleOrgSnmpMonitoringChange = (newValue: boolean) => {
    const actionLabel = newValue ? 'Enable' : 'Disable'
    const buttons = [
      {
        label: 'Yes',
        onClick: async () => {
          const loadingToast = toast.loading(`${actionLabel}ing SNMP Monitoring...`)
          try {
            await patchOrganization({
              orig_name: selectedOrg,
              name: selectedOrg,
              email: selectedOrgEmail,
              snmp_monitoring: newValue,
              rsus_to_add: [],
              rsus_to_remove: [],
              users_to_add: [],
              users_to_modify: [],
              users_to_remove: [],
              intersections_to_add: [],
              intersections_to_remove: [],
            }).unwrap()
            toast.success(`Successfully ${actionLabel.toLowerCase()}d SNMP Monitoring for all RSUs in ${selectedOrg}`, {
              id: loadingToast,
            })
          } catch (error) {
            toast.error('Failed to update SNMP Monitoring: ' + error, { id: loadingToast })
          }
        },
      },
      { label: 'No', onClick: () => {} },
    ]
    const alertOptions = Options(
      `${actionLabel} SNMP Monitoring`,
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
                handleOrgTimDepositChange(true)
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
                handleOrgTimDepositChange(false)
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
                handleOrgSnmpMonitoringChange(true)
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
                handleOrgSnmpMonitoringChange(false)
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

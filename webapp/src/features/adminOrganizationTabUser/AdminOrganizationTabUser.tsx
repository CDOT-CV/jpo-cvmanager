import { useState, useEffect } from 'react'
import AdminTable from '../../components/AdminTable'
import Accordion from '@mui/material/Accordion'
import AccordionSummary from '@mui/material/AccordionSummary'
import AccordionDetails from '@mui/material/AccordionDetails'
import Typography from '@mui/material/Typography'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { DropdownList, Multiselect } from 'react-widgets'
import { confirmAlert } from 'react-confirm-alert'
import { Options } from '../../components/AdminDeletionOptions'
import {
  selectAuthLoginData,
  selectEmail,
  selectLoadingGlobal,
  setOrganizationList,
} from '../../generalSlices/userSlice'
import { useSelector, useDispatch } from 'react-redux'

import '../adminRsuTab/Admin.css'
import { Action, Column } from '@material-table/core'
import { AdminOrgUser } from '../adminOrganizationTab/adminOrganizationTabSlice'
import toast from 'react-hot-toast'

import { useTheme } from '@mui/material'
import { AddCircleOutline, DeleteOutline } from '@mui/icons-material'
import {
  useGetAllUsersNotInOrganizationQuery,
  useLazyGetUserOrganizationsQuery,
  usePatchOrganizationMutation,
} from '../api/organizationApiSlice'
import { useGetUserAllowedSelectionsQuery } from '../api/userApiSlice'

interface AdminOrganizationTabUserProps {
  selectedOrg: string
  selectedOrgEmail: string
  tableData: AdminOrgUser[]
  updateTableData: (org: string) => void
}

const AdminOrganizationTabUser = (props: AdminOrganizationTabUserProps) => {
  const { selectedOrg, selectedOrgEmail } = props
  const dispatch = useDispatch()
  const theme = useTheme()

  const { data: availableUserList } = useGetAllUsersNotInOrganizationQuery(selectedOrg, {
    skip: !selectedOrg,
  })
  const { data: allowedSelections } = useGetUserAllowedSelectionsQuery()
  const [patchOrganization] = usePatchOrganizationMutation()
  const [getUserOrganizations] = useLazyGetUserOrganizationsQuery()

  const [selectedUserList, setSelectedUserList] = useState<AdminOrgUser[]>([])
  const loadingGlobal = useSelector(selectLoadingGlobal)
  const authLoginData = useSelector(selectAuthLoginData)
  const userEmail = useSelector(selectEmail)
  const [userColumns] = useState<Column<any>[]>([
    {
      title: 'First Name',
      field: 'first_name',
      editable: 'never',
      id: 0,
    },
    {
      title: 'Last Name',
      field: 'last_name',
      editable: 'never',
      id: 1,
    },
    { title: 'Email', field: 'email', editable: 'never', id: 2 },
    {
      title: 'Role',
      field: 'role',
      id: 3,
      lookup: { user: 'User', operator: 'Operator', admin: 'Admin' },
    },
  ])

  const userActions: Action<AdminOrgUser>[] = [
    {
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      iconProps: {
        itemType: 'rowAction',
      },
      position: 'row',
      onClick: (event, rowData: AdminOrgUser | AdminOrgUser[]) => {
        const buttons = [
          {
            label: 'Yes',
            onClick: () => {
              if (Array.isArray(rowData)) {
                userMultiDelete(rowData)
              } else {
                userOnDelete(rowData)
              }
            },
          },
          {
            label: 'No',
            onClick: () => {},
          },
        ]
        const alertOptions = Options(
          'Delete User',
          'Are you sure you want to delete "' +
            (Array.isArray(rowData) ? rowData.map((user) => user.email).join(', ') : rowData.email) +
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
      position: 'toolbarOnSelect',
      iconProps: {
        itemType: 'rowAction',
      },
      onClick: (event, rowData: AdminOrgUser | AdminOrgUser[]) => {
        const buttons = [
          {
            label: 'Yes',
            onClick: () => {
              if (Array.isArray(rowData)) {
                userMultiDelete(rowData)
              } else {
                userOnDelete(rowData)
              }
            },
          },
          {
            label: 'No',
            onClick: () => {},
          },
        ]
        const alertOptions = Options(
          'Delete Selected Users',
          'Are you sure you want to delete ' +
            (Array.isArray(rowData) ? rowData.length : 1) +
            ' user(s) from ' +
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
          className="org-multiselect"
          dataKey="id"
          textField="email"
          placeholder="Click to add users"
          data={availableUserList}
          value={selectedUserList}
          onChange={(value) => handleUserSelectionChange(value as AdminUser[])}
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
        title: 'Add User',
        color: 'primary',
        itemType: 'contained',
      },
      icon: () => <AddCircleOutline />,
      onClick: () => userMultiAdd(selectedUserList),
    },
  ]

  const userTableEditable = {
    onBulkUpdate: (
      changes: Record<
        number,
        {
          oldData: AdminOrgUser
          newData: AdminOrgUser
        }
      >
    ) =>
      new Promise((resolve) => {
        userBulkEdit(changes)
        setTimeout(() => {
          resolve(null)
        }, 2000)
      }),
  }

  useEffect(() => {
    setSelectedUserList([])
  }, [selectedOrg])

  const handleUserSelectionChange = (value: AdminUser[]) => {
    setSelectedUserList(
      value.map((user) => ({
        ...user,
        role: '',
        organizations: user.organizations.map((org) => ({ name: org.organization, role: org.role })),
      }))
    )
  }

  const handleUserRoleChange = (email: string, role: string) => {
    setSelectedUserList((prev) => prev.map((u) => (u.email === email ? { ...u, role } : u)))
  }

  const userOnDelete = async (row: AdminOrgUser) => {
    const loadingToast = toast.loading(`Deleting User ${row.email}...`)
    try {
      const userOrgs = await getUserOrganizations(row.email).unwrap()
      if (userOrgs.length > 1) {
        await patchOrganization({
          orig_name: selectedOrg,
          name: selectedOrg,
          email: selectedOrgEmail,
          users_to_remove: [row.email],
          users_to_add: [],
          users_to_modify: [],
          rsus_to_add: [],
          rsus_to_remove: [],
          intersections_to_add: [],
          intersections_to_remove: [],
        }).unwrap()
        props.updateTableData(selectedOrg)
        if (row.email === authLoginData?.data?.email) {
          dispatch(setOrganizationList({ value: { name: selectedOrg, role: row.role }, type: 'delete' }))
        }
        toast.success('User deleted successfully', { id: loadingToast })
      } else {
        toast.dismiss(loadingToast)
        alert(
          'Cannot remove User ' +
            row.email +
            ' from ' +
            selectedOrg +
            ' because they must belong to at least one organization.'
        )
      }
    } catch (error) {
      toast.error('Failed to delete User due to error: ' + error, { id: loadingToast })
    }
  }

  const userMultiDelete = async (rows: AdminOrgUser[]) => {
    const loadingToast = toast.loading(`Deleting ${rows.length} User(s)...`)
    try {
      const invalidUsers: string[] = []
      const validEmails: string[] = []
      for (const user of rows) {
        const userOrgs = await getUserOrganizations(user.email).unwrap()
        if (userOrgs.length > 1) {
          validEmails.push(user.email)
        } else {
          invalidUsers.push(user.email)
        }
      }
      if (invalidUsers.length > 0) {
        toast.dismiss(loadingToast)
        alert(
          'Cannot remove User(s) ' +
            invalidUsers.join(', ') +
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
        users_to_remove: validEmails,
        users_to_add: [],
        users_to_modify: [],
        rsus_to_add: [],
        rsus_to_remove: [],
        intersections_to_add: [],
        intersections_to_remove: [],
      }).unwrap()
      props.updateTableData(selectedOrg)
      for (const user of rows) {
        if (user.email === authLoginData?.data?.email) {
          dispatch(setOrganizationList({ value: { name: selectedOrg, role: user.role }, type: 'delete' }))
        }
      }
      toast.success('User(s) deleted successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to delete User(s) due to error: ' + error, { id: loadingToast })
    }
  }

  const userMultiAdd = async (userList: AdminOrgUser[]) => {
    if (userList.length === 0) {
      toast.error('Please select users to add')
      return
    }
    const missingRole = userList.find((u) => !u.role)
    if (missingRole) {
      toast.error('Please select a role for all users to add')
      return
    }
    const loadingToast = toast.loading(`Adding ${userList.length} User(s)...`)
    try {
      await patchOrganization({
        orig_name: selectedOrg,
        name: selectedOrg,
        email: selectedOrgEmail,
        users_to_add: userList.map((u) => ({ email: u.email, role: u.role })),
        users_to_remove: [],
        users_to_modify: [],
        rsus_to_add: [],
        rsus_to_remove: [],
        intersections_to_add: [],
        intersections_to_remove: [],
      }).unwrap()
      setSelectedUserList([])
      props.updateTableData(selectedOrg)
      for (const user of userList) {
        if (user.email === authLoginData?.data?.email) {
          dispatch(setOrganizationList({ value: { name: selectedOrg, role: user.role }, type: 'add' }))
        }
      }
      toast.success('User(s) added successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to add User(s) due to error: ' + error, { id: loadingToast })
    }
  }

  const userBulkEdit = async (
    json: Record<
      number,
      {
        oldData: AdminOrgUser
        newData: AdminOrgUser
      }
    >
  ) => {
    const loadingToast = toast.loading('Updating User(s)...')
    try {
      const rows = Object.values(json)
      await patchOrganization({
        orig_name: selectedOrg,
        name: selectedOrg,
        email: selectedOrgEmail,
        users_to_modify: rows.map((r) => ({ email: r.newData.email, role: r.newData.role })),
        users_to_add: [],
        users_to_remove: [],
        rsus_to_add: [],
        rsus_to_remove: [],
        intersections_to_add: [],
        intersections_to_remove: [],
      }).unwrap()
      for (const row of rows) {
        if (row.newData.email === userEmail) {
          dispatch(
            setOrganizationList({
              value: { name: selectedOrg, role: row.newData.role },
              orgName: selectedOrg,
              type: 'update',
            })
          )
        }
      }
      props.updateTableData(selectedOrg)
      toast.success('User(s) updated successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to update User(s) due to error: ' + error, { id: loadingToast })
    }
  }

  return (
    <div>
      <Accordion elevation={0}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />} aria-controls="panel1a-content" id="panel1a-header">
          <Typography variant="h6">Users</Typography>
        </AccordionSummary>
        <AccordionDetails sx={{ padding: '8px 0px' }}>
          {loadingGlobal === false && (
            <>
              {selectedUserList.length > 0 &&
                selectedUserList.map((user) => (
                  <div key={user.email}>
                    <p>{user.email}</p>
                    <DropdownList
                      className="org-form-dropdown"
                      dataKey="role"
                      textField="role"
                      data={allowedSelections?.roles || []}
                      value={user}
                      onChange={(value) => {
                        handleUserRoleChange(user.email, value)
                      }}
                    />
                  </div>
                ))}
              <AdminTable
                title=""
                data={props.tableData}
                columns={userColumns}
                actions={userActions}
                editable={userTableEditable}
              />
            </>
          )}
        </AccordionDetails>
      </Accordion>
    </div>
  )
}

export default AdminOrganizationTabUser

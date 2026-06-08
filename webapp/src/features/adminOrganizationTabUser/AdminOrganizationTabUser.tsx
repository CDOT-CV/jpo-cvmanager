import { useState, useEffect, useMemo } from 'react'
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
import toast from 'react-hot-toast'

import { useTheme } from '@mui/material'
import { AddCircleOutline, DeleteOutline } from '@mui/icons-material'
import {
  useGetAllUsersNotInOrganizationQuery,
  useLazyGetUserOrganizationsQuery,
  usePatchOrganizationMutation,
} from '../api/organizationApiSlice'
import { useGetUserAllowedSelectionsQuery, useGetUsersQuery } from '../api/userApiSlice'

interface AdminOrganizationTabUserProps {
  selectedOrgId: number
  selectedOrgName: string
}

const AdminOrganizationTabUser = (props: AdminOrganizationTabUserProps) => {
  const { selectedOrgId, selectedOrgName } = props
  const dispatch = useDispatch()
  const theme = useTheme()

  const { data: availableUserList } = useGetAllUsersNotInOrganizationQuery(selectedOrgId, {
    skip: !selectedOrgId,
  })
  const { data: userTableData } = useGetUsersQuery({ organization: selectedOrgId })
  const { data: allowedSelections } = useGetUserAllowedSelectionsQuery()
  const [patchOrganization] = usePatchOrganizationMutation()
  const [getUserOrganizations] = useLazyGetUserOrganizationsQuery()

  const [selectedUserList, setSelectedUserList] = useState<AdminUserForOrg[]>([])
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

  const userData = useMemo(() => {
    return userTableData?.content.map((user) => {
      const orgInfo = user.organizations.find((org) => org.organization === selectedOrgId)
      return {
        ...user,
        role: orgInfo ? orgInfo.role.toLowerCase() : '',
      }
    })
  }, [userTableData])

  const userActions: Action<AdminUserForOrg>[] = [
    {
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      iconProps: {
        itemType: 'rowAction',
      },
      position: 'row',
      onClick: (event, rowData: AdminUserForOrg | AdminUserForOrg[]) => {
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
            selectedOrgName +
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
      onClick: (event, rowData: AdminUserForOrg | AdminUserForOrg[]) => {
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
            selectedOrgName +
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
          onChange={(value) => setSelectedUserList(value as AdminUserForOrg[])}
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
          oldData: AdminUserForOrg
          newData: AdminUserForOrg
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
  }, [selectedOrgName])

  const handleUserRoleChange = (email: string, role: UserRole) => {
    setSelectedUserList((prev) => prev.map((u) => (u.email === email ? { ...u, role } : u)))
  }

  const userOnDelete = async (row: AdminUserForOrg) => {
    const loadingToast = toast.loading(`Deleting User ${row.email}...`)
    try {
      const userOrgs = await getUserOrganizations(row.email).unwrap()
      if (userOrgs.length > 1) {
        await patchOrganization({
          orig_name: selectedOrgName,
          users_to_remove: [row.email],
        }).unwrap()
        if (row.email === authLoginData?.data?.email) {
          dispatch(setOrganizationList({ value: { name: selectedOrgName, role: row.role }, type: 'delete' }))
        }
        toast.success('User deleted successfully', { id: loadingToast })
      } else {
        toast.dismiss(loadingToast)
        alert(
          'Cannot remove User ' +
            row.email +
            ' from ' +
            selectedOrgName +
            ' because they must belong to at least one organization.'
        )
      }
    } catch (error) {
      toast.error('Failed to delete User due to error: ' + error, { id: loadingToast })
    }
  }

  const userMultiDelete = async (rows: AdminUserForOrg[]) => {
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
            selectedOrgName +
            ' because they must belong to at least one organization.'
        )
        return
      }
      await patchOrganization({
        orig_name: selectedOrgName,
        users_to_remove: validEmails,
      }).unwrap()
      for (const user of rows) {
        if (user.email === authLoginData?.data?.email) {
          dispatch(setOrganizationList({ value: { name: selectedOrgName, role: user.role }, type: 'delete' }))
        }
      }
      toast.success('User(s) deleted successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to delete User(s) due to error: ' + error, { id: loadingToast })
    }
  }

  const userMultiAdd = async (userList: AdminUserForOrg[]) => {
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
        orig_name: selectedOrgName,
        users_to_add: userList.map((u) => ({ email: u.email, role: u.role })),
      }).unwrap()
      setSelectedUserList([])
      for (const user of userList) {
        if (user.email === authLoginData?.data?.email) {
          dispatch(setOrganizationList({ value: { name: selectedOrgName, role: user.role }, type: 'add' }))
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
        oldData: AdminUserForOrg
        newData: AdminUserForOrg
      }
    >
  ) => {
    const loadingToast = toast.loading('Updating User(s)...')
    try {
      const rows = Object.values(json)
      await patchOrganization({
        orig_name: selectedOrgName,
        users_to_modify: rows.map((r) => ({ email: r.newData.email, role: r.newData.role })),
      }).unwrap()
      for (const row of rows) {
        if (row.newData.email === userEmail) {
          dispatch(
            setOrganizationList({
              value: { name: selectedOrgName, role: row.newData.role },
              orgName: selectedOrgName,
              type: 'update',
            })
          )
        }
      }
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
                data={userData || []}
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

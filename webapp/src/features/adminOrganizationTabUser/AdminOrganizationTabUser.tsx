import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import Accordion from '@mui/material/Accordion'
import AccordionDetails from '@mui/material/AccordionDetails'
import AccordionSummary from '@mui/material/AccordionSummary'
import Typography from '@mui/material/Typography'
import { useEffect, useMemo, useState } from 'react'
import { confirmAlert } from 'react-confirm-alert'
import { useDispatch, useSelector } from 'react-redux'
import { DropdownList, Multiselect } from 'react-widgets'
import { Options } from '../../components/AdminDeletionOptions'
import AdminTable from '../../components/AdminTable'
import {
    addOrModifyOrgAssociationByOrgName,
    deleteOrgAssociationByOrgId,
    selectAuthLoginData,
    selectEmail,
    selectLoadingGlobal,
} from '../../generalSlices/userSlice'

import { Action, Column } from '@material-table/core'
import toast from 'react-hot-toast'
import '../adminRsuTab/Admin.css'

import { AddCircleOutline, Close, DeleteOutline } from '@mui/icons-material'
import { IconButton, useTheme } from '@mui/material'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../store'
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
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
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
      // TODO: After UserOrganization gets updated to use org id number, change this comparison to use selectedOrgId instead of selectedOrgName
      const orgInfo = user.organizations.find((org) => org.organization === selectedOrgName)
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
            onClick: async () => {
              if (Array.isArray(rowData)) {
                await userMultiDelete(rowData)
              } else {
                await userOnDelete(rowData)
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
            onClick: async () => {
              if (Array.isArray(rowData)) {
                await userMultiDelete(rowData)
              } else {
                await userOnDelete(rowData)
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
          onChange={(value) =>
            setSelectedUserList(value.map((user) => ({ ...user, role: (user as any).role || 'USER' })))
          }
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
          id: selectedOrgId,
          users_to_remove: [row.email],
        }).unwrap()
        if (row.email === authLoginData?.data?.email) {
          dispatch(deleteOrgAssociationByOrgId(props.selectedOrgId))
        }
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
      const orgCounts = await Promise.all(rows.map((user) => getUserOrganizations(user.email).unwrap()))
      const invalidUsers: string[] = []
      const validEmails: string[] = []
      rows.forEach((user, i) => {
        if (orgCounts[i].length > 1) {
          validEmails.push(user.email)
        } else {
          invalidUsers.push(user.email)
        }
      })
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
        id: selectedOrgId,
        users_to_remove: validEmails,
      }).unwrap()
      for (const user of rows) {
        if (user.email === authLoginData?.data?.email) {
          dispatch(deleteOrgAssociationByOrgId(props.selectedOrgId))
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
    if (userList.some((u) => !u.role)) {
      toast.error('Please select a role for all users to add')
      return
    }
    const loadingToast = toast.loading(`Adding ${userList.length} User(s)...`)
    try {
      await patchOrganization({
        id: selectedOrgId,
        users_to_add: userList.map((u) => ({ email: u.email, role: u.role.toLowerCase() })),
      }).unwrap()
      setSelectedUserList([])
      for (const user of userList) {
        if (user.email === authLoginData?.data?.email) {
          dispatch(addOrModifyOrgAssociationByOrgName({ name: selectedOrgName, role: user.role as UserRole }))
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
        id: selectedOrgId,
        users_to_modify: rows.map((r) => ({ email: r.newData.email, role: r.newData.role })),
      }).unwrap()
      for (const row of rows) {
        if (row.newData.email === userEmail) {
          dispatch(addOrModifyOrgAssociationByOrgName({ name: selectedOrgName, role: row.newData.role as UserRole }))
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
                  <div
                    key={user.email}
                    style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '8px 16px' }}
                  >
                    <span style={{ whiteSpace: 'nowrap' }}>New User: {user.email}</span>
                    <span style={{ whiteSpace: 'nowrap' }}>Role:</span>
                    <div style={{ width: '200px', flexShrink: 0 }}>
                      <DropdownList
                        className="org-form-dropdown"
                        aria-label="Role"
                        dataKey="role"
                        textField="role"
                        data={allowedSelections?.roles || []}
                        value={user.role?.toLowerCase()}
                        onChange={(value) => {
                          handleUserRoleChange(user.email, value)
                        }}
                      />
                    </div>
                    <IconButton
                      aria-label={`Remove ${user.email}`}
                      size="small"
                      onClick={() => setSelectedUserList((prev) => prev.filter((u) => u.email !== user.email))}
                    >
                      <Close fontSize="small" />
                    </IconButton>
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

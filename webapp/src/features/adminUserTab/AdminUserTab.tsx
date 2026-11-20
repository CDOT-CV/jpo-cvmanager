import { useState } from 'react'
import { confirmAlert } from 'react-confirm-alert'
import { useSelector } from 'react-redux'
import { Options } from '../../components/AdminDeletionOptions'
import AdminTable from '../../components/AdminTable'
import { selectOrganizationId } from '../../generalSlices/userSlice'
import AdminAddUser from '../adminAddUser/AdminAddUser'
import AdminEditUser from '../adminEditUser/AdminEditUser'

import { Action } from '@material-table/core'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import ModeEditOutline from '@mui/icons-material/ModeEditOutline'
import { useTheme } from '@mui/material'
import toast from 'react-hot-toast'
import { Route, Routes, useNavigate } from 'react-router-dom'
import { useServerPagedAdminTable } from '../../hooks/useServerPagedAdminTable'
import { NotFound } from '../../pages/404'
import '../adminRsuTab/Admin.css'
import {
  useDeleteMultipleUsersMutation,
  useDeleteUserMutation,
  useGetUsersQuery,
  useLazyGetUsersQuery,
} from '../api/userApiSlice'

const AdminUserTab = () => {
  const navigate = useNavigate()
  const theme = useTheme()
  const organization = useSelector(selectOrganizationId)

  const [deleteUserApi] = useDeleteUserMutation()
  const [deleteMultipleUsersApi] = useDeleteMultipleUsersMutation()
  const [userSelection, setUserSelection] = useState<{
    organization: number | undefined
    rows: AdminUserWithId[]
  }>({ organization, rows: [] })
  const selectedUsers = userSelection.organization === organization ? userSelection.rows : []

  const [columns] = useState([
    { title: 'First Name', field: 'first_name', id: 0 },
    { title: 'Last Name', field: 'last_name', id: 1 },
    { title: 'Email', field: 'email', id: 2 },
    {
      title: 'Super User',
      field: 'super_user',
      id: 3,
      render: (rowData: AdminUserWithId) => (rowData.super_user ? 'Yes' : 'No'),
    },
  ])

  const { tableRef, isRefreshing, handleQueryChange, handleRefresh } = useServerPagedAdminTable({
    organization,
    columns,
    defaultSortField: 'first_name',
    entityName: 'Users',
    useQuery: useGetUsersQuery,
    useLazyQuery: useLazyGetUsersQuery,
  })

  const tableActions: Action<AdminUserWithId>[] = [
    {
      icon: () => <ModeEditOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      iconProps: {
        itemType: 'rowAction',
      },
      position: 'row',
      onClick: (event, rowData: AdminUserWithId) => onEdit(rowData),
    },
    {
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      iconProps: {
        itemType: 'rowAction',
      },
      position: 'row',
      onClick: (event, rowData: AdminUserWithId) => {
        const buttons = [
          {
            label: 'Yes',
            onClick: () => onDelete(rowData),
          },
          {
            label: 'No',
            onClick: () => {},
          },
        ]
        const alertOptions = Options('Delete User', 'Are you sure you want to delete "' + rowData.email + '"?', buttons)
        confirmAlert(alertOptions)
      },
    },
    {
      tooltip: 'Remove All Selected Users',
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      disabled: selectedUsers.length === 0,
      position: 'toolbarOnSelect',
      onClick: () => {
        const buttons = [
          {
            label: 'Yes',
            onClick: () => multiDelete(selectedUsers),
          },
          {
            label: 'No',
            onClick: () => {},
          },
        ]
        const alertOptions = Options(
          'Delete Selected Users',
          'Are you sure you want to delete ' + selectedUsers.length + ' users?',
          buttons
        )
        confirmAlert(alertOptions)
      },
    },
    {
      icon: () => null,
      position: 'toolbar',
      iconProps: {
        title: 'Refresh',
        color: 'info',
        itemType: 'outlined',
      },
      onClick: handleRefresh,
    },
    {
      icon: () => null,
      position: 'toolbar',
      iconProps: {
        title: 'New',
        color: 'primary',
        itemType: 'contained',
      },
      onClick: () => {
        navigate('addUser')
      },
    },
  ]

  const onDelete = async (row: AdminUserWithId) => {
    const loadingToast = toast.loading(`Deleting User ${row.email}...`)
    try {
      await deleteUserApi(row.email).unwrap()
      setUserSelection((current) => ({
        organization,
        rows: current.rows.filter((user) => user.email !== row.email),
      }))
      handleRefresh()
      toast.success('User Deleted Successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to delete User due to error: ' + error, { id: loadingToast })
    }
  }

  const multiDelete = async (rows: AdminUserWithId[]) => {
    const loadingToast = toast.loading(`Deleting ${rows.length} Users...`)
    try {
      await deleteMultipleUsersApi(rows.map((row) => row.email)).unwrap()
      setUserSelection({ organization, rows: [] })
      handleRefresh()
      toast.success('Users Deleted Successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to delete Users due to error: ' + error, { id: loadingToast })
    }
  }

  const onEdit = (row: AdminUserWithId) => {
    navigate('editUser/' + row.email)
  }

  return (
    <div>
      <Routes>
        <Route
          path="/"
          element={
            <div className="scroll-div-tab">
              <AdminTable
                title={''}
                columns={columns}
                actions={tableActions}
                handleQueryChange={handleQueryChange}
                isLoading={isRefreshing}
                tableRef={tableRef}
                selectionKeyField="email"
                selectedRows={selectedUsers}
                onSelectionChange={(rows) => setUserSelection({ organization, rows })}
              />
            </div>
          }
        />
        <Route
          path="addUser"
          element={
            <div className="scroll-div-tab">
              <AdminAddUser />
            </div>
          }
        />
        <Route
          path="editUser/:email"
          element={
            <div className="scroll-div-tab">
              <AdminEditUser />
            </div>
          }
        />
        <Route
          path="*"
          element={
            <NotFound
              redirectRoute="/dashboard/admin/users"
              redirectRouteName="Admin User Page"
              offsetHeight={319}
              description="This page does not exist. Please return to the admin User page."
            />
          }
        />
      </Routes>
    </div>
  )
}

export default AdminUserTab

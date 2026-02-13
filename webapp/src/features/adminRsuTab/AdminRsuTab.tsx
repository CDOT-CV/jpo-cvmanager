import React, { useState } from 'react'
import AdminAddRsu from '../adminAddRsu/AdminAddRsu'
import AdminEditRsu, { AdminEditRsuFormType } from '../adminEditRsu/AdminEditRsu'
import AdminTable from '../../components/AdminTable'
import { confirmAlert } from 'react-confirm-alert'
import { Options } from '../../components/AdminDeletionOptions'
import { selectOrganizationName } from '../../generalSlices/userSlice'
import { useSelector, useDispatch } from 'react-redux'

import './Admin.css'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { RootState } from '../../store'
import { Action, OrderByCollection } from '@material-table/core'
import { Route, Routes, useNavigate } from 'react-router-dom'
import { NotFound } from '../../pages/404'
import toast from 'react-hot-toast'
import { useTheme } from '@mui/material'
import { DeleteOutline, ModeEditOutline } from '@mui/icons-material'
import { useGetAllRsusQuery } from '../api/rsuApiSlice'
import { useDeleteRsuMutation, useDeleteMultipleRsusMutation } from '../api/rsuApiSlice'
import { usePaginatedQuery } from '../../hooks/use-paginated-query'

const AdminRsuTab = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const navigate = useNavigate()
  const theme = useTheme()
  const organization = useSelector(selectOrganizationName)

  const { fetchData, refetch, isLoading } = usePaginatedQuery(useGetAllRsusQuery, { organization })
  const [deleteRsuApi] = useDeleteRsuMutation()
  const [deleteMultipleRsusApi] = useDeleteMultipleRsusMutation()

  const [columns] = useState([
    { title: 'Milepost', field: 'milepost', id: 0 },
    { title: 'IP Address', field: 'ip', id: 1 },
    { title: 'Primary Route', field: 'primary_route', id: 2 },
    { title: 'RSU Model', field: 'model', id: 3 },
    { title: 'Serial Number', field: 'serial_number', id: 4 },
  ])

  const tableActions: Action<AdminEditRsuFormType>[] = [
    {
      icon: () => <ModeEditOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      tooltip: 'Edit RSU',
      position: 'row',
      iconProps: {
        itemType: 'rowAction',
      },
      onClick: (event, rowData: AdminEditRsuFormType) => {
        onEdit(rowData)
      },
    },
    {
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      tooltip: 'Delete RSU',
      position: 'row',
      iconProps: {
        itemType: 'rowAction',
      },
      onClick: (event, rowData: AdminEditRsuFormType) => {
        const buttons = [
          { label: 'Yes', onClick: () => onDelete(rowData) },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options('Delete RSU', 'Are you sure you want to delete "' + rowData.ip + '"?', buttons)
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
      onClick: (event, rowData: AdminEditRsuFormType[]) => {
        const buttons = [
          { label: 'Yes', onClick: () => multiDelete(rowData) },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options(
          'Delete Selected RSUs',
          'Are you sure you want to delete ' + rowData.length + ' RSUs?',
          buttons
        )
        confirmAlert(alertOptions)
      },
    },
    {
      icon: () => null,
      iconProps: {
        title: 'Refresh',
        color: 'info',
        itemType: 'outlined',
      },
      position: 'toolbar',
      onClick: refetch,
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
        navigate('addRsu')
      },
    },
  ]

  const onEdit = (row: AdminEditRsuFormType) => {
    navigate('editRsu/' + row.ip)
  }

  const onDelete = async (row: AdminEditRsuFormType) => {
    const loadingToast = toast.loading(`Deleting RSU ${row.ip}...`)
    try {
      await deleteRsuApi(row.ip).unwrap()
      toast.success('RSU Deleted Successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to delete RSU due to error: ' + error, { id: loadingToast })
    }
  }

  const multiDelete = async (rows: AdminEditRsuFormType[]) => {
    const loadingToast = toast.loading(`Deleting ${rows.length} RSUs...`)
    try {
      await deleteMultipleRsusApi(rows.map((row) => row.ip)).unwrap()
      toast.success('RSUs Deleted Successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to delete RSUs due to error: ' + error, { id: loadingToast })
    }
  }

  return (
    <div>
      <Routes>
        <Route
          path="/"
          element={
            isLoading === false && (
              <div className="scroll-div-tab">
                <AdminTable
                  title={''}
                  columns={columns}
                  actions={tableActions}
                  fetchData={fetchData}
                  isLoading={isLoading}
                />
              </div>
            )
          }
        />
        <Route path="addRsu" element={<AdminAddRsu />} />
        <Route path="editRsu/:rsuIp" element={<AdminEditRsu />} />
        <Route
          path="*"
          element={
            <NotFound
              redirectRoute="/dashboard/admin/rsus"
              redirectRouteName="Admin RSU Page"
              offsetHeight={319}
              description="This page does not exist. Please return to the admin RSU page."
            />
          }
        />
      </Routes>
    </div>
  )
}

export default AdminRsuTab

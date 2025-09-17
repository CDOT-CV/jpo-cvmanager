import React, { useState } from 'react'
import AdminTable from '../../components/AdminTable'
import { Typography, useTheme } from '@mui/material'
import Accordion from '@mui/material/Accordion'
import AccordionSummary from '@mui/material/AccordionSummary'
import AccordionDetails from '@mui/material/AccordionDetails'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { confirmAlert } from 'react-confirm-alert'
import { Options } from '../../components/AdminDeletionOptions'
import {
  useGetFirmwareFilesQuery,
  useGetRsuFirmwareStatusesQuery,
  useGetObuFirmwareStatusesQuery,
  useDeleteFirmwareFileMutation,
  selectRsuFirmwareFiles,
  selectObuFirmwareFiles,
  selectRsuFirmwareStatuses,
  selectObuFirmwareStatuses,
  type FirmwareFile,
  type FirmwareStatus,
} from '../api/firmwareApiSlice'
import { useSelector } from 'react-redux'
import { Action, Column } from '@material-table/core'
import toast from 'react-hot-toast'
import { DeleteOutline, Refresh, CloudUpload } from '@mui/icons-material'

const AdminFirmwareTab = () => {
  const theme = useTheme()

  // Use RTK Query hooks
  const { isLoading: rsuFilesLoading } = useGetFirmwareFilesQuery('RSU')
  const { isLoading: obuFilesLoading } = useGetFirmwareFilesQuery('OBU')
  const { isLoading: rsuStatusesLoading, refetch: refetchRsuStatuses } = useGetRsuFirmwareStatusesQuery(undefined)
  const { isLoading: obuStatusesLoading, refetch: refetchObuStatuses } = useGetObuFirmwareStatusesQuery(undefined)

  const [deleteFirmwareFile] = useDeleteFirmwareFileMutation()

  // Selectors for processed data
  const rsuFirmwareFiles = useSelector(selectRsuFirmwareFiles)
  const obuFirmwareFiles = useSelector(selectObuFirmwareFiles)
  const rsuFirmwareStatuses = useSelector(selectRsuFirmwareStatuses)
  const obuFirmwareStatuses = useSelector(selectObuFirmwareStatuses)

  const loading = rsuFilesLoading || obuFilesLoading || rsuStatusesLoading || obuStatusesLoading

  const [rsuFirmwareColumns] = useState<Column<FirmwareFile>[]>([
    { title: 'Filename', field: 'filename', id: 0, width: '20%' },
    { title: 'Version', field: 'version', id: 1, width: '12%' },
    {
      title: 'File Size (MB)',
      field: 'file_size',
      id: 2,
      width: '12%',
      render: (rowData) => (rowData.file_size / (1024 * 1024)).toFixed(2),
    },
    { title: 'Upload Date', field: 'upload_date', id: 3, width: '15%' },
    { title: 'Description', field: 'description', id: 4, width: '20%' },
    {
      title: 'Rules',
      field: 'rules',
      id: 5,
      width: '21%',
      render: (rowData) => {
        if (!rowData.rules || rowData.rules.length === 0) {
          return 'No rules'
        }
        return rowData.rules.map((rule) => rule.name).join(', ')
      },
    },
  ])

  const [obuFirmwareColumns] = useState<Column<FirmwareFile>[]>([
    { title: 'Filename', field: 'filename', id: 0, width: '25%' },
    { title: 'Version', field: 'version', id: 1, width: '15%' },
    {
      title: 'File Size (MB)',
      field: 'file_size',
      id: 2,
      width: '15%',
      render: (rowData) => (rowData.file_size / (1024 * 1024)).toFixed(2),
    },
    { title: 'Upload Date', field: 'upload_date', id: 3, width: '20%' },
    { title: 'Description', field: 'description', id: 4, width: '25%' },
  ])

  const [rsuFirmwareStatusColumns] = useState<Column<FirmwareStatus>[]>([
    { title: 'RSU IP', field: 'rsu_ip', id: 0, width: '20%' },
    { title: 'Current Version', field: 'current_version', id: 1, width: '20%' },
    { title: 'Target Version', field: 'target_version', id: 2, width: '20%' },
    { title: 'Status', field: 'upgrade_status', id: 3, width: '15%' },
    { title: 'Last Updated', field: 'last_updated', id: 4, width: '15%' },
    { title: 'Error', field: 'error_message', id: 5, width: '10%', render: (rowData) => rowData.error_message || '-' },
  ])

  const [obuFirmwareStatusColumns] = useState<Column<FirmwareStatus>[]>([
    { title: 'OBU ID', field: 'obu_id', id: 0, width: '20%' },
    { title: 'Current Version', field: 'current_version', id: 1, width: '20%' },
    { title: 'Target Version', field: 'target_version', id: 2, width: '20%' },
    { title: 'Status', field: 'upgrade_status', id: 3, width: '15%' },
    { title: 'Last Updated', field: 'last_updated', id: 4, width: '15%' },
    { title: 'Error', field: 'error_message', id: 5, width: '10%', render: (rowData) => rowData.error_message || '-' },
  ])

  const rsuFirmwareActions: Action<FirmwareFile>[] = [
    {
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      iconProps: {
        itemType: 'rowAction',
      },
      position: 'row',
      onClick: (event, rowData: FirmwareFile) => {
        const buttons = [
          { label: 'Yes', onClick: () => handleDeleteFirmwareFile(rowData) },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options(
          'Delete Firmware File',
          `Are you sure you want to delete "${rowData.filename}"?`,
          buttons
        )
        confirmAlert(alertOptions)
      },
    },
    {
      tooltip: 'Remove All Selected Firmware Files',
      icon: 'delete',
      position: 'toolbarOnSelect',
      iconProps: {
        itemType: 'rowAction',
      },
      onClick: (event, rowData: FirmwareFile[]) => {
        const buttons = [
          { label: 'Yes', onClick: () => handleMultiDeleteFirmwareFiles(rowData) },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options(
          'Delete Selected Firmware Files',
          `Are you sure you want to delete ${rowData.length} firmware files?`,
          buttons
        )
        confirmAlert(alertOptions)
      },
    },
    {
      icon: () => <CloudUpload sx={{ color: theme.palette.primary.main }} />,
      position: 'toolbar',
      iconProps: {
        title: 'Upload RSU Firmware',
        color: 'primary',
        itemType: 'contained',
      },
      onClick: () => {
        // TODO: Implement upload functionality
        toast('Upload functionality coming soon')
      },
    },
  ]

  const obuFirmwareActions: Action<FirmwareFile>[] = [
    {
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      iconProps: {
        itemType: 'rowAction',
      },
      position: 'row',
      onClick: (event, rowData: FirmwareFile) => {
        const buttons = [
          { label: 'Yes', onClick: () => handleDeleteFirmwareFile(rowData) },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options(
          'Delete Firmware File',
          `Are you sure you want to delete "${rowData.filename}"?`,
          buttons
        )
        confirmAlert(alertOptions)
      },
    },
    {
      tooltip: 'Remove All Selected Firmware Files',
      icon: 'delete',
      position: 'toolbarOnSelect',
      iconProps: {
        itemType: 'rowAction',
      },
      onClick: (event, rowData: FirmwareFile[]) => {
        const buttons = [
          { label: 'Yes', onClick: () => handleMultiDeleteFirmwareFiles(rowData) },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options(
          'Delete Selected Firmware Files',
          `Are you sure you want to delete ${rowData.length} firmware files?`,
          buttons
        )
        confirmAlert(alertOptions)
      },
    },
    {
      icon: () => <CloudUpload sx={{ color: theme.palette.primary.main }} />,
      position: 'toolbar',
      iconProps: {
        title: 'Upload OBU Firmware',
        color: 'primary',
        itemType: 'contained',
      },
      onClick: () => {
        // TODO: Implement upload functionality
        toast('Upload functionality coming soon')
      },
    },
  ]

  const rsuFirmwareStatusActions: Action<FirmwareStatus>[] = [
    {
      icon: () => <Refresh sx={{ color: theme.palette.primary.main }} />,
      position: 'toolbar',
      iconProps: {
        title: 'Refresh RSU Statuses',
        color: 'primary',
        itemType: 'contained',
      },
      onClick: () => {
        refetchRsuStatuses()
          .then((result) => {
            if (result.data?.success !== false) {
              toast.success('RSU firmware statuses refreshed successfully')
            } else {
              toast.error('Failed to refresh RSU firmware statuses: ' + result.data?.message)
            }
          })
          .catch(() => {
            toast.error('Failed to refresh RSU firmware statuses')
          })
      },
    },
  ]

  const obuFirmwareStatusActions: Action<FirmwareStatus>[] = [
    {
      icon: () => <Refresh sx={{ color: theme.palette.primary.main }} />,
      position: 'toolbar',
      iconProps: {
        title: 'Refresh OBU Statuses',
        color: 'primary',
        itemType: 'contained',
      },
      onClick: () => {
        refetchObuStatuses()
          .then((result) => {
            if (result.data?.success !== false) {
              toast.success('OBU firmware statuses refreshed successfully')
            } else {
              toast.error('Failed to refresh OBU firmware statuses: ' + result.data?.message)
            }
          })
          .catch(() => {
            toast.error('Failed to refresh OBU firmware statuses')
          })
      },
    },
  ]

  // Data is automatically fetched by RTK Query hooks

  const handleDeleteFirmwareFile = async (firmwareFile: FirmwareFile) => {
    try {
      const result = await deleteFirmwareFile({
        firmwareId: firmwareFile.id,
        deviceType: firmwareFile.device_type,
        removedBy: 'admin', // TODO: Get actual user from auth context
      }).unwrap()
      if (result.success) {
        toast.success('Firmware file deleted successfully')
      } else {
        toast.error('Failed to delete firmware file: ' + result.message)
      }
    } catch (error: any) {
      toast.error('Failed to delete firmware file: ' + (error.data?.message || error.message))
    }
  }

  const handleMultiDeleteFirmwareFiles = async (firmwareFiles: FirmwareFile[]) => {
    try {
      const promises = firmwareFiles.map((file) =>
        deleteFirmwareFile({
          firmwareId: file.id,
          deviceType: file.device_type,
          removedBy: 'admin', // TODO: Get actual user from auth context
        }).unwrap()
      )
      await Promise.all(promises)
      toast.success('Firmware files deleted successfully')
    } catch (error: any) {
      toast.error('Failed to delete some firmware files: ' + (error.data?.message || error.message))
    }
  }

  return (
    <div style={{ backgroundColor: theme.palette.background.paper, height: 'fit-content', padding: '10px 0px' }}>
      <div className="scroll-div-org-tab">
        {/* RSU Section */}
        <div className="accordion">
          <Accordion className="accordion-content" elevation={0} defaultExpanded>
            <AccordionSummary
              expandIcon={<ExpandMoreIcon />}
              aria-controls="rsu-section-content"
              id="rsu-section-header"
            >
              <Typography variant="h6">RSU Firmware Management</Typography>
            </AccordionSummary>
            <AccordionDetails sx={{ padding: '8px 0px' }}>
              {loading === false && (
                <div>
                  {/* RSU Firmware Files */}
                  <div style={{ marginBottom: '20px' }}>
                    <Typography variant="subtitle1" sx={{ marginBottom: '10px', fontWeight: 'bold' }}>
                      RSU Firmware Files
                    </Typography>
                    <AdminTable
                      title={''}
                      data={rsuFirmwareFiles}
                      columns={rsuFirmwareColumns}
                      actions={rsuFirmwareActions}
                    />
                  </div>

                  {/* RSU Firmware Status Updates */}
                  <div>
                    <Typography variant="subtitle1" sx={{ marginBottom: '10px', fontWeight: 'bold' }}>
                      RSU Firmware Status Updates
                    </Typography>
                    <AdminTable
                      title={''}
                      data={rsuFirmwareStatuses}
                      columns={rsuFirmwareStatusColumns}
                      actions={rsuFirmwareStatusActions}
                    />
                  </div>
                </div>
              )}
            </AccordionDetails>
          </Accordion>
        </div>

        {/* OBU Section */}
        <div className="accordion">
          <Accordion className="accordion-content" elevation={0}>
            <AccordionSummary
              expandIcon={<ExpandMoreIcon />}
              aria-controls="obu-section-content"
              id="obu-section-header"
            >
              <Typography variant="h6">OBU Firmware Management</Typography>
            </AccordionSummary>
            <AccordionDetails sx={{ padding: '8px 0px' }}>
              {loading === false && (
                <div>
                  {/* OBU Firmware Files */}
                  <div style={{ marginBottom: '20px' }}>
                    <Typography variant="subtitle1" sx={{ marginBottom: '10px', fontWeight: 'bold' }}>
                      OBU Firmware Files
                    </Typography>
                    <AdminTable
                      title={''}
                      data={obuFirmwareFiles}
                      columns={obuFirmwareColumns}
                      actions={obuFirmwareActions}
                    />
                  </div>

                  {/* OBU Firmware Status Updates */}
                  <div>
                    <Typography variant="subtitle1" sx={{ marginBottom: '10px', fontWeight: 'bold' }}>
                      OBU Firmware Status Updates
                    </Typography>
                    <AdminTable
                      title={''}
                      data={obuFirmwareStatuses}
                      columns={obuFirmwareStatusColumns}
                      actions={obuFirmwareStatusActions}
                    />
                  </div>
                </div>
              )}
            </AccordionDetails>
          </Accordion>
        </div>
      </div>
    </div>
  )
}

export default AdminFirmwareTab

import { useEffect, useState } from 'react'
import AdminTable from '../../components/AdminTable'
import Accordion from '@mui/material/Accordion'
import AccordionSummary from '@mui/material/AccordionSummary'
import AccordionDetails from '@mui/material/AccordionDetails'
import Typography from '@mui/material/Typography'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { confirmAlert } from 'react-confirm-alert'
import { Options } from '../../components/AdminDeletionOptions'
import {
  useGetIntersectionsNotInOrganizationQuery,
  useGetIntersectionsQuery,
  useLazyGetIntersectionQuery,
} from '../api/adminIntersectionApiSlice'
import { usePatchOrganizationMutation } from '../api/organizationApiSlice'
import { selectLoadingGlobal } from '../../generalSlices/userSlice'
import { useSelector } from 'react-redux'
import { Action, Column } from '@material-table/core'
import toast from 'react-hot-toast'
import { useTheme } from '@mui/material'
import { AddCircleOutline, DeleteOutline } from '@mui/icons-material'
import { Multiselect } from 'react-widgets/cjs'
import '../css/multiselect.css'
import { AdminIntersection } from '../../models/Intersection'

interface AdminOrganizationTabIntersectionProps {
  selectedOrgId: number
  selectedOrgName: string
}

const AdminOrganizationTabIntersection = (props: AdminOrganizationTabIntersectionProps) => {
  const { selectedOrgId, selectedOrgName } = props
  const theme = useTheme()
  const [fetchIntersection] = useLazyGetIntersectionQuery()
  const [patchOrganization] = usePatchOrganizationMutation()

  const { data: availableIntersectionsResponse } = useGetIntersectionsNotInOrganizationQuery(selectedOrgId)
  const { data: intersectionTableData } = useGetIntersectionsQuery(selectedOrgId)
  const availableIntersectionList = availableIntersectionsResponse?.intersection_data ?? []

  const [selectedIntersectionList, setSelectedIntersectionList] = useState<AdminIntersection[]>([])

  useEffect(() => {
    setSelectedIntersectionList([])
  }, [selectedOrgName])

  const loadingGlobal = useSelector(selectLoadingGlobal)
  const [intersectionColumns] = useState<Column<any>[]>([
    { title: 'ID', field: 'intersection_id', id: 0, width: '45%' },
    { title: 'Name', field: 'intersection_name', id: 1, width: '45%' },
  ])

  const intersectionActions: Action<AdminIntersection>[] = [
    {
      icon: () => <DeleteOutline sx={{ color: theme.palette.custom.rowActionIcon }} />,
      iconProps: {
        itemType: 'rowAction',
      },
      position: 'row',
      onClick: (event, rowData: AdminIntersection | AdminIntersection[]) => {
        const buttons = [
          {
            label: 'Yes',
            onClick: () => {
              if (Array.isArray(rowData)) {
                intersectionMultiDelete(rowData)
              } else {
                intersectionOnDelete(rowData)
              }
            },
          },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options(
          'Delete Intersection',
          'Are you sure you want to delete "' +
            (Array.isArray(rowData) ? rowData.map((r) => r.intersection_id).join(', ') : rowData.intersection_id) +
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
      onClick: (event, rowData: AdminIntersection | AdminIntersection[]) => {
        const buttons = [
          {
            label: 'Yes',
            onClick: () => {
              if (Array.isArray(rowData)) {
                intersectionMultiDelete(rowData)
              } else {
                intersectionOnDelete(rowData)
              }
            },
          },
          { label: 'No', onClick: () => {} },
        ]
        const alertOptions = Options(
          'Delete Selected Intersections',
          'Are you sure you want to delete ' +
            (Array.isArray(rowData) ? rowData.length : 1) +
            ' Intersection(s) from ' +
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
          dataKey="intersection_id"
          textField="intersection_name"
          placeholder="Click to add Intersections"
          data={availableIntersectionList}
          value={selectedIntersectionList}
          onChange={(value) => {
            setSelectedIntersectionList(value as AdminIntersection[])
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
        title: 'Add Intersection',
        color: 'primary',
        itemType: 'contained',
      },
      icon: () => <AddCircleOutline />,
      onClick: () => intersectionMultiAdd(selectedIntersectionList),
    },
  ]

  const intersectionOnDelete = async (intersection: AdminIntersection) => {
    const loadingToast = toast.loading(`Deleting Intersection ${intersection.intersection_id}...`)
    try {
      const result = await fetchIntersection(intersection.intersection_id).unwrap()
      if (result?.intersection_data?.organizations?.length > 1) {
        await patchOrganization({
          id: selectedOrgId,
          intersections_to_remove: [Number(intersection.intersection_id)],
        }).unwrap()
        toast.success('Intersection deleted successfully', { id: loadingToast })
      } else {
        toast.dismiss(loadingToast)
        alert(
          'Cannot remove Intersection ' +
            intersection.intersection_id +
            ' from ' +
            selectedOrgName +
            ' because it must belong to at least one organization.'
        )
      }
    } catch (error) {
      toast.error('Failed to delete Intersection due to error: ' + error, { id: loadingToast })
    }
  }

  const intersectionMultiAdd = async (intersectionList: AdminIntersection[]) => {
    if (intersectionList.length === 0) {
      toast.error('Please select Intersections to add')
      return
    }
    const loadingToast = toast.loading(`Adding ${intersectionList.length} Intersection(s)...`)
    try {
      await patchOrganization({
        id: selectedOrgId,
        intersections_to_add: intersectionList.map((i) => Number(i.intersection_id)),
      }).unwrap()
      setSelectedIntersectionList([])
      toast.success('Intersection(s) added successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to add Intersection(s) due to error: ' + error, { id: loadingToast })
    }
  }

  const intersectionMultiDelete = async (rows: AdminIntersection[]) => {
    const loadingToast = toast.loading(`Deleting ${rows.length} Intersection(s)...`)
    try {
      const invalidIntersections: string[] = []
      const validIntersectionIds: number[] = []
      for (const row of rows) {
        const result = await fetchIntersection(row.intersection_id).unwrap()
        if (result?.intersection_data?.organizations?.length > 1) {
          validIntersectionIds.push(Number(row.intersection_id))
        } else {
          invalidIntersections.push(row.intersection_id)
        }
      }
      if (invalidIntersections.length > 0) {
        toast.dismiss(loadingToast)
        alert(
          'Cannot remove Intersection(s) ' +
            invalidIntersections.join(', ') +
            ' from ' +
            selectedOrgName +
            ' because they must belong to at least one organization.'
        )
        return
      }
      await patchOrganization({
        id: selectedOrgId,
        intersections_to_remove: validIntersectionIds,
      }).unwrap()
      toast.success('Intersection(s) deleted successfully', { id: loadingToast })
    } catch (error) {
      toast.error('Failed to delete Intersection(s) due to error: ' + error, { id: loadingToast })
    }
  }

  return (
    <div className="accordion">
      <Accordion className="accordion-content" elevation={0}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />} aria-controls="panel1a-content" id="panel1a-header">
          <Typography variant="h6">Intersections</Typography>
        </AccordionSummary>
        <AccordionDetails sx={{ padding: '8px 0px' }}>
          {loadingGlobal === false && [
            <div key="adminTable">
              <AdminTable
                title={''}
                data={intersectionTableData?.intersection_data}
                columns={intersectionColumns}
                actions={intersectionActions}
              />
            </div>,
          ]}
        </AccordionDetails>
      </Accordion>
    </div>
  )
}

export default AdminOrganizationTabIntersection

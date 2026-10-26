import Grid2 from '@mui/material/Grid2'
import { useDispatch, useSelector } from 'react-redux'
import { DropdownList } from 'react-widgets'
import AdminOrganizationDeleteMenu from '../../components/AdminOrganizationDeleteMenu'
import AdminAddOrganization from '../adminAddOrganization/AdminAddOrganization'
import AdminEditOrganization from '../adminEditOrganization/AdminEditOrganization'
import AdminOrganizationTabIntersection from '../adminOrganizationTabIntersection/AdminOrganizationTabIntersection'
import AdminOrganizationTabRsu from '../adminOrganizationTabRsu/AdminOrganizationTabRsu'
import AdminOrganizationTabUser from '../adminOrganizationTabUser/AdminOrganizationTabUser'

import { AddCircleOutline, EditOutlined, Refresh } from '@mui/icons-material'
import { alpha, Button, useTheme } from '@mui/material'
import { AnyAction, ThunkDispatch } from '@reduxjs/toolkit'
import { useEffect, useState } from 'react'
import toast from 'react-hot-toast'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { ConditionalRenderIntersection, ConditionalRenderRsu } from '../../feature-flags'
import { deleteOrgAssociationByOrgId, selectOrganizationId } from '../../generalSlices/userSlice'
import { NotFound } from '../../pages/404'
import { RootState } from '../../store'
import { ContainedIconButton } from '../../styles/components/ContainedIconButton'
import '../adminRsuTab/Admin.css'
import {
  ADMIN_INTERSECTION_AVAILABLE_LIST_ID,
  ADMIN_INTERSECTION_LIST_ID,
  ADMIN_INTERSECTION_TAG,
  adminIntersectionApiSlice,
} from '../api/adminIntersectionApiSlice'
import {
  ORGANIZATION_API_AVAILABLE_RSU_LIST_TAG,
  ORGANIZATION_API_AVAILABLE_USER_LIST_TAG,
  ORGANIZATION_API_ORG_TAG,
  ORGANIZATION_API_RSU_LIST_TAG,
  ORGANIZATION_API_RSU_TAG,
  ORGANIZATION_API_USER_LIST_TAG,
  ORGANIZATION_API_USER_TAG,
  organizationApiSlice,
  useDeleteOrganizationMutation,
  useGetOrganizationsQuery,
} from '../api/organizationApiSlice'
import { RSU_API_RSU_LIST_ID, RSU_API_RSU_TAG, rsuApiSlice } from '../api/rsuApiSlice'
import { USER_API_USER_LIST_ID, USER_API_USER_TAG, userApiSlice } from '../api/userApiSlice'

const AdminOrganizationTab = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const navigate = useNavigate()
  const theme = useTheme()
  const location = useLocation()

  const activeTab = location.pathname.split('/')[4]

  const { data: orgList = [] } = useGetOrganizationsQuery()
  const [deleteOrganization] = useDeleteOrganizationMutation()
  const globalSelectedOrgId = useSelector(selectOrganizationId)

  const [selectedOrg, setSelectedOrg] = useState(orgList.find((o) => o.id === globalSelectedOrgId) ?? orgList[0])

  const updateSelectedOrgById = (orgId: number) => {
    const org = orgList.find((o) => o.id === orgId)
    if (org) {
      setSelectedOrg(org)
    }
  }

  useEffect(() => {
    if (orgList.length > 0 && !orgList.find((o) => o.id === selectedOrg?.id)) {
      // If selected org no longer exists, select the first org in the list
      setSelectedOrg(orgList.find((o) => o.id === globalSelectedOrgId) ?? orgList[0])
    } else {
      // Else, refresh org reference
      setSelectedOrg(orgList.find((o) => o.id === selectedOrg?.id) ?? orgList[0])
    }
    // Ignore dependency warning for selectedOrg
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orgList])

  const refresh = () => {
    // Invalidate tags to automatically refresh all organization page data
    dispatch(
      organizationApiSlice.util.invalidateTags([
        ORGANIZATION_API_ORG_TAG,
        ORGANIZATION_API_RSU_LIST_TAG,
        ORGANIZATION_API_AVAILABLE_RSU_LIST_TAG,
        ORGANIZATION_API_RSU_TAG,
        ORGANIZATION_API_USER_LIST_TAG,
        ORGANIZATION_API_AVAILABLE_USER_LIST_TAG,
        ORGANIZATION_API_USER_TAG,
      ])
    )
    dispatch(
      adminIntersectionApiSlice.util.invalidateTags([
        { type: ADMIN_INTERSECTION_TAG, id: ADMIN_INTERSECTION_LIST_ID },
        { type: ADMIN_INTERSECTION_TAG, id: ADMIN_INTERSECTION_AVAILABLE_LIST_ID },
      ])
    )
    dispatch(userApiSlice.util.invalidateTags([{ type: USER_API_USER_TAG, id: USER_API_USER_LIST_ID }]))
    dispatch(rsuApiSlice.util.invalidateTags([{ type: RSU_API_RSU_TAG, id: RSU_API_RSU_LIST_ID }]))
  }

  const handleOrgDelete = async (orgId: number, orgName: string) => {
    const loadingToast = toast.loading(`Deleting organization ${orgName}...`)
    try {
      await deleteOrganization(orgId).unwrap()
      dispatch(deleteOrgAssociationByOrgId(orgId))
      const next = orgList.find((o) => o.id !== orgId) ?? orgList[0]
      if (next) {
        updateSelectedOrgById(next.id)
      }
      toast.success(`Successfully deleted organization ${orgName}`, { id: loadingToast })
    } catch (error) {
      toast.error('Failed to delete organization due to error: ' + error, { id: loadingToast })
    }
  }

  return (
    <Routes>
      <Route
        path="/"
        element={
          <div style={{ backgroundColor: theme.palette.background.paper, height: 'fit-content', padding: '10px 0px' }}>
            <div style={{ display: 'flex', flexDirection: 'row', margin: '10px' }}>
              <Grid2
                sx={{
                  display: 'flex',
                  flexDirection: 'row',
                  width: '70%',
                  justifyContent: 'flex-start',
                  alignItems: 'center',
                }}
              >
                <Grid2 size={{ xs: 0 }} sx={{ marginLeft: '10px' }}>
                  <DropdownList
                    style={{ width: '250px' }}
                    dataKey="name"
                    textField="name"
                    data={orgList}
                    value={selectedOrg?.name}
                    onChange={(value) => setSelectedOrg(value)}
                  />
                </Grid2>
                {selectedOrg === undefined ? (
                  <></>
                ) : (
                  <>
                    <Grid2 size={{ xs: 0 }} sx={{ marginLeft: '10px' }}>
                      <ContainedIconButton
                        key="delete_button"
                        title="Edit Organization"
                        onClick={() => navigate('editOrganization/' + selectedOrg?.name)}
                        sx={{
                          backgroundColor: 'transparent',
                          borderRadius: '2px',
                          '&:hover': {
                            backgroundColor: alpha(theme.palette.text.primary, 0.1),
                          },
                        }}
                      >
                        <EditOutlined sx={{ color: theme.palette.custom.rowActionIcon }} />
                      </ContainedIconButton>
                    </Grid2>
                    <Grid2 size={{ xs: 0 }} sx={{ marginLeft: '10px' }}>
                      <AdminOrganizationDeleteMenu
                        deleteOrganization={() => handleOrgDelete(selectedOrg.id, selectedOrg.name)}
                        selectedOrganization={selectedOrg?.name ?? ''}
                      />
                    </Grid2>
                  </>
                )}
              </Grid2>
              <Grid2
                sx={{
                  display: 'flex',
                  flexDirection: 'row',
                  width: '30%',
                  justifyContent: 'flex-end',
                  alignItems: 'center',
                }}
              >
                {activeTab === undefined && [
                  <Grid2 size={{ xs: 0 }} sx={{ marginRight: '10px' }}>
                    <Button
                      key="refresh_button"
                      title="Refresh Organizations"
                      onClick={() => refresh()}
                      variant="outlined"
                      color="info"
                      size="small"
                      className="museo-slab capital-case"
                      startIcon={<Refresh />}
                    >
                      Refresh
                    </Button>
                  </Grid2>,
                  <Grid2 size={{ xs: 0 }} sx={{ marginRight: '10px' }}>
                    <Button
                      key="plus_button"
                      title="Add Organization"
                      onClick={() => navigate('addOrganization')}
                      startIcon={<AddCircleOutline />}
                      variant="contained"
                      size="small"
                      className="museo-slab capital-case"
                    >
                      New
                    </Button>
                  </Grid2>,
                ]}
              </Grid2>
            </div>

            <div className="scroll-div-org-tab">
              {selectedOrg === undefined ? (
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    height: '100%',
                  }}
                >
                  <h2 style={{ color: theme.palette.text.primary }}>No organization selected</h2>
                  <p style={{ color: theme.palette.text.secondary }}>Please select an organization to view details.</p>
                </div>
              ) : (
                <>
                  <ConditionalRenderRsu>
                    <AdminOrganizationTabRsu
                      selectedOrgId={selectedOrg.id}
                      selectedOrgName={selectedOrg.name}
                      key="rsu"
                    />
                  </ConditionalRenderRsu>
                  <ConditionalRenderIntersection>
                    <AdminOrganizationTabIntersection
                      selectedOrgId={selectedOrg.id}
                      selectedOrgName={selectedOrg.name}
                      key="intersection"
                    />
                  </ConditionalRenderIntersection>
                  <AdminOrganizationTabUser
                    selectedOrgId={selectedOrg.id}
                    selectedOrgName={selectedOrg.name}
                    key="user"
                  />
                </>
              )}
            </div>
          </div>
        }
      />
      <Route path="addOrganization" element={<AdminAddOrganization />} />
      <Route path="editOrganization/:orgName" element={<AdminEditOrganization />} />
      <Route
        path="*"
        element={
          <NotFound
            redirectRoute="/dashboard/admin/organization"
            redirectRouteName="Admin Organization Page"
            offsetHeight={319}
            description="This page does not exist. Please return to the admin organization page."
          />
        }
      />
    </Routes>
  )
}

export default AdminOrganizationTab

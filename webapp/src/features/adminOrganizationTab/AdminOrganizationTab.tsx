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
import toast from 'react-hot-toast'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { ConditionalRenderIntersection, ConditionalRenderRsu } from '../../feature-flags'
import {
  changeOrganizationName,
  deleteOrgAssociationByOrgId,
  selectOrganizationId,
  selectOrganizationName,
} from '../../generalSlices/userSlice'
import { NotFound } from '../../pages/404'
import { RootState } from '../../store'
import { ContainedIconButton } from '../../styles/components/ContainedIconButton'
import '../adminRsuTab/Admin.css'
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

const AdminOrganizationTab = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
  const navigate = useNavigate()
  const theme = useTheme()
  const location = useLocation()

  const activeTab = location.pathname.split('/')[4]

  const { data: orgList = [] } = useGetOrganizationsQuery()
  const [deleteOrganization] = useDeleteOrganizationMutation()

  const selectedOrgId = useSelector(selectOrganizationId)
  const selectedOrgName = useSelector(selectOrganizationName)

  const refresh = () => {
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
  }

  const handleOrgDelete = async (orgId: number, orgName: string) => {
    const loadingToast = toast.loading(`Deleting organization ${orgName}...`)
    try {
      await deleteOrganization(orgId).unwrap()
      dispatch(deleteOrgAssociationByOrgId(orgId))
      const next = orgList.find((o) => o.id !== orgId) ?? orgList[0]
      if (next) {
        dispatch(changeOrganizationName(next.name))
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
                    value={selectedOrgName}
                    onChange={(value) => dispatch(changeOrganizationName(value.name))}
                  />
                </Grid2>
                {selectedOrgName === undefined || selectedOrgId === undefined ? (
                  <></>
                ) : (
                  <>
                    <Grid2 size={{ xs: 0 }} sx={{ marginLeft: '10px' }}>
                      <ContainedIconButton
                        key="delete_button"
                        title="Edit Organization"
                        onClick={() => navigate('editOrganization/' + selectedOrgName)}
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
                        deleteOrganization={() => handleOrgDelete(selectedOrgId, selectedOrgName)}
                        selectedOrganization={selectedOrgName}
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
              {selectedOrgName === undefined || selectedOrgId === undefined ? (
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
                      selectedOrgId={selectedOrgId}
                      selectedOrgName={selectedOrgName}
                      key="rsu"
                    />
                  </ConditionalRenderRsu>
                  <ConditionalRenderIntersection>
                    <AdminOrganizationTabIntersection
                      selectedOrgId={selectedOrgId}
                      selectedOrgName={selectedOrgName}
                      key="intersection"
                    />
                  </ConditionalRenderIntersection>
                  <AdminOrganizationTabUser
                    selectedOrgId={selectedOrgId}
                    selectedOrgName={selectedOrgName}
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

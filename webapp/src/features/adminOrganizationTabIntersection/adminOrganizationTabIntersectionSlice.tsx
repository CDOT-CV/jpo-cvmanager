import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { selectToken } from '../../generalSlices/userSlice'
import { RootState } from '../../store'
import {
  AdminOrgIntersectionDeleteMultiple,
  AdminOrgIntersectionDeleteSingle,
} from './AdminOrganizationTabIntersectionTypes'
import { adminOrgPatch, editOrg } from '../adminOrganizationTab/adminOrganizationTabSlice'
import { adminIntersectionApiSlice } from '../api/adminIntersectionApiSlice'

export const intersectionDeleteSingle = createAsyncThunk(
  'adminOrganizationTabIntersection/intersectionDeleteSingle',
  async (payload: AdminOrgIntersectionDeleteSingle, { getState, dispatch }) => {
    const { intersection, selectedOrg, selectedOrgEmail, updateTableData } = payload

    const result = await dispatch(
      adminIntersectionApiSlice.endpoints.getIntersection.initiate(intersection.intersection_id)
    ).unwrap()

    const promises = []
    if (result?.intersection_data?.organizations?.length > 1) {
      const patchJson: adminOrgPatch = {
        name: selectedOrg,
        email: selectedOrgEmail,
        intersections_to_remove: [intersection.intersection_id],
      }
      promises.push(dispatch(editOrg(patchJson)))
    } else {
      alert(
        'Cannot remove Intersection ' +
          intersection.intersection_id +
          ' from ' +
          selectedOrg +
          ' because it must belong to at least one organization.'
      )
    }
    const res = await Promise.all(promises)
    dispatch(refresh({ selectedOrg, updateTableData }))

    if ((res[0].payload as any).success) {
      return { success: true, message: 'Intersection deleted successfully' }
    } else {
      return { success: false, message: 'Failed to delete Intersection' }
    }
  },
  { condition: (_, { getState }) => selectToken(getState() as RootState) != undefined }
)

export const intersectionDeleteMultiple = createAsyncThunk(
  'adminOrganizationTabIntersection/intersectionDeleteMultiple',
  async (payload: AdminOrgIntersectionDeleteMultiple, { getState, dispatch }) => {
    const { rows, selectedOrg, selectedOrgEmail, updateTableData } = payload

    const invalidIntersections = []
    const patchJson: adminOrgPatch = {
      name: selectedOrg,
      email: selectedOrgEmail,
      intersections_to_remove: [],
    }
    for (const row of rows) {
      const result = await dispatch(
        adminIntersectionApiSlice.endpoints.getIntersection.initiate(row.intersection_id)
      ).unwrap()
      if (result?.intersection_data?.organizations?.length > 1) {
        patchJson.intersections_to_remove.push(row.intersection_id)
      } else {
        invalidIntersections.push(row.intersection_id)
      }
    }
    if (invalidIntersections.length === 0) {
      const res = await dispatch(editOrg(patchJson))
      dispatch(refresh({ selectedOrg, updateTableData }))
      if ((res.payload as any).success) {
        return { success: true, message: 'Intersection(s) deleted successfully' }
      } else {
        return { success: false, message: 'Failed to delete Intersection(s)' }
      }
    } else {
      alert(
        'Cannot remove Intersection(s) ' +
          invalidIntersections.map((ip) => ip.toString()).join(', ') +
          ' from ' +
          selectedOrg +
          ' because they must belong to at least one organization.'
      )
    }
  },
  { condition: (_, { getState }) => selectToken(getState() as RootState) != undefined }
)

export const refresh = createAsyncThunk(
  'adminOrganizationTabIntersection/refresh',
  async (
    payload: {
      selectedOrg: string
      updateTableData: (selectedOrg: string) => void
    },
    { dispatch }
  ) => {
    const { selectedOrg, updateTableData } = payload
    updateTableData(selectedOrg)
    dispatch(adminIntersectionApiSlice.util.invalidateTags([{ type: 'AdminIntersection', id: 'LIST' }]))
  },
  { condition: (_, { getState }) => selectToken(getState() as RootState) != undefined }
)

export const adminOrganizationTabIntersectionSlice = createSlice({
  name: 'adminOrganizationTabIntersection',
  initialState: {
    loading: false,
    value: {},
  },
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(refresh.fulfilled, () => {
      // no-op, kept for potential future use
    })
  },
})

export const selectLoading = (state: RootState) => state.adminOrganizationTabIntersection.loading

export default adminOrganizationTabIntersectionSlice.reducer

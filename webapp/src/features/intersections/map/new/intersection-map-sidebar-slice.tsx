import { createSlice } from '@reduxjs/toolkit'
import { RootState } from '../../../../store'

const initialState = {}

export const intersectionMapSidebarSlice = createSlice({
  name: 'intersectionMapSidebar',
  initialState: {
    loading: false,
    value: initialState,
  },
  reducers: {},
  extraReducers: (builder) => {
    builder
  },
})

export const selectLoading = (state: RootState) => state.intersectionMapSidebar.loading

export const {} = intersectionMapSidebarSlice.actions

export default intersectionMapSidebarSlice.reducer
